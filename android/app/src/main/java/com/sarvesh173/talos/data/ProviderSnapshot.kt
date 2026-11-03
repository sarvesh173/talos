package com.sarvesh173.talos.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Provider health snapshot as returned by `GET /api/providers`.
 *
 * Field names mirror the core's pydantic models exactly; the client keeps only
 * what the UI renders so a schema addition on the server never breaks parsing.
 */
@Serializable
data class ProviderModelDto(
    val id: String,
    @SerialName("context_length") val contextLength: Long? = null,
    @SerialName("context_source") val contextSource: String? = null
)

@Serializable
data class ProviderSnapshotDto(
    val id: String,
    val name: String = "",
    val vendor: String = "",
    @SerialName("base_url") val baseUrl: String = "",
    @SerialName("default_model") val defaultModel: String = "",
    val status: String = "unknown",
    @SerialName("status_detail") val statusDetail: String = "",
    @SerialName("latency_ms") val latencyMs: Double? = null,
    @SerialName("context_length") val contextLength: Long? = null,
    @SerialName("max_context_length") val maxContextLength: Long? = null,
    @SerialName("credential_present") val credentialPresent: Boolean = false,
    @SerialName("models_count") val modelsCount: Int = 0,
    val models: List<ProviderModelDto> = emptyList()
)
