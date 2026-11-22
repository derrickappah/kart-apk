package com.example

import android.app.Application
import com.example.data.remote.KartAppContainer

class KartApplication : Application() {
    lateinit var container: KartAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = KartAppContainer(this)
    }
}
