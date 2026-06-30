package com.adskipper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.adskipper.service.AdSkipAccessibilityService
import com.adskipper.util.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, checking auto-start setting")

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val settingsManager = SettingsManager(context)

            scope.launch {
                try {
                    val settings = settingsManager.settings.first()
                    if (settings.autoStart) {
                        Log.d(TAG, "Auto-start enabled, prompting user to enable accessibility service")
                        // We cannot directly enable accessibility service, but we can notify the user
                        // The service will be started when the user opens the app
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking auto-start setting", e)
                }
            }
        }
    }
}