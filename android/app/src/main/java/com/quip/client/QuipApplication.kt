package com.quip.client

import android.app.Application

/** Ensures AppState's SharedPreferences-backed state is loaded before any Activity runs. */
class QuipApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppState.init(this)
    }
}
