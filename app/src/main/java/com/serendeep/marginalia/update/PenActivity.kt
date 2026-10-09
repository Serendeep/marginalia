package com.serendeep.marginalia.update

import android.os.SystemClock

/** When the pen last touched the screen, so an update never restarts the app under a hand that is writing. */
object PenActivity {
    const val IDLE_MS = 60_000L

    @Volatile private var last = Long.MIN_VALUE / 2

    fun mark() {
        last = SystemClock.elapsedRealtime()
    }

    /** How much longer to wait before the pen counts as idle; 0 when it already does. */
    fun msUntilIdle(): Long = msUntilIdle(last, SystemClock.elapsedRealtime())

    internal fun msUntilIdle(lastAt: Long, now: Long): Long = (lastAt + IDLE_MS - now).coerceAtLeast(0)
}
