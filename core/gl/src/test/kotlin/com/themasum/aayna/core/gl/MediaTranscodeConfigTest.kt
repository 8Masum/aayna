package com.themasum.aayna.core.gl

import com.themasum.aayna.core.model.AspectRatioPreset
import com.themasum.aayna.core.model.OpticalSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTranscodeConfigTest {
    @Test
    fun d3dMp4Config_buildsPingPongCycleAndTargetLoopDuration() {
        assertEquals(listOf(0, 1, 2, 3, 2, 1), D3dMp4Config.buildCycleIndices(4, pingPong = true))
        assertEquals(listOf(0, 1, 2, 3), D3dMp4Config.buildCycleIndices(4, pingPong = false))
        assertEquals(listOf(0, 1), D3dMp4Config.buildCycleIndices(2, pingPong = true))

        for (delay in listOf(60, 80, 100, 140, 200)) {
            val plan =
                D3dMp4Config.buildPlan(
                    sourceWidth = 720,
                    sourceHeight = 960,
                    frameCount = 4,
                    frameDelayMs = delay,
                    pingPong = true
                )
            assertEquals(720, plan.width)
            assertEquals(960, plan.height)
            assertTrue(
                "Expected totalDurationMs in [2400, 3200] for delay=$delay, got ${plan.totalDurationMs}",
                plan.totalDurationMs in 2400..3200
            )
            assertEquals(plan.frameSequence.size, plan.presentationTimesUs.size)
            for (i in 1 until plan.presentationTimesUs.size) {
                assertTrue(plan.presentationTimesUs[i] > plan.presentationTimesUs[i - 1])
            }
        }

        // 180ms * 6-frame cycle = 1080ms -> closest full-cycle multiple to 2800ms is 3 cycles (3240ms)
        val plan180 =
            D3dMp4Config.buildPlan(
                sourceWidth = 720,
                sourceHeight = 960,
                frameCount = 4,
                frameDelayMs = 180,
                pingPong = true
            )
        assertEquals(3, plan180.repeatCount)
        assertEquals(3240, plan180.totalDurationMs)

        // Slow delay (350ms * 6-frame cycle = 2100ms) keeps 1 seamless full cycle rather than jumping to 4200ms
        val slowPlan =
            D3dMp4Config.buildPlan(
                sourceWidth = 720,
                sourceHeight = 960,
                frameCount = 4,
                frameDelayMs = 350,
                pingPong = true
            )
        assertEquals(1, slowPlan.repeatCount)
        assertEquals(2100, slowPlan.totalDurationMs)
    }

    @Test
    fun d3dMp4Config_capsLargeDimensionsAndEnforcesEvenAlignment() {
        val (w, h) = D3dMp4Config.computeDimensions(sourceWidth = 1501, sourceHeight = 2001, maxEdgePx = 1080)
        assertEquals(0, w % 2)
        assertEquals(0, h % 2)
        assertEquals(1080, h)
        assertEquals(810, w)
    }

    @Test
    fun videoTranscodeConfig_bakesRotationCropAndTrimBoundsCleanly() {
        val plan =
            VideoTranscodeConfig.buildPlan(
                sourceWidth = 1920,
                sourceHeight = 1080,
                sourceRotationDegrees = 90,
                sourceDurationMs = 10_000L,
                trimStartMs = 1_500L,
                trimEndMs = 6_500L,
                opticalSettings =
                OpticalSettings(
                    aspectRatio = AspectRatioPreset.RATIO_16_9,
                    cropAspectRatio = AspectRatioPreset.RATIO_1_1
                )
            )
        assertEquals(90, plan.totalRotationDegrees)
        assertEquals(1080, plan.displayWidth)
        assertEquals(1920, plan.displayHeight)
        assertEquals(1080, plan.outputWidth)
        assertEquals(1080, plan.outputHeight)
        assertEquals(1_500L, plan.trimStartMs)
        assertEquals(6_500L, plan.trimEndMs)
        assertEquals(5_000L, plan.durationMs)
        assertEquals(1_500_000L, plan.trimStartUs)
        assertEquals(6_500_000L, plan.trimEndUs)
    }

    @Test
    fun videoTranscodeConfig_honorsCropPortraitOverrideAndUserRotation() {
        val plan =
            VideoTranscodeConfig.buildPlan(
                sourceWidth = 1920,
                sourceHeight = 1080,
                sourceRotationDegrees = 90,
                sourceDurationMs = 8_000L,
                trimStartMs = 0L,
                trimEndMs = Long.MAX_VALUE,
                opticalSettings =
                OpticalSettings(
                    aspectRatio = AspectRatioPreset.RATIO_4_3,
                    cropAspectRatio = AspectRatioPreset.RATIO_16_9,
                    cropPortraitOverride = false,
                    rotationDegrees = 90
                )
            )
        // 90 container + 90 user = 180 -> display remains 1920x1080
        assertEquals(180, plan.totalRotationDegrees)
        assertEquals(1920, plan.displayWidth)
        assertEquals(1080, plan.displayHeight)
        assertEquals(1920, plan.outputWidth)
        assertEquals(1080, plan.outputHeight)
        assertEquals(8_000L, plan.durationMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun videoTranscodeConfig_rejectsInvalidTrimRange() {
        VideoTranscodeConfig.buildPlan(
            sourceWidth = 1280,
            sourceHeight = 720,
            sourceRotationDegrees = 0,
            sourceDurationMs = 5_000L,
            trimStartMs = 3_000L,
            trimEndMs = 3_050L, // < MIN_TRIM_DURATION_MS (100ms)
            opticalSettings = OpticalSettings()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun d3dMp4Config_rejectsZeroFrameCount() {
        D3dMp4Config.buildCycleIndices(0)
    }
}
