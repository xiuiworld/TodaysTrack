package dev.todaystrack.vehicle.generation.v2

/** Shared with the actual Room migration, independently exercised against a temporary SQLite DB. */
object MusicReceiptSchema {
    val statements=listOf("CREATE TABLE IF NOT EXISTS music_v2_receipts (intentId TEXT NOT NULL PRIMARY KEY, inputId TEXT, inputHash TEXT, acceptanceUncertain INTEGER NOT NULL, plan TEXT, returnedLyrics TEXT, returnedStructure TEXT, integrity TEXT, serverRevision INTEGER NOT NULL)")
}
