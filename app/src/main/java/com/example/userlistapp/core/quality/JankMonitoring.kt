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

    /** Frames are reported on the JankStats aggregator thread, sessions start and stop on the main thread. */
    private val framesLock = Any()
    private val stateCounts = mutableMapOf<String, FrameCounts>()
    private var sessionTrace: QualityTrace? = null
    private var totalFrames = 0L
    private var jankyFrames = 0L

    fun resume() {
        if (jankStats.isTrackingEnabled) return
        synchronized(framesLock) {
            totalFrames = 0
            jankyFrames = 0
            stateCounts.clear()
        }
        sessionTrace = qualityMonitor.startTrace(JANK_SESSION_TRACE).also {
            it.putAttribute(ACTIVITY_ATTRIBUTE, MAIN_ACTIVITY_VALUE)
        }
        jankStats.isTrackingEnabled = true
    }

    fun pause() {
        if (!jankStats.isTrackingEnabled) return
        jankStats.isTrackingEnabled = false
        val session = synchronized(framesLock) {
            FrameSummary(
                totalFrames = totalFrames,
                jankyFrames = jankyFrames,
                stateCounts = stateCounts.mapValues { (_, counts) -> counts.copy() },
            )
        }
        val jankyStates = session.stateCounts
            .filterValues { counts -> counts.janky > 0 }
            .toSortedMap()
        val stateSummary = jankyStates.keys
            .joinToString(separator = JANKY_STATE_SEPARATOR)
            .ifEmpty { NO_JANKY_STATES }
        val summary = FRAMES_PREFIX + session.totalFrames +
                JANKY_INFIX + session.jankyFrames +
                JANKY_STATES_INFIX + stateSummary
        Log.i(LOG_TAG, JANK_SESSION_LOG_PREFIX + summary)
        jankyStates.forEach { (state, counts) ->
            Log.i(
                LOG_TAG,
                JANK_STATE_LOG_PREFIX + state +
                        FRAMES_INFIX + counts.total +
                        JANKY_INFIX + counts.janky,
            )
        }
        qualityMonitor.log(JANK_SESSION_LOG_PREFIX + summary)
        qualityMonitor.setCustomKey(LAST_JANK_TOTAL_FRAMES_KEY, session.totalFrames.toString())
        qualityMonitor.setCustomKey(LAST_JANK_FRAMES_KEY, session.jankyFrames.toString())
        sessionTrace?.apply {
            putMetric(TOTAL_FRAMES_METRIC, session.totalFrames)
            putMetric(JANKY_FRAMES_METRIC, session.jankyFrames)
            putAttribute(JANK_SEEN_ATTRIBUTE, (session.jankyFrames > 0).toString())
            stop()
        }
        sessionTrace = null
    }

    private fun onFrame(frame: FrameData) {
        val state = frame.states
            .filter { it.key in MONITORED_STATE_KEYS }
            .sortedBy { it.key }
            .joinToString(separator = STATE_SEPARATOR) {
                it.key + STATE_VALUE_SEPARATOR + it.value
            }
            .ifEmpty { UNKNOWN_SCREEN_STATE }
        synchronized(framesLock) {
            totalFrames++
            val counts = stateCounts.getOrPut(state) { FrameCounts() }
            counts.total++
            if (frame.isJank) {
                jankyFrames++
                counts.janky++
            }
        }
        if (frame.isJank) {
            val durationMs = frame.frameDurationUiNanos / NANOS_PER_MILLISECOND
            Log.w(
                LOG_TAG,
                JANK_FRAME_LOG_PREFIX + durationMs + STATE_LOG_INFIX + state,
            )
        }
    }

    private data class FrameCounts(var total: Long = 0, var janky: Long = 0)

    private data class FrameSummary(
        val totalFrames: Long,
        val jankyFrames: Long,
        val stateCounts: Map<String, FrameCounts>,
    )

    private companion object {
        const val LOG_TAG = "UserListJank"
        const val JANK_SESSION_TRACE = "ui_jank_session"
        const val NANOS_PER_MILLISECOND = 1_000_000L
        private const val ACTIVITY_ATTRIBUTE = "activity"
        private const val MAIN_ACTIVITY_VALUE = "main"
        private const val LAST_JANK_TOTAL_FRAMES_KEY = "last_jank_total_frames"
        private const val LAST_JANK_FRAMES_KEY = "last_jank_frames"
        private const val TOTAL_FRAMES_METRIC = "total_frames"
        private const val JANKY_FRAMES_METRIC = "janky_frames"
        private const val JANK_SEEN_ATTRIBUTE = "jank_seen"
        private const val SCREEN_STATE_KEY = "screen"
        private const val PHASE_STATE_KEY = "phase"
        private const val INTERACTION_STATE_KEY = "interaction"
        private const val VISIBLE_USERS_STATE_KEY = "visible_users"
        private const val STATE_SEPARATOR = ","
        private const val STATE_VALUE_SEPARATOR = "="
        private const val JANKY_STATE_SEPARATOR = "|"
        private const val UNKNOWN_SCREEN_STATE = "screen=unknown"
        private const val NO_JANKY_STATES = "none"
        private const val FRAMES_PREFIX = "frames="
        private const val FRAMES_INFIX = " frames="
        private const val JANKY_INFIX = " janky="
        private const val JANKY_STATES_INFIX = " janky_states="
        private const val JANK_SESSION_LOG_PREFIX = "Jank session "
        private const val JANK_STATE_LOG_PREFIX = "Jank state="
        private const val JANK_FRAME_LOG_PREFIX = "Jank frame duration_ms="
        private const val STATE_LOG_INFIX = " state="
        val MONITORED_STATE_KEYS = setOf(
            SCREEN_STATE_KEY,
            PHASE_STATE_KEY,
            INTERACTION_STATE_KEY,
            VISIBLE_USERS_STATE_KEY,
        )
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
