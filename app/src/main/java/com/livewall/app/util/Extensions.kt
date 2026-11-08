package com.livewall.app.util

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity

/**
 * Traverses ContextWrapper hierarchy to find the host [ComponentActivity].
 */
fun Context.findActivity(): ComponentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
