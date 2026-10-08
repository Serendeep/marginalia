package com.serendeep.marginalia.study

import android.os.SystemClock
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.data.SessionKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

const val WORK_SECONDS = 25 * 60
const val BREAK_SECONDS = 5 * 60
const val POMODORO_ROUNDS = 4

data class FocusState(
    val remainingSec: Int = WORK_SECONDS,
    val running: Boolean = false,
    val round: Int = 1,
    val onBreak: Boolean = false,
)

/** Pomodoro: 25 min work, 5 min break, rounds 1 to 4. Each stretch of work is logged as a FOCUS session. */
@Singleton
class FocusTimer @Inject constructor(
    private val repository: MarginaliaRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(FocusState())
    val state: StateFlow<FocusState> = _state.asStateFlow()

    private var job: Job? = null
    private var workStartedAt = 0L

    fun toggle() {
        if (_state.value.running) pause() else start()
    }

    private fun start() {
        val s = _state.value
        if (s.running) return
        _state.value = s.copy(running = true)
        if (!s.onBreak) workStartedAt = System.currentTimeMillis()
        val endsAt = SystemClock.elapsedRealtime() + s.remainingSec * 1000L
        job = scope.launch {
            while (true) {
                val left = endsAt - SystemClock.elapsedRealtime()
                if (left <= 0) {
                    complete()
                    return@launch
                }
                val sec = ((left + 999) / 1000).toInt()
                if (sec != _state.value.remainingSec) _state.value = _state.value.copy(remainingSec = sec)
                // Wake just after the next whole-second boundary: one emission per second.
                delay(left % 1000 + 5)
            }
        }
    }

    private fun pause() {
        job?.cancel()
        logWork()
        _state.value = _state.value.copy(running = false)
    }

    private fun complete() {
        val s = _state.value
        if (s.onBreak) {
            _state.value = FocusState(round = s.round % POMODORO_ROUNDS + 1)
        } else {
            logWork()
            _state.value = s.copy(remainingSec = BREAK_SECONDS, onBreak = true, running = false)
            start()
        }
    }

    private fun logWork() {
        val s = _state.value
        if (s.onBreak || workStartedAt == 0L) return
        val from = workStartedAt
        workStartedAt = 0L
        val to = System.currentTimeMillis()
        if (to - from >= MIN_SESSION_MS) {
            scope.launch { repository.saveSession(null, SessionKind.FOCUS, from, to) }
        }
    }
}
