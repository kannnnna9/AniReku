package com.anireku.app

import android.app.Application

class AniRekuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
