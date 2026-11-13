package dev.todaystrack.server

import dev.todaystrack.vehicle.generation.v2.MusicReceiptSchema
import java.sql.DriverManager
import org.junit.Test
import kotlin.test.*

class VehicleMusicMigrationTest {
    @Test fun additiveMigrationKeepsExistingFrozenIntentAndSongAndAddsSeparateRemoteReceipts() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            db.createStatement().use { s ->
                s.execute("CREATE TABLE generation_intents(id TEXT PRIMARY KEY,payload TEXT,inputHash TEXT)")
                s.execute("INSERT INTO generation_intents VALUES('legacy','frozen-v1','original-hash')")
                s.execute("CREATE TABLE tracks(id TEXT PRIMARY KEY,sha256 TEXT)");s.execute("INSERT INTO tracks VALUES('song','original-audio-hash')")
                MusicReceiptSchema.statements.forEach(s::execute)
                s.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='music_v2_receipts'").use { assertTrue(it.next(),"v2 receipts must exist without rebuilding old tables") }
                s.executeQuery("SELECT payload,inputHash FROM generation_intents WHERE id='legacy'").use { assertTrue(it.next());assertEquals("frozen-v1",it.getString(1));assertEquals("original-hash",it.getString(2)) }
                s.executeQuery("SELECT sha256 FROM tracks WHERE id='song'").use { assertTrue(it.next());assertEquals("original-audio-hash",it.getString(1)) }
            }
        }
    }
}
