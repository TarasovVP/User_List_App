package com.example.userlistapp.core.quality

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

interface QualityTrace {
    fun putAttribute(name: String, value: String)
    fun putMetric(name: String, value: Long)
    fun stop()
}

interface AppQualityMonitor {
    val isEnabled: Boolean
    fun startTrace(name: String): QualityTrace
    fun log(message: String)
    fun setCustomKey(name: String, value: String)
    fun recordNonFatal(error: Throwable)
}

object NoOpAppQualityMonitor : AppQualityMonitor {
    override val isEnabled = false
    override fun startTrace(name: String): QualityTrace = NoOpQualityTrace
    override fun log(message: String) = Unit
    override fun setCustomKey(name: String, value: String) = Unit
    override fun recordNonFatal(error: Throwable) = Unit
}

private object NoOpQualityTrace : QualityTrace {
    override fun putAttribute(name: String, value: String) = Unit
    override fun putMetric(name: String, value: Long) = Unit
    override fun stop() = Unit
}

@Singleton
class FirebaseAppQualityMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppQualityMonitor {
    override val isEnabled: Boolean
        get() = FirebaseApp.getApps(context).isNotEmpty()

    override fun startTrace(name: String): QualityTrace {
        if (!isEnabled) return NoOpQualityTrace
        val trace = FirebasePerformance.getInstance().newTrace(name)
        trace.start()
        return FirebaseQualityTrace(trace)
    }

    override fun log(message: String) {
        if (isEnabled) FirebaseCrashlytics.getInstance().log(message)
    }

    override fun setCustomKey(name: String, value: String) {
        if (isEnabled) FirebaseCrashlytics.getInstance().setCustomKey(name, value)
    }

    override fun recordNonFatal(error: Throwable) {
        if (isEnabled) FirebaseCrashlytics.getInstance().recordException(error)
    }
}

private class FirebaseQualityTrace(private val trace: Trace) : QualityTrace {
    private val stopped = AtomicBoolean(false)

    override fun putAttribute(name: String, value: String) {
        if (!stopped.get()) trace.putAttribute(name, value)
    }

    override fun putMetric(name: String, value: Long) {
        if (!stopped.get()) trace.putMetric(name, value)
    }

    override fun stop() {
        if (stopped.compareAndSet(false, true)) trace.stop()
    }
}
