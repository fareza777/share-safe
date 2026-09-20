package com.sharesafe.app.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coroutines that finish on a background dispatcher must hop back to the main looper before
 * touching the UI: `Toast` throws if it is constructed on a thread without a prepared Looper.
 * These helpers keep every UI side effect on the main thread.
 */
suspend fun Context.toast(message: String) {
    withContext(Dispatchers.Main) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }
}

/** Toast for callers already on the main dispatcher (for example an intent-failure callback). */
fun Context.toastOnMain(message: String) {
    Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
}

/** Starts an intent on the main thread, reporting failure instead of crashing. */
suspend fun Context.startActivitySafely(intent: Intent, onError: (String) -> Unit = {}) {
    withContext(Dispatchers.Main) {
        runCatching { startActivity(intent) }
            .onFailure { error -> onError(error.message.orEmpty()) }
    }
}
