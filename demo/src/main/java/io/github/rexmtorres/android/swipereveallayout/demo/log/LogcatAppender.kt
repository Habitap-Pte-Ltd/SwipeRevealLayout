/**
The MIT License (MIT)

Copyright (c) 2022 Rex Mag-uyon Torres

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

package io.github.rexmtorres.android.swipereveallayout.demo.log

import android.content.Context
import android.util.Log
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.core.util.StatusPrinter
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

/**
 * Custom implementation of an [Appender][ch.qos.logback.core.Appender] based on
 * [ch.qos.logback.classic.android.LogcatAppender](https://github.com/tony19/logback-android/blob/main/logback-android/src/main/java/ch/qos/logback/classic/android/LogcatAppender.java)
 * from [tony19/logback-android](https://github.com/tony19/logback-android).
 *
 * This is meant to replace
 * [ch.qos.logback.classic.android.LogcatAppender](https://github.com/tony19/logback-android/blob/main/logback-android/src/main/java/ch/qos/logback/classic/android/LogcatAppender.java)
 * since the original implementation is quite old and does not work with newer versions of SLF4J.
 *
 * Note that, unlike [tony19/logback-android](https://github.com/tony19/logback-android), this
 * appender needs to be explicitly registered, preferably in your
 * [Application's][android.app.Application] `onCreate()` method, like so:
 * ```kotlin
 * import android.app.Application
 * import io.github.rexmtorres.android.swipereveallayout.demo.log.LogcatAppender
 *
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         LogcatAppender.registerAppender(this, BuildConfig.DEBUG)
 *     }
 * }
 * ```
 *
 * Also note that the appender must be named "LOGCAT" in the `assets/logback.xml` configuration:
 * ```
 * <configuration>
 *     <appender name="LOGCAT" class="io.github.rexmtorres.android.swipereveallayout.demo.log.LogcatAppender">
 *         ...
 *     </appender>
 *
 *     ...
 * </configuration>
 * ```
 */
class LogcatAppender : AppenderBase<ILoggingEvent>() {
    companion object {
        private const val MAX_TAG_LENGTH = 23

        private var _loggableLevel: Int = Level.ERROR.levelInt
        private val loggableLevel: Int
            get() = _loggableLevel

        private var _enabled: Boolean = false
        private val enabled: Boolean
            get() = _enabled

        //region Adapted from: https://tomzurkan.medium.com/using-logback-with-android-to-extend-or-enhance-your-logging-6217bfd486dc
        fun register(context: Context, enabled: Boolean) {
            println("LogcatAppender.register: context = $context")
            println("LogcatAppender.register: enabled = $enabled")

            _enabled = enabled

            // assume SLF4J is bound to logback in the current environment
            val logContext = LoggerFactory.getILoggerFactory() as LoggerContext
            println("LogcatAppender.register: logContext = $logContext")

            try {
                val configurator = JoranConfigurator()
                configurator.context = logContext
                // Call context.reset() to clear any previous configuration, e.g. default
                // configuration. For multi-step configuration, omit calling context.reset().
                logContext.reset()

                val file = getFileFromAssets(context, "logback.xml")
                println("LogcatAppender.register: file = $file")
                configurator.doConfigure(file)

                val log = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
                _loggableLevel = log.level.levelInt
                println("LogcatAppender.register: level = ${log.level}")

                val app = log.getAppender("LOGCAT") as LogcatAppender
                println("LogcatAppender.register: app = $app")
            } catch (e: Exception) {
                // StatusPrinter will handle this
                e.printStackTrace()
                StatusPrinter.printInCaseOfErrorsOrWarnings(logContext)
            }
        }

        @Throws(IOException::class)
        fun getFileFromAssets(context: Context, fileName: String): File =
            File(context.cacheDir, fileName)
                .also {
                    if (it.exists()) {
                        System.err.println("LogcatAppender.getFileFromAssets: Deleting $it")
                        it.delete()
                    }

                    it.outputStream().use { cache ->
                        context.assets.open(fileName).use { inputStream ->
                            inputStream.copyTo(cache)
                        }
                    }
                }
        //endregion Adapted from: https://tomzurkan.medium.com/using-logback-with-android-to-extend-or-enhance-your-logging-6217bfd486dc
    }

    var encoder: PatternLayoutEncoder? = null
    var tagEncoder: PatternLayoutEncoder? = null

    override fun append(event: ILoggingEvent?) {
        if (!enabled) {
            return
        }

        val layout = encoder?.layout

        if (!isStarted || (event == null) || (layout == null)) {
            return
        }

        val tag = getTag(event)
        val levelInt = event.level.levelInt

        if (levelInt < loggableLevel) {
            return
        }

        when (levelInt) {
            Level.ALL_INT, Level.TRACE_INT -> Log.v(tag, layout.doLayout(event))
            Level.DEBUG_INT -> Log.d(tag, layout.doLayout(event))
            Level.INFO_INT -> Log.i(tag, layout.doLayout(event))
            Level.WARN_INT -> Log.w(tag, layout.doLayout(event))
            Level.ERROR_INT -> Log.e(tag, layout.doLayout(event))
        }
    }

    override fun start() {
        if (!enabled) {
            System.err.println("Logging is not enabled for the appender named [$name].")
            return
        }

        if (encoder?.layout == null) {
            addError("No layout set for the appender named [$name].")
            return
        }

        val tagEncoder = this.tagEncoder

        if (tagEncoder != null) {
            val layout = tagEncoder.layout

            if (layout == null) {
                addError("No layout set for the appender named [$name].")
                return
            }

            if (layout is PatternLayout) {
                val pattern = tagEncoder.pattern

                if (!pattern.contains("%nopex")) {
                    tagEncoder.stop()
                    tagEncoder.pattern = "$pattern%nopex"
                    tagEncoder.start()
                }

                layout.setPostCompileProcessor(null)
            }
        }

        super.start()
    }

    private fun getTag(event: ILoggingEvent): String =
        (tagEncoder?.layout?.doLayout(event) ?: event.loggerName).let { tag ->
            if (tag.length > MAX_TAG_LENGTH) {
                "${tag.substring(0, MAX_TAG_LENGTH - 1)}*"
            } else {
                tag
            }
        }
}
