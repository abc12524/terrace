package com.terrace

import android.app.Application
import com.terrace.data.AppPreferences
import com.terrace.data.db.AppDatabase

class ShellToolApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppPreferences.init(this)
        AppDatabase.getInstance(this)
    }
}
