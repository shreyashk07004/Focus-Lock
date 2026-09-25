package com.focuslock.app.data.repository

/** Bounds of a daily limit, shared by the repository (which enforces them) and the limit editor UI. */
object DailyLimit {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 240
    const val STEP_MINUTES = 5
    const val DEFAULT_MINUTES = 30

    fun coerce(minutes: Int): Int = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
}
