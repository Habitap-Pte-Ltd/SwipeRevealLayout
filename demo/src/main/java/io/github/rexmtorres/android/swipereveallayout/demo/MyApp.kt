package io.github.rexmtorres.android.swipereveallayout.demo

import android.app.Application
import io.github.rexmtorres.android.swipereveallayout.demo.log.LogcatAppender

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LogcatAppender.register(this, BuildConfig.DEBUG)
    }
}
