package com.themasum.aayna.core.model

import kotlin.math.ln
import kotlin.math.roundToInt

data class ShutterPreset(val label: String, val exposureTimeNs: Long?)

data class ShaderExposureFallback(val evGainStops: Float, val shaderNoiseBoost: Float, val digitalBoostStops: Float)

object ExposureMath {
    val STANDARD_ISO_STEPS: List<Int?> = listOf(null, 50, 100, 200, 400, 800, 1600)

    val STANDARD_SHUTTER_PRESETS: List<ShutterPreset> =
        listOf(
            ShutterPreset("AUTO", null),
            ShutterPreset("1/1000", 1_000_000L),
            ShutterPreset("1/500", 2_000_000L),
            ShutterPreset("1/250", 4_000_000L),
            ShutterPreset("1/125", 8_000_000L),
            ShutterPreset("1/60", 16_666_667L),
            ShutterPreset("1/30", 33_333_333L),
            ShutterPreset("1/15", 66_666_667L),
            ShutterPreset("1/8", 125_000_000L),
            ShutterPreset("1/4", 250_000_000L)
        )

    private const val REFERENCE_ISO = 100.0
    private const val REFERENCE_SHUTTER_NS = 16_666_667.0 // 1/60s baseline

    /**
     * Returns true only when the sensor supports manual exposure and both ISO and shutter
     * speed are explicitly supplied, allowing Camera2 `CONTROL_AE_MODE_OFF` without AE stalls.
     */
    fun isFullManualExposure(hasManualSensor: Boolean, iso: Int?, shutterNs: Long?): Boolean =
        hasManualSensor && iso != null && shutterNs != null

    fun clampIso(requestedIso: Int?, minIso: Int, maxIso: Int): Int? {
        if (requestedIso == null) return null
        val low = minOf(minIso, maxIso)
        val high = maxOf(minIso, maxIso)
        return requestedIso.coerceIn(low, high)
    }

    fun clampExposureTimeNs(requestedNs: Long?, minNs: Long, maxNs: Long): Long? {
        if (requestedNs == null) return null
        val low = minOf(minNs, maxNs)
        val high = maxOf(minNs, maxNs)
        return requestedNs.coerceIn(low, high)
    }

    fun evToCompensationSteps(ev: Float, stepNumerator: Int, stepDenominator: Int, minIndex: Int, maxIndex: Int): Int {
        if (stepNumerator == 0 || stepDenominator == 0) return 0.coerceIn(minIndex, maxIndex)
        val stepSize = stepNumerator.toFloat() / stepDenominator.toFloat()
        if (stepSize == 0f) return 0.coerceIn(minIndex, maxIndex)
        val rawIndex = (ev / stepSize).roundToInt()
        val low = minOf(minIndex, maxIndex)
        val high = maxOf(minIndex, maxIndex)
        return rawIndex.coerceIn(low, high)
    }

    /**
     * Computes shader-side EV/ISO/shutter fallback stops.
     *
     * Full manual exposure (`hasManualSensor && iso != null && shutterNs != null`) relies on the
     * hardware sensor for brightness and only applies shader grain boost at elevated ISO.
     * When AE is active or only one of ISO/shutter is specified (partial manual), Camera2 falls
     * back to AE/EV, so negative/positive ISO stops (including ISO 50 < ISO 100), shutter stops,
     * and software EV fallback (unless [hardwareEvApplied] is true) are applied cleanly.
     */
    fun computeShaderFallbackGains(
        hasManualSensor: Boolean,
        ev: Float,
        iso: Int?,
        shutterNs: Long?,
        hardwareEvApplied: Boolean = false
    ): ShaderExposureFallback {
        val isoStops =
            if (iso != null && iso > 0) {
                (ln(iso.toDouble() / REFERENCE_ISO) / ln(2.0)).toFloat().coerceIn(-3.0f, 5.0f)
            } else {
                0f
            }

        val noiseBoost =
            when {
                iso == null -> 0f
                iso <= 100 -> 0f
                else -> (isoStops * 0.28f).coerceIn(0f, 1.4f)
            }

        if (isFullManualExposure(hasManualSensor, iso, shutterNs)) {
            return ShaderExposureFallback(
                evGainStops = 0f,
                shaderNoiseBoost = noiseBoost,
                digitalBoostStops = 0f
            )
        }

        val shutterStops =
            if (shutterNs != null && shutterNs > 0L) {
                (ln(shutterNs.toDouble() / REFERENCE_SHUTTER_NS) / ln(2.0)).toFloat().coerceIn(-3.0f, 3.0f)
            } else {
                0f
            }

        val digitalBoost = (isoStops * 0.35f + shutterStops * 0.25f).coerceIn(-2.0f, 2.5f)
        val evFallback = if (hardwareEvApplied) 0f else ev.coerceIn(-2.0f, 2.0f)
        return ShaderExposureFallback(
            evGainStops = evFallback,
            shaderNoiseBoost = noiseBoost,
            digitalBoostStops = digitalBoost
        )
    }
}
