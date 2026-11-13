package com.skd.notebook

import android.app.Application
import com.skd.notebook.data.sync.CloudSync
import com.skd.notebook.util.Str
import com.skd.notebook.util.ThemePrefs

class NoteStackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Str.init(this)
        ThemePrefs.applySaved(this)
        CloudSync.init(this) // sends notes saved offline as soon as the connection is back
    }
}
