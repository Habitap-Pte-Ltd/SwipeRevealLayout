package app.habitap.swipereveallayout.util

import mu.KLogger

private const val SHOW_LOGS = false

internal fun KLogger.debugX(msg: () -> Any?) {
    if (SHOW_LOGS) {
        debug { msg() }
    }
}
