package com.panda.touch

import android.app.Application
import com.topjohnwu.superuser.Shell

class PandaTouchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Shell.enableVerboseLogging = true
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
    }
}
