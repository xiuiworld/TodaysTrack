package dev.todaystrack.vehicle.generation

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.work.*
import dev.todaystrack.vehicle.TodaysTrackApplication
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File

/** Bounded deferred transport/download. It never directly calls a music provider. */
class GenerationOutboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val app=applicationContext as TodaysTrackApplication
        val dao=app.database.generationDao()
        val sessionId=inputData.getString("sessionId") ?: return Result.failure()
        var intent=dao.forSession(sessionId) ?: return Result.failure()
        val api=GenerationApiClient(app,app.identity)
        try {
            if(intent.state=="AVAILABLE") return Result.success()
            if(intent.jobId==null) {
                // Acceptance-response loss must not require a fresh quote or spare budget.
                val existing=try { api.request("GET","/v1/generation-jobs/by-intent/${intent.id}") }
                    catch(error: Exception) { if(error.message=="API_404") null else throw error }
                val job=existing ?: run {
                    if(!GenerationRecoveryPolicy.mayCreateJob(intent.state)) {
                        dao.update(intent.copy(state=if(intent.state=="REQUEST_HELD") intent.state else "ACCEPTANCE_UNCERTAIN",errorCode="ORIGINAL_JOB_NOT_FOUND"))
                        return Result.success()
                    }
                    intent=intent.copy(state="PREPARING",errorCode=null);dao.update(intent)
                    val snapshot=api.request("POST","/v1/input-snapshots",JSONObject().put("localIntentId",intent.id).put("snapshot",intent.payload))
                    check(snapshot.getString("inputHash")==intent.inputHash)
                    val quote=api.request("POST","/v1/generation-quotes",JSONObject().put("snapshotId",snapshot.getString("snapshotId")))
                    // Durable before send: all later recovery is lookup-only until acknowledged.
                    intent=intent.copy(state="ACCEPTANCE_UNCERTAIN");dao.update(intent)
                    api.request("POST","/v1/generation-jobs",JSONObject().put("localIntentId",intent.id)
                        .put("snapshotId",snapshot.getString("snapshotId")).put("quoteId",quote.getString("quoteId")))
                }
                check(job.getString("inputHash")==intent.inputHash)
                intent=intent.copy(jobId=job.getString("jobId"),state=job.getString("state"),mode=job.getString("mode"),errorCode=null)
                dao.update(intent)
            }
            repeat(60) {
                val job=api.request("GET","/v1/generation-jobs/${intent.jobId}")
                check(job.getString("inputHash")==intent.inputHash)
                val state=job.getString("state")
                intent=intent.copy(state=state,errorCode=null);dao.update(intent)
                when {
                    state=="READY" -> {
                        val path=File(app.filesDir,"tracks/${intent.jobId}.mp3")
                        api.download(job.getString("assetPath"),path,job.getString("sha256"),job.getLong("bytes"))
                        val retriever=MediaMetadataRetriever()
                        val duration=try { retriever.setDataSource(path.absolutePath);retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L } finally { retriever.release() }
                        check(duration>0)
                        dao.insertTrack(Track(intent.jobId!!,intent.jobId!!,sessionId,intent.inputHash,path.absolutePath,job.getString("sha256"),path.length(),
                            job.getString("mode"),if(job.getString("mode")=="FIXTURE") "연결 검증용 기존 음원 · 대역" else "오늘의 트랙 · 새 AI 음악",duration))
                        dao.update(intent.copy(state="AVAILABLE",errorCode=null))
                        return Result.success()
                    }
                    state=="RECONCILING" || state.startsWith("FAILED") -> return Result.success()
                }
                delay(3000)
            }
            dao.update(intent.copy(state="SERVER_PENDING"))
            return if(runAttemptCount<2) Result.retry() else Result.success()
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(error: Exception) {
            if(intent.state=="ACCEPTANCE_UNCERTAIN") {
                dao.update(intent.copy(errorCode=error.message?.takeIf { it.startsWith("API_") } ?: error.javaClass.simpleName))
                return if(runAttemptCount<2) Result.retry() else Result.failure()
            }
            if(error.message in setOf("API_401","API_403","API_409")) {
                dao.update(intent.copy(state=if(error.message=="API_409") "REQUEST_HELD" else "AUTH_REQUIRED",errorCode=error.message))
                return Result.failure()
            }
            // PREPARING is new-version provenance for failures before the accept call.
            dao.update(intent.copy(state=if(intent.jobId==null && intent.state in setOf("LOCAL_PENDING","PREPARING")) intent.state else "TRANSPORT_PENDING",errorCode=error.javaClass.simpleName))
            return if(runAttemptCount<2) Result.retry() else Result.failure()
        }
    }
    companion object {
        suspend fun recoverPending(context: Context) {
            val app=context.applicationContext as TodaysTrackApplication
            for(intent in app.database.generationDao().unfinished()) {
                if(GenerationRecoveryPolicy.automaticallyResume(intent.state)) enqueue(app,intent.sessionId,intent.id)
            }
        }
        fun enqueue(context: Context, sessionId: String, intentId: String) {
            WorkManager.getInstance(context).enqueueUniqueWork("generation-$intentId",ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<GenerationOutboxWorker>().setInputData(workDataOf("sessionId" to sessionId))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }
}
