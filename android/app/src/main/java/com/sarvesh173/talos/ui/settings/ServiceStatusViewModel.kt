package com.sarvesh173.talos.ui.settings

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Polls [ServiceStatusProbe] every [POLL_INTERVAL_MS] but only while the
 * screen is resumed. Polling stops on pause so nothing runs in the
 * background (battery-sensitive laptop; no background cost).
 *
 * Drive it from Compose with [startPolling], or call [onResumed]/[onPaused]
 * from a lifecycle observer. [retry] probes once immediately.
 */
class ServiceStatusViewModel(
    private val baseUrl: String = "http://100.75.107.79:9119",
    private val token: String? = null,
    private val probe: ServiceStatusProbe = ServiceStatusProbe()
) : ViewModel() {

    companion object {
        const val POLL_INTERVAL_MS: Long = 10_000L
    }

    private val _result = MutableStateFlow(
        ServiceProbeResult(
            state = ServiceState.Unknown,
            title = "Checking",
            body = "Checking the dashboard",
            detail = "Not checked yet"
        )
    )
    val result: StateFlow<ServiceProbeResult> = _result.asStateFlow()

    private var pollingJob: Job? = null

    /** Probe once now. Safe to call any time, resumed or not. */
    fun retry() {
        viewModelScope.launch { refresh() }
    }

    suspend fun refresh() {
        _result.value = probe.probe(baseUrl, token)
    }

    /** Start the 10s poll loop. No-op if already running. */
    fun onResumed() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (true) {
                refresh()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** Stop polling. Called when the screen is paused. */
    fun onPaused() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * Bind polling to RESUMED with [repeatOnLifecycle]: probes while resumed,
     * cancels the moment the lifecycle drops below RESUMED.
     */
    fun startPolling(owner: LifecycleOwner) {
        viewModelScope.launch {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                onResumed()
                try {
                    kotlinx.coroutines.awaitCancellation()
                } finally {
                    onPaused()
                }
            }
        }
    }

    override fun onCleared() {
        onPaused()
        super.onCleared()
    }
}
