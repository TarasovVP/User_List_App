package com.example.userlistapp.core.quality

import android.util.Log
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState

class JankMonitor(
    window: Window,
    private val qualityMonitor: AppQualityMonitor,
) {
    private val jankStats = JankStats.createAndTrack(window, ::onFrame).apply {
        isTrackingEnabled = false
    }
    private val stateCounts = mutableMapOf<String, FrameCounts>()
    private var sessionTrace: QualityTrace? = null
    private var totalFrames = 0L
    private var jankyFrames = 0L

    fun resume() {
        if (jankStats.isTrackingEnabled) return
        totalFrames = 0
        jankyFrames = 0
        stateCounts.clear()
        sessionTrace = qualityMonitor.startTrace(JANK_SESSION_TRACE).also {
            it.putAttribute("activity", "main")
        }
        jankStats.isTrackingEnabled = true
    }

    fun pause() {
        if (!jankStats.isTrackingEnabled) return
        jankStats.isTrackingEnabled = false
        val summary = "frames=$totalFrames janky=$jankyFrames"
        Log.i(LOG_TAG, "Jank session $summary")
        stateCounts.forEach { (state, counts) ->
            Log.i(LOG_TAG, "Jank state=$state frames=${counts.total} janky=${counts.janky}")
        }
        qualityMonitor.log("Jank session $summary")
        qualityMonitor.setCustomKey("last_jank_total_frames", totalFrames.toString())
        qualityMonitor.setCustomKey("last_jank_frames", jankyFrames.toString())
        sessionTrace?.apply {
            putMetric("total_frames", totalFrames)
            putMetric("janky_frames", jankyFrames)
            putAttribute("jank_seen", (jankyFrames > 0).toString())
            stop()
        }
        sessionTrace = null
    }

    private fun onFrame(frame: FrameData) {
        totalFrames++
        if (frame.isJank) jankyFrames++
        val state = frame.states
            .filter { it.key in MONITORED_STATE_KEYS }
            .sortedBy { it.key }
            .joinToString(separator = ",") { "${it.key}=${it.value}" }
            .ifEmpty { "screen=unknown" }
        val counts = stateCounts.getOrPut(state) { FrameCounts() }
        counts.total++
        if (frame.isJank) {
            counts.janky++
            val durationMs = frame.frameDurationUiNanos / NANOS_PER_MILLISECOND
            Log.w(LOG_TAG, "Jank frame duration_ms=$durationMs state=$state")
        }
    }

    private data class FrameCounts(var total: Long = 0, var janky: Long = 0)

    private companion object {
        const val LOG_TAG = "UserListJank"
        const val JANK_SESSION_TRACE = "ui_jank_session"
        const val NANOS_PER_MILLISECOND = 1_000_000L
        val MONITORED_STATE_KEYS = setOf("screen", "phase", "interaction", "visible_users")
    }
}

@Composable
fun TrackJankStates(states: Map<String, String>) {
    val view = LocalView.current
    DisposableEffect(view, states) {
        val holder = PerformanceMetricsState.getHolderForHierarchy(view)
        val publish = Runnable { holder.state?.let { state -> states.forEach(state::putState) } }
        publish.run()
        view.post(publish)
        onDispose {
            view.removeCallbacks(publish)
            holder.state?.let { state -> states.keys.forEach(state::removeState) }
        }
    }
}
