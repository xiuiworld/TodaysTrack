package dev.todaystrack.vehicle

import android.app.Application
import androidx.room.Room
import dev.todaystrack.vehicle.storage.CaptureDatabase
import dev.todaystrack.vehicle.auth.DevelopmentIdentity
import dev.todaystrack.vehicle.generation.GenerationOutboxWorker
import kotlinx.coroutines.*

class TodaysTrackApplication : Application() {
    val identity by lazy { DevelopmentIdentity(this) }
    override fun onCreate() {
        super.onCreate(); identity
        // One finite scan closes freeze→enqueue/process-death gaps; WorkManager owns transport.
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch { GenerationOutboxWorker.recoverPending(this@TodaysTrackApplication) }
    }
    val database: CaptureDatabase by lazy {
        Room.databaseBuilder(this, CaptureDatabase::class.java, "todaystrack.db").addMigrations(CaptureDatabase.MIGRATION_1_2,CaptureDatabase.MIGRATION_2_3).build()
    }
}
