package com.example.userlistapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.userlistapp.core.quality.AppQualityMonitor
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class QualityTestReceiver : BroadcastReceiver() {
    @Inject
    lateinit var qualityMonitor: AppQualityMonitor

    override fun onReceive(context: Context, intent: Intent) {
        if (!qualityMonitor.isEnabled) {
            Log.w(LOG_TAG, "Firebase is unavailable; check app/google-services.json")
            return
        }
        when (intent.action) {
            ACTION_NON_FATAL -> {
                qualityMonitor.setCustomKey("quality_test_source", "adb")
                qualityMonitor.log("Deliberate debug non-fatal requested")
                qualityMonitor.recordNonFatal(
                    IllegalStateException("Deliberate debug non-fatal"),
                )
                Log.i(LOG_TAG, "Crashlytics non-fatal recorded")
            }

            ACTION_CRASH -> throw IllegalStateException("Deliberate debug Crashlytics crash")
        }
    }

    private companion object {
        const val LOG_TAG = "QualityTest"
        const val ACTION_NON_FATAL = "com.example.userlistapp.QUALITY_NON_FATAL"
        const val ACTION_CRASH = "com.example.userlistapp.QUALITY_CRASH"
    }
}
