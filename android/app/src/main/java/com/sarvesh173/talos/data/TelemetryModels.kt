package com.sarvesh173.talos.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContextTelemetry(
    @SerialName("agent_id") val agentId: String = "",
    val model: String = "",
    @SerialName("prompt_tokens") val promptTokens: Long = 0,
    @SerialName("completion_tokens") val completionTokens: Long = 0,
    @SerialName("total_tokens") val totalTokens: Long = 0,
    @SerialName("context_window_limit") val contextWindowLimit: Long = 1_000_000,
    @SerialName("usage_pct") val usagePct: Float = 0f,
    @SerialName("warning_threshold") val warningThreshold: String = "normal",
    @SerialName("generation_rate_per_sec") val generationRatePerSec: Double = 0.0
)

@Serializable
data class RoomContextBreakdown(
    @SerialName("system_tokens") val systemTokens: Long = 0,
    @SerialName("history_tokens") val historyTokens: Long = 0,
    @SerialName("draft_tokens") val draftTokens: Long = 0,
    @SerialName("completion_tokens") val completionTokens: Long = 0,
    @SerialName("total_tokens") val totalTokens: Long = 0,
    @SerialName("context_window_limit") val contextWindowLimit: Long = 1_000_000,
    @SerialName("usage_pct") val usagePct: Float = 0f,
    @SerialName("warning_threshold") val warningThreshold: String = "normal"
)

@Serializable
data class TelemetryConfig(
    @SerialName("sync_delay_secs") val syncDelaySecs: Long = 5
)
