package com.sarvesh173.talos.ui.logs

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.sarvesh173.talos.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash reports the user can actually send.
 *
 * Without this the only record of a crash is whatever the phone's own log buffer
 * still holds, which is gone by the time anyone goes looking. So every uncaught
 * exception becomes a file in the app's private storage, and the Logs screen
 * lists them — open one, copy it, or hand it to another app.
 *
 * Files are app-private, so no storage permission is needed.
 */
object CrashLogger {
    private const val DIR = "crash-logs"
    private const val LOGCAT_LINES = 400

    @Volatile
    private var installed = false

    /** Install the handler. Safe to call more than once; only the first wins. */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Write first: once the default handler runs, the process is on its
            // way down and nothing else here is guaranteed to complete.
            runCatching { write(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun logDir(context: Context): File =
        File(context.filesDir, DIR).apply { mkdirs() }

    /** Newest first. */
    fun list(context: Context): List<File> =
        logDir(context)
            .listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun read(context: Context, file: File): String =
        runCatching { file.readText() }.getOrDefault("Could not read ${file.name}")

    fun clear(context: Context): Int {
        val files = list(context)
        files.forEach { it.delete() }
        return files.size
    }

    /** Share sheet. Needs the FileProvider declared in the manifest. */
    fun share(context: Context, file: File) {
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.crashlog", file)
        }.getOrNull() ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "TALOS crash — ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Send crash log"))
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(logDir(context), "crash-$stamp.txt")

        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()

        // The logcat tail is what makes a Compose stack trace diagnosable: it
        // shows the frames the exception itself already unwound past.
        val text = buildString {
            appendLine("TALOS crash report")
            appendLine("time:    ${Date()}")
            appendLine("app:     ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("device:  ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("thread:  ${thread.name}")
            appendLine()
            appendLine("exception: ${throwable.javaClass.name}: ${throwable.message}")
            appendLine()
            appendLine(stack)
            appendLine()
            appendLine("---- logcat (last $LOGCAT_LINES lines) ----")
            appendLine(logcat())
        }
        file.writeText(text)
    }

    /**
     * Recent logcat for this app.
     *
     * An app may read its own logs without READ_LOGS; `-d` dumps and exits so a
     * dying process cannot hang on it.
     */
    private fun logcat(): String = runCatching {
        val process = ProcessBuilder("logcat", "-d", "-t", "$LOGCAT_LINES", "-v", "threadtime")
            .redirectErrorStream(true)
            .start()
        val out = process.inputStream.bufferedReader().readText()
        process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
        out.ifBlank { "(no logcat output)" }
    }.getOrElse { "(logcat unavailable: ${it.message})" }
}