package com.serendeep.marginalia.study

import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.data.SessionKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

const val MIN_SESSION_MS = 30_000L
const val IDLE_MS = 2 * 60_000L

/**
 * Turns notebook foreground time into READING sessions. [activity] only stores
 * a timestamp; the database is touched when a session closes.
 */
@Singleton
class StudyTracker @Inject constructor(
    private val repository: MarginaliaRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var lectureId: String? = null
    private var open = false
    private var startedAt = 0L

    @Volatile
    private var lastActivity = 0L

    fun start(lectureId: String) {
        synchronized(lock) {
            if (open) closeLocked(System.currentTimeMillis())
            this.lectureId = lectureId
            open = true
            val now = System.currentTimeMillis()
            startedAt = now
            lastActivity = now
        }
    }

    fun activity() {
        val now = System.currentTimeMillis()
        // Fast path: still inside the idle window, a single volatile write.
        if (now - lastActivity < IDLE_MS) {
            lastActivity = now
            return
        }
        synchronized(lock) {
            if (!open) return
            closeLocked(now)
            startedAt = now
            lastActivity = now
            open = true
        }
    }

    fun stop() {
        synchronized(lock) { closeLocked(System.currentTimeMillis()) }
    }

    private fun closeLocked(now: Long) {
        if (!open) return
        open = false
        val end = if (now - lastActivity > IDLE_MS) lastActivity else now
        val id = lectureId
        val start = startedAt
        if (end - start >= MIN_SESSION_MS) {
            scope.launch { repository.saveSession(id, SessionKind.READING, start, end) }
        }
    }
}
