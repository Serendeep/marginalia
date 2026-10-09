package com.serendeep.marginalia.update

/** Saves the user's library before a risky change, labelled for the recent-backups list. */
fun interface SafetySnapshot {
    suspend fun take(label: String)
}
