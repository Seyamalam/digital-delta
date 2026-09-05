package com.example.digitaldelta

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DeltaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.example.digitaldelta.service.MeshMaintenance.schedulePeriodic(this)
        com.example.digitaldelta.service.MeshMaintenance.scheduleNow(this)
        com.example.digitaldelta.service.ObserverPublication.periodic(this)
        com.example.digitaldelta.service.ObserverPublication.schedule(this)
    }
}
