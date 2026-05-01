package com.omnio.phone

import android.app.Application
import com.omnio.phone.diag.CrashReporter
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OmnioPhoneApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
