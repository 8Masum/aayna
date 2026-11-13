package com.themasum.aayna.feature.viewfinder

import com.themasum.aayna.core.model.AspectRatioPreset
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.CaptureStorageMode
import com.themasum.aayna.core.model.ExposureMath
import com.themasum.aayna.core.model.FlashMode
import com.themasum.aayna.core.model.FrameBorderStyle
import com.themasum.aayna.core.model.LensAttachment
import com.themasum.aayna.core.model.TimerDuration
import com.themasum.aayna.core.model.UserSettingsSnapshot
import com.themasum.aayna.core.model.WhiteBalancePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewfinderStateReducerTest {
    @Test
    fun switchCamera_appliesCameraOpticalDefaultsAndPreservesUserSettings() {
        val customSettings =
            UserSettingsSnapshot(
                storageMode = CaptureStorageMode.ANALOG_DEVELOPING_RITUAL,
                dualSaveEnabled = true,
                gridEnabled = true,
                rollCapacity = 36
            )
        val initial =
            ViewfinderUiState(
                activeCamera = CameraCatalog.dc04SilverPulse,
                settings = customSettings,
                isGridVisible = true,
                isFrontCamera = true,
                timerDuration = TimerDuration.THREE_SECONDS,
                activePanel = ViewfinderPanel.CAMERA_SHELF
            )

        val switched = ViewfinderStateReducer.switchCamera(initial, CameraCatalog.instMInstant)

        assertEquals(CameraCatalog.instMInstant.id, switched.activeCamera.id)
        assertEquals(AspectRatioPreset.RATIO_1_1, switched.opticalSettings.aspectRatio)
        assertEquals(FrameBorderStyle.INSTANT_PRINT, switched.opticalSettings.frameStyle)
        assertEquals(CameraCatalog.instMInstant.bloom.intensity, switched.opticalSettings.bloomIntensity, 0.001f)
        assertEquals(CameraCatalog.instMInstant.eraYearOffset, switched.stampConfig.eraYear)
        assertEquals(ViewfinderPanel.NONE, switched.activePanel)
        assertTrue(switched.isGridVisible)
        assertTrue(switched.isFrontCamera)
        assertEquals(TimerDuration.THREE_SECONDS, switched.timerDuration)
        assertEquals(customSettings, switched.settings)
    }

    @Test
    fun switchCamera_allSixCamerasProduceValidDefaults() {
        val base = ViewfinderUiState()
        for (profile in CameraCatalog.all) {
            val next = ViewfinderStateReducer.switchCamera(base, profile)
            assertEquals(profile.id, next.activeCamera.id)
            assertEquals(profile.defaultAspect, next.opticalSettings.aspectRatio)
            assertEquals(profile.defaultFrame, next.opticalSettings.frameStyle)
            assertEquals(profile.eraYearOffset, next.stampConfig.eraYear)
        }
    }

    @Test
    fun stepEv_clampsToRangeAndRoundsHalfStops() {
        assertEquals(0.5f, ViewfinderStateReducer.stepEv(0.0f, 0.5f), 0.001f)
        assertEquals(2.0f, ViewfinderStateReducer.stepEv(1.5f, 1.0f), 0.001f)
        assertEquals(-2.0f, ViewfinderStateReducer.stepEv(-1.5f, -1.0f), 0.001f)
    }

    @Test
    fun cycleEv_wrapsAroundFullRange() {
        var ev = 0.0f
        val visited = mutableListOf<Float>()
        repeat(9) {
            ev = ViewfinderStateReducer.cycleEv(ev)
            visited.add(ev)
        }
        assertEquals(0.0f, visited.last(), 0.001f)
        assertEquals(9, visited.size)
    }

    @Test
    fun cycleIso_cyclesThroughStandardStepsAndWraps() {
        var iso: Int? = null
        val sequence = mutableListOf<Int?>()
        repeat(ExposureMath.STANDARD_ISO_STEPS.size) {
            iso = ViewfinderStateReducer.cycleIso(iso)
            sequence.add(iso)
        }
        assertEquals(listOf(100, 200, 400, 800, 1600, null), sequence)
        assertNull(sequence.last())
    }

    @Test
    fun cycleShutter_cyclesThroughPresetsAndWraps() {
        var shutter: Long? = null
        val seenLabels = mutableListOf<String>()
        repeat(ExposureMath.STANDARD_SHUTTER_PRESETS.size) {
            shutter = ViewfinderStateReducer.cycleShutter(shutter)
            seenLabels.add(ViewfinderStateReducer.formatShutterLabel(shutter))
        }
        assertEquals("AUTO", seenLabels.last())
        assertEquals(ExposureMath.STANDARD_SHUTTER_PRESETS.size, seenLabels.size)
    }

    @Test
    fun cycleFlashMode_cyclesAllFourModes() {
        val f1 = ViewfinderStateReducer.cycleFlashMode(FlashMode.OFF)
        val f2 = ViewfinderStateReducer.cycleFlashMode(f1)
        val f3 = ViewfinderStateReducer.cycleFlashMode(f2)
        val f4 = ViewfinderStateReducer.cycleFlashMode(f3)

        assertEquals(FlashMode.AUTO, f1)
        assertEquals(FlashMode.HARDWARE_ON, f2)
        assertEquals(FlashMode.OPTICAL_FLASH_SIM, f3)
        assertEquals(FlashMode.OFF, f4)
    }

    @Test
    fun cycleLensAttachment_cyclesAllFourAttachments() {
        val l1 = ViewfinderStateReducer.cycleLensAttachment(LensAttachment.STANDARD)
        val l2 = ViewfinderStateReducer.cycleLensAttachment(l1)
        val l3 = ViewfinderStateReducer.cycleLensAttachment(l2)
        val l4 = ViewfinderStateReducer.cycleLensAttachment(l3)

        assertEquals(LensAttachment.FISHEYE, l1)
        assertEquals(LensAttachment.STAR_4PT, l2)
        assertEquals(LensAttachment.STAR_6PT, l3)
        assertEquals(LensAttachment.STANDARD, l4)
    }

    @Test
    fun cycleWhiteBalanceAndAspectAndTimer_wrapCleanly() {
        assertEquals(WhiteBalancePreset.DAYLIGHT, ViewfinderStateReducer.cycleWhiteBalance(WhiteBalancePreset.AUTO))
        assertEquals(AspectRatioPreset.RATIO_3_2, ViewfinderStateReducer.cycleAspectRatio(AspectRatioPreset.RATIO_4_3))
        assertEquals(TimerDuration.THREE_SECONDS, ViewfinderStateReducer.cycleTimer(TimerDuration.OFF))
        assertEquals(TimerDuration.OFF, ViewfinderStateReducer.cycleTimer(TimerDuration.TEN_SECONDS))
    }

    @Test
    fun toggleFrameStyle_respectsCameraOptionalAndDefaultFrames() {
        assertEquals(
            FrameBorderStyle.FILM_35MM,
            ViewfinderStateReducer.toggleFrameStyle(CameraCatalog.rf77Classic, FrameBorderStyle.NONE)
        )
        assertEquals(
            FrameBorderStyle.NONE,
            ViewfinderStateReducer.toggleFrameStyle(CameraCatalog.rf77Classic, FrameBorderStyle.FILM_35MM)
        )
        assertEquals(
            FrameBorderStyle.INSTANT_PRINT,
            ViewfinderStateReducer.toggleFrameStyle(CameraCatalog.dc04SilverPulse, FrameBorderStyle.NONE)
        )
    }

    @Test
    fun toggleLeaksAndDust_togglesOffAndRestoresNonZeroStrength() {
        val lomoDefaults = ViewfinderStateReducer.defaultOpticalSettingsFor(CameraCatalog.lomo94Grunge)
        assertTrue(lomoDefaults.lightLeakIntensity > 0f)

        val turnedOff = ViewfinderStateReducer.toggleLeaksAndDust(CameraCatalog.lomo94Grunge, lomoDefaults)
        assertEquals(0f, turnedOff.lightLeakIntensity, 0.001f)
        assertEquals(0f, turnedOff.dustIntensity, 0.001f)

        val turnedOn = ViewfinderStateReducer.toggleLeaksAndDust(CameraCatalog.dc04SilverPulse, turnedOff)
        assertTrue(turnedOn.lightLeakIntensity > 0f)
        assertTrue(turnedOn.dustIntensity > 0f)
    }

    @Test
    fun formatLcdModeBadgeAndEvLabel_matchExpectedFormats() {
        val instant =
            ViewfinderUiState(
                settings = UserSettingsSnapshot(storageMode = CaptureStorageMode.INSTANT_DIGICAM)
            )
        val neg =
            ViewfinderUiState(
                settings = UserSettingsSnapshot(storageMode = CaptureStorageMode.NON_DESTRUCTIVE_ROLL)
            )
        val roll =
            ViewfinderUiState(
                settings = UserSettingsSnapshot(storageMode = CaptureStorageMode.ANALOG_DEVELOPING_RITUAL),
                rollShotsTaken = 4,
                rollCapacity = 24
            )

        assertEquals("INSTANT", ViewfinderStateReducer.formatLcdModeBadge(instant))
        assertEquals("NEG", ViewfinderStateReducer.formatLcdModeBadge(neg))
        assertEquals("ROLL 04/24", ViewfinderStateReducer.formatLcdModeBadge(roll))
        assertEquals("+0.5", ViewfinderStateReducer.formatEvLabel(0.5f))
        assertEquals("-1.0", ViewfinderStateReducer.formatEvLabel(-1.0f))
        assertEquals("0.0", ViewfinderStateReducer.formatEvLabel(0.0f))
    }
}
