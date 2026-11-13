package dev.todaystrack.vehicle.capture

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData

data class SdkReading(
    val kind: String, val source: String,
    val utc: Long = System.currentTimeMillis(), val elapsed: Long = SystemClock.elapsedRealtime(),
    val sourceTimestampMs: Long? = null, val latitude: Double? = null, val longitude: Double? = null,
    val accuracy: Double? = null, val odometerKm: Double? = null, val requestElapsed: Long? = null,
    val detail: String? = null
)

/** Main-thread SDK lifetime; does not combine asynchronous GPS and odometer events. */
class PleosCaptureAdapter(private val context: Context, private val emit: (SdkReading) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var fused: FusedLocation? = null
    private val lifetime = CaptureStartGate()
    private var inFlight = false
    private var consecutiveErrors = 0
    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            if (location == null) { dispatch(SdkReading("SOURCE_ERROR", "LOCATION", detail = "NULL_LOCATION")); return }
            dispatch(SdkReading("LOCATION", "LOCATION", sourceTimestampMs = location.timestampMillis,
                latitude = location.latitude, longitude = location.longitude, accuracy = location.confidence.toDouble()))
        }
        override fun onError(exception: Exception) {
            dispatch(SdkReading("SOURCE_ERROR", "LOCATION", detail = exception.javaClass.simpleName))
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            if (lifetime.isStopped || consecutiveErrors >= 2) return
            if (!inFlight) {
                val start = SystemClock.elapsedRealtime()
                inFlight = true
                vehicle?.getOdometer()?.getCurrentDistanceDriven(
                    onComplete = { value ->
                        val reading = SdkReading("ODOMETER", "ODOMETER", odometerKm = value?.toDouble(), requestElapsed = start)
                        onMainWhileActive {
                            inFlight = false
                            consecutiveErrors = 0
                            emit(reading)
                        }
                    },
                    onFailed = { error ->
                        val reading = SdkReading("SOURCE_ERROR", "ODOMETER", requestElapsed = start, detail = error.toString())
                        onMainWhileActive {
                            inFlight = false
                            consecutiveErrors++
                            emit(reading)
                        }
                    })
            }
            // Initial sampling recipe, not a freshness/SLA acceptance threshold.
            if (!lifetime.isStopped) handler.postDelayed(this, 2000)
        }
    }
    private fun onMainWhileActive(action: () -> Unit) {
        if (Looper.myLooper() == handler.looper) lifetime.runIfActive(action)
        else handler.post { lifetime.runIfActive(action) }
    }
    private fun dispatch(reading: SdkReading) = onMainWhileActive { emit(reading) }
    fun start() {
        if (!lifetime.tryStart()) return
        vehicle = Vehicle(context.applicationContext).also { it.initialize() }
        fused = FusedLocation(context.applicationContext).also {
            if (it.isSupported()) it.initialize()
            else { dispatch(SdkReading("SOURCE_ERROR", "LOCATION", detail = "UNSUPPORTED")); return@also }
        }
        handler.postDelayed({
            lifetime.runIfActive {
                if (fused?.isSupported() == true) fused?.registerFusedLocationCallback(listener)
                // No getLastKnownLocation: previous cache is not a new observation.
                tick.run()
            }
        }, 3000)
    }
    fun close() {
        if (!lifetime.stop()) return
        handler.removeCallbacksAndMessages(null)
        val closingFused = fused
        val closingVehicle = vehicle
        fused = null
        vehicle = null
        try { closingFused?.unregisterFusedLocationCallback(listener) } finally {
            try { closingFused?.release() } finally { closingVehicle?.release() }
        }
    }
}
