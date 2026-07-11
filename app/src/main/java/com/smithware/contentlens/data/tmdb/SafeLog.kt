package com.smithware.contentlens.data.tmdb

import android.util.Log

internal object SafeLog {
    fun debug(tag: String, message: String) {
        runCatching { Log.d(tag, message) }
    }

    fun warn(tag: String, message: String) {
        runCatching { Log.w(tag, message) }
    }
}
