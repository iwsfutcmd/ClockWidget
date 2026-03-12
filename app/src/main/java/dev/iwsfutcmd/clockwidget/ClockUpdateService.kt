package dev.iwsfutcmd.clockwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import java.util.Calendar
import java.util.Date

class ClockUpdateService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            ClockWidget.updateAll(this@ClockUpdateService)
            handler.postDelayed(this, nextDelay())
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> handler.removeCallbacks(tick)
                Intent.ACTION_SCREEN_ON  -> handler.post(tick)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Clock Widget", NotificationManager.IMPORTANCE_MIN)
                .apply { setShowBadge(false) }
        )
        startForeground(
            NOTIF_ID,
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("Clock Widget")
                .setOngoing(true)
                .build()
        )

        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(tick)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isInteractive) handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private enum class Granularity { SECOND, MINUTE, HOUR, DAY }

    private fun skeletonGranularity(skeleton: String): Granularity {
        val chars = skeleton.toSet()
        return when {
            chars.any { it in "sSA" }          -> Granularity.SECOND
            chars.any { it in "m" }            -> Granularity.MINUTE
            chars.any { it in "hHkKjJCabB" }   -> Granularity.HOUR
            else                               -> Granularity.DAY
        }
    }

    private fun nextDelay(): Long {
        val ids = AppWidgetManager.getInstance(this)
            .getAppWidgetIds(ComponentName(this, ClockWidget::class.java))
        val finest = ids
            .map { skeletonGranularity(ClockPrefs(this, it).skeleton) }
            .minOrNull() ?: Granularity.SECOND
        val now = System.currentTimeMillis()
        return when (finest) {
            Granularity.SECOND -> 1_000L - (now % 1_000L)
            Granularity.MINUTE -> 60_000L - (now % 60_000L)
            Granularity.HOUR   -> {
                val cal = Calendar.getInstance()
                val msIntoHour = (cal.get(Calendar.MINUTE) * 60L + cal.get(Calendar.SECOND)) * 1_000L +
                    cal.get(Calendar.MILLISECOND)
                3_600_000L - msIntoHour
            }
            Granularity.DAY    -> msUntilNextDayChange(ids, now)
        }
    }

    // Probe forward to find when any widget's output actually changes — works for any
    // calendar system regardless of where the day boundary falls (midnight, sunset, dawn, etc.)
    private fun msUntilNextDayChange(ids: IntArray, now: Long): Long {
        var earliest = Long.MAX_VALUE
        for (id in ids) {
            val prefs = ClockPrefs(this, id)
            val sdf = SimpleDateFormat(prefs.pattern, ULocale.forLanguageTag(prefs.localeTag))
            val baseline = sdf.format(Date(now))
            for (h in 1..48) {
                val probe = now + h * 3_600_000L
                if (sdf.format(Date(probe)) != baseline) {
                    // Binary-search within this hour down to minute precision
                    var lo = probe - 3_600_000L
                    var hi = probe
                    while (hi - lo > 60_000L) {
                        val mid = (lo + hi) / 2
                        if (sdf.format(Date(mid)) == baseline) lo = mid else hi = mid
                    }
                    earliest = minOf(earliest, hi)
                    break
                }
            }
        }
        return if (earliest == Long.MAX_VALUE) 86_400_000L
               else (earliest - now).coerceAtLeast(60_000L)
    }

    companion object {
        private const val NOTIF_ID = 1
        private const val CHANNEL_ID = "clock_widget_updates"
    }
}
