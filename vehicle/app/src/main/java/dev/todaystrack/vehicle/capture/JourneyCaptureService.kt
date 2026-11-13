package dev.todaystrack.vehicle.capture

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import dev.todaystrack.vehicle.TodaysTrackApplication
import dev.todaystrack.vehicle.MainActivity
import dev.todaystrack.vehicle.storage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.UUID

class JourneyCaptureService : Service() {
    companion object {
        val status = MutableStateFlow("기록 대기")
        val active = MutableStateFlow(false)
        const val STOP = "dev.todaystrack.vehicle.STOP_CAPTURE"
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val buffer = Channel<SdkReading>(128)
    private var adapter: PleosCaptureAdapter? = null
    private var writer: Job? = null
    private var epoch: CaptureEpoch? = null
    private val lifetime = CaptureStartGate()
    private val stopping get() = lifetime.isStopped
    private var failed = false
    private val repository by lazy { CaptureRepository((application as TodaysTrackApplication).database) }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("capture", "여정 기록", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, JourneyCaptureService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        startForeground(100, Notification.Builder(this, "capture").setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("오늘의 트랙 · 기록 중").setContentText("기록 상태 확인 또는 마무리")
            .setContentIntent(open).setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(Notification.Action.Builder(null, "기록 마무리", stop).build()).build())
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { finishCapture(); return START_NOT_STICKY }
        if (!lifetime.tryStart()) return START_NOT_STICKY
        active.value = true
        status.value = "SDK 연결·기록 복구 중"
        writer = scope.launch {
            try {
                val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                val boot = withContext(Dispatchers.IO) { runCatching { File("/proc/sys/kernel/random/boot_id").readText().trim() }.getOrDefault("unknown") }
                val opened = repository.open(prefs.getString("kind", "DAY")!!, prefs.getString("genre", "잔잔한 팝")!!,
                    prefs.getString("lyrics", "INSTRUMENTAL")!!, boot, SystemClock.elapsedRealtime(), System.currentTimeMillis())
                epoch = opened
                var previousSourceTime: Long? = null
                var previousDistance: Double? = null
                val policy = CapturePolicy()
                // Room open suspends. STOP may have closed this lifetime while it was pending;
                // retain the opened epoch for normal sealing, but never start a late adapter.
                lifetime.runIfActive {
                    adapter = PleosCaptureAdapter(this@JourneyCaptureService) { reading ->
                        if (!stopping && buffer.trySend(reading).isFailure) {
                            failed = true
                            status.value = "저장 대기열 초과 · 수집 중지, 복귀 시 누락 가능 범위 표시"
                            finishCapture()
                        }
                    }.also { it.start() }
                }
                for (reading in buffer) {
                    val quality = when (reading.kind) {
                        "LOCATION" -> when {
                            reading.latitude == null || reading.longitude == null || !reading.latitude.isFinite() || !reading.longitude.isFinite() ||
                                reading.latitude !in -90.0..90.0 || reading.longitude !in -180.0..180.0 -> "INVALID"
                            else -> policy.locationQuality(reading.sourceTimestampMs, opened.startedAtUtc, reading.utc, previousSourceTime)
                        }
                        "ODOMETER" -> when {
                            reading.odometerKm == null || !reading.odometerKm.isFinite() || reading.odometerKm < 0 -> "INVALID"
                            previousDistance != null && reading.odometerKm < previousDistance!! -> "RESET"
                            else -> "VALID"
                        }
                        else -> "ERROR"
                    }
                    repository.append(CaptureEvent(eventId = UUID.randomUUID().toString(), sessionId = opened.sessionId,
                        epochId = opened.id, bootId = opened.bootId, kind = reading.kind, source = reading.source,
                        receivedAtUtc = reading.utc, receivedElapsedMs = reading.elapsed, sourceTimestampMs = reading.sourceTimestampMs,
                        sourceTimeBasis = if (reading.sourceTimestampMs != null) "SDK_UTC_MILLIS" else null,
                        requestStartedElapsedMs = reading.requestElapsed, latitude = reading.latitude, longitude = reading.longitude,
                        sourceConfidence = reading.accuracy, odometerKm = reading.odometerKm, quality = quality, detail = reading.detail,
                        policyVersion = "capture-v2"))
                    if (quality == "FRESH") previousSourceTime = reading.sourceTimestampMs
                    if (reading.kind == "ODOMETER" && quality != "INVALID") previousDistance = reading.odometerKm
                    if (!failed) status.value = if (quality in setOf("FRESH", "VALID")) "기록 저장됨 · ${reading.source}" else "일부 관측 확인 중 · $quality"
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                failed = true
                status.value = "기록 중지 · ${error.javaClass.simpleName} · 저장된 기록은 유지됩니다"
                finishCapture()
            }
        }
        return START_NOT_STICKY
    }
    private fun finishCapture() {
        if (!lifetime.stop()) return
        runCatching { adapter?.close() }.onFailure { failed = true }
        buffer.close()
        scope.launch {
            writer?.join()
            // Failed writes/overflow deliberately leave an open epoch for recovery.
            if (!failed) try {
                epoch?.let { repository.close(it.id, seal = true) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                failed = true
                status.value = "기록 마감 저장 실패 · ${error.javaClass.simpleName} · 저장된 기록은 유지됩니다"
            }
            active.value = false
            if (!failed) status.value = "기록이 모였습니다 · 요약을 확인하세요"
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        lifetime.stop()
        runCatching { adapter?.close() }
        buffer.close()
        scope.cancel()
        active.value = false
        super.onDestroy()
    }
}
