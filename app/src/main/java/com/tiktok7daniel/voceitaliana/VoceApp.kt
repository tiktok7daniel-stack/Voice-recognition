package com.tiktok7daniel.voceitaliana

import android.app.Application
import android.os.Build
import java.io.File

class VoceApp : Application() {
    lateinit var engine: Engine
        private set

    val crashFile get() = File(filesDir, "crash.txt")

    override fun onCreate() {
        super.onCreate()
        // Save any crash so the next launch can show what went wrong.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                crashFile.writeText(
                    "Voce Italiana ${packageManager.getPackageInfo(packageName, 0).versionName} · " +
                        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        error.stackTraceToString(),
                )
            }
            previous?.uncaughtException(thread, error)
        }
        engine = Engine(this)
    }
}
