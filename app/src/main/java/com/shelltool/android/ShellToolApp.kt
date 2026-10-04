package com.shelltool.android

import android.app.Application
import com.shelltool.android.data.AppPreferences
import com.shelltool.android.data.db.AppDatabase

class ShellToolApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppPreferences.init(this)
        AppDatabase.getInstance(this)
    }
}
