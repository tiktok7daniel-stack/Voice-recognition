package com.tiktok7daniel.voceitaliana

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
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process alive (and the CPU awake) while [Engine] downloads a
 * model or transcribes a file, so the work finishes even if the app is closed or the screen is off.
 * It mirrors the engine's progress in a notification and stops itself once the engine is idle.
 */
class WorkService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var watching = false

    private val engine get() = (application as VoceApp).engine

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            engine.cancel()
            return START_NOT_STICKY
        }
        createChannels()
        if (!goForeground()) {
            // The system refused a foreground service: the work still runs in the app process,
            // it just may stop if Android kills the app in the background. Never crash for it.
            stopSelf()
            return START_NOT_STICKY
        }

        if (wakeLock == null) {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoceItaliana:work")
                .apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }
        }
        if (!watching) {
            watching = true
            scope.launch {
                snapshotFlow { engine.phase }.conflate().collect { phase ->
                    val nm = getSystemService(NotificationManager::class.java)
                    if (phase is Phase.Importing) return@collect
                    if (engine.busy) {
                        nm.notify(ONGOING_ID, progressNotification(phase))
                        delay(1000) // notifications are rate-limited by the system
                    } else {
                        finish(phase)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Downloads are data sync, transcription is media processing (Android 15+ only). Some phones
     * and Android versions reject one type or the other, so try each before giving up.
     */
    private fun goForeground(): Boolean {
        val types = buildList {
            if (Build.VERSION.SDK_INT >= 35 && engine.phase !is Phase.Downloading) add(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            if (Build.VERSION.SDK_INT >= 29) add(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            if (Build.VERSION.SDK_INT >= 35) add(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            if (Build.VERSION.SDK_INT < 34) add(0)
        }.distinct()
        for (type in types) {
            try {
                ServiceCompat.startForeground(this, ONGOING_ID, progressNotification(engine.phase), type)
                return true
            } catch (e: Exception) {
                Log.w("VoceItaliana", "startForeground(type=$type) refused", e)
            }
        }
        return false
    }

    /** Android 15+: the daily time budget for this service type ran out. Stop cleanly instead of crashing. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun finish(phase: Phase) {
        val nm = getSystemService(NotificationManager::class.java)
        val (title, text) = when (phase) {
            is Phase.Done -> "خلصنا — النص جاهز" to "${engine.segments.size} جملة من ${engine.file?.name ?: "الملف"}"
            is Phase.Failed -> phase.title to phase.detail
            is Phase.BatchDone -> "خلصنا ${phase.saved} ملف" to
                (if (phase.failed > 0) "${phase.failed} ماخلصوش — " else "") + "اتحفظوا في المجموعة"
            else -> null to null
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (title != null) {
            nm.notify(
                RESULT_ID,
                NotificationCompat.Builder(this, CHANNEL_DONE)
                    .setSmallIcon(R.drawable.ic_stat_voice)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(if (phase is Phase.Done) engine.text.take(400).ifBlank { text } else text))
                    .setContentIntent(openApp())
                    .setAutoCancel(true)
                    .build(),
            )
        }
        stopSelf()
    }

    private fun progressNotification(phase: Phase): android.app.Notification {
        val b = NotificationCompat.Builder(this, CHANNEL_WORK)
            .setSmallIcon(R.drawable.ic_stat_voice)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp())
            .addAction(0, "إيقاف", PendingIntent.getService(
                this, 1, Intent(this, WorkService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ))
        when (phase) {
            is Phase.Downloading -> {
                val mb = { v: Long -> v / 1_048_576 }
                b.setContentTitle("بحمّل ${phase.model.title}")
                if (phase.total > 0) {
                    b.setContentText("${mb(phase.done)} / ${mb(phase.total)} MB")
                    b.setProgress(1000, (phase.done * 1000 / phase.total).toInt(), false)
                } else {
                    b.setContentText("${mb(phase.done)} MB").setProgress(0, 0, true)
                }
            }
            is Phase.Working -> {
                b.setContentTitle("بستخرج الكلام الإيطالي — ${(phase.progress * 100).toInt()}%")
                    .setContentText(engine.segments.lastOrNull()?.text ?: engine.file?.name)
                    .setProgress(1000, (phase.progress * 1000).toInt(), false)
            }
            is Phase.Batch -> {
                b.setContentTitle("${phase.index} من ${phase.total} خلصوا — ${(phase.progress * 100).toInt()}%")
                    .setContentText(phase.name)
                    .setProgress(1000, (phase.progress * 1000).toInt(), false)
            }
            else -> b.setContentTitle("بجهّز الموديل").setProgress(0, 0, true)
        }
        return b.build()
    }

    private fun openApp() = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
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
        private const val RESULT_ID = 2
        private const val ACTION_STOP = "stop"

        fun start(context: Context) {
            // Starting can be refused (e.g. ForegroundServiceStartNotAllowedException); the work
            // itself runs in Engine either way, so losing the service must not close the app.
            try {
                ContextCompat.startForegroundService(context, Intent(context, WorkService::class.java))
            } catch (e: Exception) {
                Log.w("VoceItaliana", "WorkService not started", e)
            }
        }
    }
}
