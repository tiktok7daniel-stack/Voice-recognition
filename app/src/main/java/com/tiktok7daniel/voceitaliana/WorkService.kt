package com.tiktok7daniel.voceitaliana

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process alive (and the CPU awake) while [Engine] downloads a
 * model or transcribes files, so the work finishes even if the app is closed or the screen is off.
 * It mirrors the engine's progress in a notification and stops itself once the engine is idle.
 */
class WorkService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var watching = false
    private val reported = HashSet<Long>()

    private val engine get() = (application as VoceApp).engine
    private val nm get() = getSystemService(NotificationManager::class.java)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            engine.cancelAll()
            return START_NOT_STICKY
        }
        createChannels()
        goForeground()

        if (wakeLock == null) {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoceItaliana:work")
                .apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }
        }
        // Jobs finished before this start were already seen by the user.
        if (!watching) engine.jobs.filter { !it.busy }.forEach { reported += it.id }
        if (!watching) {
            watching = true
            scope.launch {
                snapshotFlow { summary() }.conflate().collect { s ->
                    reportFinishedJobs()
                    if (engine.busy) {
                        nm.notify(ONGOING_ID, progressNotification(s))
                        delay(1000) // notifications are rate-limited by the system
                    } else {
                        ServiceCompat.stopForeground(this@WorkService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Some phones refuse one foreground type or another; fall back instead of crashing. */
    private fun goForeground() {
        val notification = progressNotification(summary())
        val types = buildList {
            if (Build.VERSION.SDK_INT >= 35) add(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            if (Build.VERSION.SDK_INT >= 29) add(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            add(0)
        }
        for (type in types) {
            try {
                ServiceCompat.startForeground(this, ONGOING_ID, notification, type)
                return
            } catch (e: Exception) {
                Log.w("VoceItaliana", "startForeground(type=$type) failed", e)
            }
        }
    }

    private data class Summary(val title: String, val text: String?, val progress: Float?)

    private fun summary(): Summary {
        val d = engine.download
        val running = engine.jobs.filter { it.busy }
        val mb = { v: Long -> v / 1_048_576 }
        return when {
            running.isNotEmpty() -> {
                val parts = running.map { (it.state as? JobState.Working)?.progress ?: 0f }
                val avg = parts.sum() / parts.size
                val title = if (running.size == 1) "بستخرج الكلام — ${(avg * 100).toInt()}%"
                else "بستخرج ${running.size} ملفات — ${(avg * 100).toInt()}%"
                Summary(title, running.first().segments.lastOrNull()?.text ?: running.first().name, avg)
            }
            d is Download.Running -> Summary(
                "بحمّل ${d.model.title}",
                if (d.total > 0) "${mb(d.done)} / ${mb(d.total)} MB" else "${mb(d.done)} MB",
                if (d.total > 0) d.done.toFloat() / d.total else null,
            )
            else -> Summary("بجهّز", null, null)
        }
    }

    private fun reportFinishedJobs() {
        engine.jobs.filter { !it.busy && it.id !in reported }.forEach { job ->
            reported += job.id
            val (title, text) = when (val s = job.state) {
                is JobState.Done -> "خلصنا — ${job.name}" to job.text.take(400)
                is JobState.Failed -> "${s.title} — ${job.name}" to s.detail
                else -> return@forEach
            }
            nm.notify(
                job.id.hashCode(),
                NotificationCompat.Builder(this, CHANNEL_DONE)
                    .setSmallIcon(R.drawable.ic_stat_voice)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(openApp())
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    private fun progressNotification(s: Summary): Notification =
        NotificationCompat.Builder(this, CHANNEL_WORK)
            .setSmallIcon(R.drawable.ic_stat_voice)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp())
            .setContentTitle(s.title)
            .setContentText(s.text)
            .setProgress(1000, ((s.progress ?: 0f) * 1000).toInt(), s.progress == null)
            .addAction(
                0, "إيقاف",
                PendingIntent.getService(
                    this, 1, Intent(this, WorkService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()

    private fun openApp() = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannels() {
        nm.createNotificationChannel(NotificationChannel(CHANNEL_WORK, "شغل في الخلفية", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "النص خلص", NotificationManager.IMPORTANCE_DEFAULT))
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_WORK = "work"
        private const val CHANNEL_DONE = "done"
        private const val ONGOING_ID = 1
        private const val ACTION_STOP = "stop"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, WorkService::class.java))
            } catch (e: Exception) {
                // Work still runs while the app is open; only background survival is lost.
                Log.w("VoceItaliana", "Could not start WorkService", e)
            }
        }
    }
}
