package dev.iwsfutcmd.clockwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Reschedules the per-minute [AlarmManager] tick after a device reboot.
 * Without this the widget would freeze until the next `onUpdate` cycle.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ClockWidget.startService(context)
        }
    }
}
