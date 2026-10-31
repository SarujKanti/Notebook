package com.skd.notebook

import android.app.Application
import com.skd.notebook.util.ThemePrefs

class NoteStackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemePrefs.applySaved(this)
    }
}
