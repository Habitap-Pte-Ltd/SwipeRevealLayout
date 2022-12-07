package app.habitap.swipereveallayout.demo

import android.app.Application
import app.habitap.swipereveallayout.demo.log.LogcatAppender

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LogcatAppender.register(this, BuildConfig.DEBUG)
    }
}
