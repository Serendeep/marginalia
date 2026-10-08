package com.serendeep.marginalia.study

import com.serendeep.marginalia.data.CardEntity
import com.serendeep.marginalia.data.MarginaliaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What is waiting to be reviewed, plus how many cards exist at all. */
data class DueState(val queue: List<CardEntity>, val totalCards: Int)

/** Review activity: per-day counts for the streak, and the share of mature reviews graded Good or Easy. */
data class ReviewActivity(val byDay: Map<LocalDate, Int>, val retentionPct: Int?)

private const val RETENTION_DAYS = 30L

// Cards come due while nothing in the database changes, so the clock drives a refresh too.
private fun minuteTicker(): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(60_000)
    }
}

fun startOfDay(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

@OptIn(ExperimentalCoroutinesApi::class)
fun MarginaliaRepository.observeDue(): Flow<DueState> {
    val introduced = minuteTicker().map { startOfDay(it) }.distinctUntilChanged()
        .flatMapLatest { observeNewIntroducedSince(it) }
    return combine(observeCards(), introduced, minuteTicker()) { cards, newToday, now ->
        DueState(dueQueue(cards, now, newToday), cards.size)
    }
}

fun MarginaliaRepository.observeReviewActivity(): Flow<ReviewActivity> {
    val since = System.currentTimeMillis() - RETENTION_DAYS * 24 * 60 * 60_000L
    return combine(observeReviewTimes(), observeRetention(since)) { times, retention ->
        val zone = ZoneId.systemDefault()
        ReviewActivity(
            byDay = times.groupingBy { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.eachCount(),
            retentionPct = retention.total.takeIf { it > 0 }?.let { retention.good * 100 / it },
        )
    }
}
