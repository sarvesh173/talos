package com.sarvesh173.talos.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CustomProvider(
    val id: String = "",
    val name: String = "",
    @SerialName("base_url") val baseUrl: String = "",
    @SerialName("api_key") val apiKey: String = "",
    val enabled: Boolean = true,
    @SerialName("target_agents") val targetAgents: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class ModelCatalogEntry(
    val id: String = "",
    val name: String = "",
    @SerialName("context_length") val contextLength: Long? = null,
    @SerialName("is_free") val isFree: Boolean? = null
)

@Serializable
data class CreateProviderRequest(
    val name: String,
    @SerialName("base_url") val baseUrl: String,
    @SerialName("api_key") val apiKey: String,
    @SerialName("target_agents") val targetAgents: List<String>? = null
)
