package com.sarvesh173.talos.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The phone, driven from the host.
 *
 * An agent that can reach the host can tap, type, go back, scroll and launch an
 * app. No root, no adb, no second machine: the accessibility framework is the
 * only door that lets one app act for the user, and the user opens it once.
 *
 * The service owns the poll loop so control survives the app being backgrounded
 * or closed - which is the point of driving a phone nobody is holding.
 *
 * Deliberately no screenshots: an agent debugging over text does not need eyes,
 * and the screenshot API needs a result callback that would double this file.
 */
class TalosAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        startPolling()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        loop?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startPolling() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            val http = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
            val json = "application/json; charset=utf-8".toMediaType()
            val prefs = getSharedPreferences("talos_prefs", Context.MODE_PRIVATE)
            val base = (prefs.getString("base_url", "") ?: "").trimEnd('/')
            val token = prefs.getString("api_token", "") ?: ""

            if (base.isBlank()) {
                Log.w(TAG, "remote control idle: no base_url")
                return@launch
            }
            Log.i(TAG, "remote control live against $base")

            while (isActive) {
                val cmd = runCatching {
                    val req = Request.Builder()
                        .url("$base/api/remote/next?device=phone")
                        .header("Authorization", "Bearer $token")
                        .get()
                        .build()
                    http.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) return@use null
                        r.body?.string()?.let { JSONObject(it).optJSONObject("command") }
                    }
                }.getOrNull()

                if (cmd == null) {
                    delay(1_200)
                    continue
                }

                val id = cmd.optString("id")
                val action = cmd.optString("action")
                val payload = cmd.optJSONObject("payload") ?: JSONObject()

                val result = runCatching { execute(action, payload) }
                    .fold(
                        onSuccess = { it },
                        onFailure = { Result(false, "error: " + (it.message ?: it.toString())) },
                    )

                runCatching {
                    val body = JSONObject()
                        .put("ok", result.ok)
                        .put("output", result.output)
                        .toString()
                        .toRequestBody(json)
                    val req = Request.Builder()
                        .url("$base/api/remote/result/$id")
                        .header("Authorization", "Bearer $token")
                        .post(body)
                        .build()
                    http.newCall(req).execute().use { }
                }.onFailure { Log.e(TAG, "result post failed", it) }
            }
        }
    }

    private data class Result(val ok: Boolean, val output: String)

    private suspend fun execute(action: String, payload: JSONObject): Result = withContext(Dispatchers.Main) {
        when (action) {
            "tap" -> {
                val x = payload.optDouble("x", -1.0).toFloat()
                val y = payload.optDouble("y", -1.0).toFloat()
                if (x < 0 || y < 0) return@withContext Result(false, "tap needs x and y")
                gesture(x, y, x, y, 60L)
                Result(true, "tapped $x,$y")
            }

            "type" -> {
                val text = payload.optString("text")
                val node = focusedNode()
                if (node == null) return@withContext Result(false, "no focused field")
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                Result(ok, if (ok) "typed ${text.length} chars" else "setText refused")
            }

            "key" -> {
                val which = payload.optString("key", "back").lowercase()
                val code = when (which) {
                    "back" -> GLOBAL_ACTION_BACK
                    "home" -> GLOBAL_ACTION_HOME
                    "recents" -> GLOBAL_ACTION_RECENTS
                    "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
                    else -> return@withContext Result(false, "unknown key $which")
                }
                Result(performGlobalAction(code), "key $which")
            }

            "swipe" -> {
                val x1 = payload.optDouble("x1", 0.0).toFloat()
                val y1 = payload.optDouble("y1", 0.0).toFloat()
                val x2 = payload.optDouble("x2", 0.0).toFloat()
                val y2 = payload.optDouble("y2", 0.0).toFloat()
                gesture(x1, y1, x2, y2, payload.optLong("ms", 300L))
                Result(true, "swiped")
            }

            "scroll" -> {
                val node = focusedNode() ?: rootInActiveWindow
                if (node == null) return@withContext Result(false, "nothing to scroll")
                val up = payload.optBoolean("up", false)
                val ok = node.performAction(
                    if (up) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
                    null,
                )
                Result(ok, "scrolled")
            }

            "open_app" -> {
                val pkg = payload.optString("package")
                if (pkg.isBlank()) return@withContext Result(false, "open_app needs package")
                val intent = packageManager.getLaunchIntentForPackage(pkg)
                    ?: return@withContext Result(false, "no launch intent for $pkg")
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                Result(true, "launched $pkg")
            }

            "wait" -> {
                delay(payload.optLong("ms", 1_000L).coerceAtMost(10_000L))
                Result(true, "waited")
            }

            else -> Result(false, "unknown action $action")
        }
    }

    private fun focusedNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?: root
    }

    private fun gesture(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long) {
        val path = Path().apply {
            moveTo(x1, y1)
            if (x1 != x2 || y1 != y2) lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, ms.coerceAtLeast(1L))
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    companion object {
        private const val TAG = "TalosRemote"
    }