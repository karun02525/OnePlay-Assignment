package com.oneplay.events

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RecordingEventReceiver : BroadcastReceiver() {

    companion object {
        const val EVENT_STOP_RECORDING = "Event:Stop_Recording"
    }
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            EVENT_STOP_RECORDING -> {
                context.sendBroadcast(Intent("${context.packageName}.RECORDING_EVENT").also {
                    it.putExtra("action", "STOP")
                })
            }
        }
    }
}
