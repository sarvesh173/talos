package com.sarvesh173.talos.network

import com.sarvesh173.talos.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val domainJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

private val apiHttpClient = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .build()

/**
 * Extension APIs for Agent Fleet, Telemetry, and Custom Provider Management.
 */

suspend fun TalosClient.fetchAgents(): List<AgentInfo> = withContext(Dispatchers.IO) {
    try {
        val req = Request.Builder()
            .url("$baseUrl/api/agents")
            .header("Authorization", "Bearer $apiToken")
            .build()
        apiHttpClient.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: "[]"
                domainJson.decodeFromString<List<AgentInfo>>(body)
            } else emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }
}

suspend fun TalosClient.fetchAgentContext(agentId: String): ContextTelemetry? = withContext(Dispatchers.IO) {
    try {
        val req = Request.Builder()
            .url("$baseUrl/api/agents/$agentId/context")
            .header("Authorization", "Bearer $apiToken")
            .build()
        apiHttpClient.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: return@withContext null
                domainJson.decodeFromString<ContextTelemetry>(body)
            } else null
        }
    } catch (_: Exception) {
        null
    }
}

suspend fun TalosClient.fetchRoomBreakdown(roomId: String): RoomContextBreakdown? = withContext(Dispatchers.IO) {
    try {
        val req = Request.Builder()
            .url("$baseUrl/api/rooms/$roomId/context-breakdown")
            .header("Authorization", "Bearer $apiToken")
            .build()
        apiHttpClient.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: return@withContext null
                domainJson.decodeFromString<RoomContextBreakdown>(body)
            } else null
        }
    } catch (_: Exception) {
        null
    }
}

suspend fun TalosClient.fetchCustomProviders(): List<CustomProvider> = withContext(Dispatchers.IO) {
    try {
        val req = Request.Builder()
            .url("$baseUrl/api/providers/custom")
            .header("Authorization", "Bearer $apiToken")
            .build()
        apiHttpClient.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: "[]"
                domainJson.decodeFromString<List<CustomProvider>>(body)
            } else emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }
}

suspend fun TalosClient.addCustomProvider(
    name: String,
    url: String,
    apiKey: String,
    targetAgents: List<String> = emptyList()
): Boolean = withContext(Dispatchers.IO) {
    try {
        val payload = domainJson.encodeToString(
            CreateProviderRequest(name = name, baseUrl = url, apiKey = apiKey, targetAgents = targetAgents)
        )
        val req = Request.Builder()
            .url("$baseUrl/api/providers/custom")
            .header("Authorization", "Bearer $apiToken")
            .post(payload.toRequestBody("application/json".toMediaTypeOrNull()))
            .build()
        apiHttpClient.newCall(req).execute().use { it.isSuccessful }
    } catch (_: Exception) {
        false
    }
}

suspend fun TalosClient.fetchProviderModels(providerId: String): List<ModelCatalogEntry> = withContext(Dispatchers.IO) {
    try {
        val req = Request.Builder()
            .url("$baseUrl/api/providers/custom/$providerId/fetch-models")
            .header("Authorization", "Bearer $apiToken")
            .post("{}".toRequestBody("application/json".toMediaTypeOrNull()))
            .build()
        apiHttpClient.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: "[]"
                domainJson.decodeFromString<List<ModelCatalogEntry>>(body)
            } else emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }
}

suspend fun TalosClient.deleteCustomProvider(providerId: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val req = Request.Builder()
            .url("$baseUrl/api/providers/custom/$providerId")
            .header("Authorization", "Bearer $apiToken")
            .delete()
            .build()
        apiHttpClient.newCall(req).execute().use { it.isSuccessful }
    } catch (_: Exception) {
        false
    }
}

suspend fun TalosClient.switchAgentModel(agentId: String, model: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val payload = domainJson.encodeToString(AgentModelSwitchRequest(model = model))
        val req = Request.Builder()
            .url("$baseUrl/api/agents/$agentId/model")
            .header("Authorization", "Bearer $apiToken")
            .post(payload.toRequestBody("application/json".toMediaTypeOrNull()))
            .build()
        apiHttpClient.newCall(req).execute().use { it.isSuccessful }
    } catch (_: Exception) {
        false
    }
}
