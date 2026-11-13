package com.themasum.aayna.core.model

import kotlin.math.ln
import kotlin.math.roundToInt

data class ShutterPreset(val label: String, val exposureTimeNs: Long?)

data class ShaderExposureFallback(val evGainStops: Float, val shaderNoiseBoost: Float, val digitalBoostStops: Float)

object ExposureMath {
    val STANDARD_ISO_STEPS: List<Int?> = listOf(null, 100, 200, 400, 800, 1600)

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

    fun computeShaderFallbackGains(
        hasManualSensor: Boolean,
        ev: Float,
        iso: Int?,
        shutterNs: Long?
    ): ShaderExposureFallback {
        val isoStops =
            if (iso != null && iso > 0) {
                (ln(iso.toDouble() / REFERENCE_ISO) / ln(2.0)).toFloat().coerceAtLeast(0f)
            } else {
                0f
            }

        val noiseBoost =
            when {
                iso == null -> 0f
                iso <= 100 -> 0f
                else -> (isoStops * 0.28f).coerceIn(0f, 1.4f)
            }

        if (hasManualSensor) {
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
        return ShaderExposureFallback(
            evGainStops = ev.coerceIn(-2.0f, 2.0f),
            shaderNoiseBoost = noiseBoost,
            digitalBoostStops = digitalBoost
        )
    }
}
