package com.abdapps.visorwifi.aplocator

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Gestor de sensores de orientación para AP Locator.
 *
 * Responsabilidades:
 * - Registrar listener de sensores de orientación del dispositivo.
 * - Priorizar `TYPE_ACCELEROMETER` + `TYPE_MAGNETIC_FIELD` usando `SensorManager.getRotationMatrix()`.
 * - Fallback a `TYPE_ROTATION_VECTOR` si no hay magnetómetro disponible.
 * - Suavizar azimut en tiempo real con un filtro de paso bajo (low-pass filter) y manejar interpolación circular (359° ↔ 0°).
 * - Exponer el azimut actual en grados [0.0f, 360.0f) mediante `StateFlow<Float>`.
 * - Gestionar ciclo de vida (start/stop listeners) adecuadamente para prevenir consumo inútil de batería y memory leaks.
 */
class SensorOrientationManager(context: Context) : SensorEventListener {

    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val _azimuthFlow = MutableStateFlow(0f)
    val azimuthFlow: StateFlow<Float> = _azimuthFlow.asStateFlow()

    private val _isSensorAvailable = MutableStateFlow(true)
    val isSensorAvailable: StateFlow<Boolean> = _isSensorAvailable.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var rotationVector: Sensor? = null

    private var useRotationVectorFallback = false

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var hasGravity = false
    private var hasGeomagnetic = false

    private var currentSmoothAzimuth = 0f
    private var isFirstSample = true

    // Factor de suavizado para el filtro de paso bajo (alpha: 0.15f para suavidad de animación)
    private val alpha = 0.15f

    init {
        if (sensorManager == null) {
            _isSensorAvailable.value = false
            _errorMessage.value = "El dispositivo no dispone del servicio de sensores."
        } else {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

            if (accelerometer == null || magnetometer == null) {
                if (rotationVector != null) {
                    useRotationVectorFallback = true
                } else {
                    _isSensorAvailable.value = false
                    _errorMessage.value = "El dispositivo no dispone de magnetómetro ni sensor de vector de rotación."
                }
            }
        }
    }

    /**
     * Inicia la captura de sensores con una tasa adecuada para UI (SENSOR_DELAY_UI).
     */
    fun startListening() {
        val manager = sensorManager ?: return
        if (!_isSensorAvailable.value) return

        isFirstSample = true

        if (useRotationVectorFallback) {
            rotationVector?.let {
                manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        } else {
            accelerometer?.let {
                manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
            magnetometer?.let {
                manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    /**
     * Detiene la captura de sensores para ahorrar batería y evitar memory leaks.
     */
    fun stopListening() {
        sensorManager?.unregisterListener(this)
        hasGravity = false
        hasGeomagnetic = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (useRotationVectorFallback && event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val rawAzimuthRad = orientationAngles[0]
            val rawAzimuthDeg = (Math.toDegrees(rawAzimuthRad.toDouble()).toFloat() + 360f) % 360f
            updateSmoothAzimuth(rawAzimuthDeg)
            return
        }

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            lowPassFilter(event.values, gravity)
            hasGravity = true
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            lowPassFilter(event.values, geomagnetic)
            hasGeomagnetic = true
        }

        if (hasGravity && hasGeomagnetic) {
            val success = SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)
            if (success) {
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val rawAzimuthRad = orientationAngles[0]
                val rawAzimuthDeg = (Math.toDegrees(rawAzimuthRad.toDouble()).toFloat() + 360f) % 360f
                updateSmoothAzimuth(rawAzimuthDeg)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No se requiere acción adicional
    }

    /**
     * Actualiza el azimut aplicando interpolación circular adecuada para 359° ↔ 0°.
     */
    private fun updateSmoothAzimuth(targetAzimuthDeg: Float) {
        if (isFirstSample) {
            currentSmoothAzimuth = targetAzimuthDeg
            isFirstSample = false
        } else {
            // Manejar diferencia circular para evitar que 359° ↔ 1° cause una rotación de 358°
            var diff = targetAzimuthDeg - currentSmoothAzimuth
            while (diff < -180f) diff += 360f
            while (diff > 180f) diff -= 360f

            currentSmoothAzimuth = (currentSmoothAzimuth + alpha * diff + 360f) % 360f
        }
        _azimuthFlow.value = currentSmoothAzimuth
    }

    /**
     * Filtro de paso bajo básico para suavizar la aceleración y campos magnéticos.
     */
    private fun lowPassFilter(input: FloatArray, output: FloatArray) {
        val filterAlpha = 0.25f
        for (i in input.indices) {
            output[i] = output[i] + filterAlpha * (input[i] - output[i])
        }
    }
}
