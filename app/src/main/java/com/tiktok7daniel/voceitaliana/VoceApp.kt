package com.tiktok7daniel.voceitaliana

import android.app.Application

class VoceApp : Application() {
    lateinit var engine: Engine
        private set

    override fun onCreate() {
        super.onCreate()
        engine = Engine(this)
    }
}
