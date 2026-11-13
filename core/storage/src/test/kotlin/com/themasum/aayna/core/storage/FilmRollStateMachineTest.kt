package com.themasum.aayna.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmRollStateMachineTest {
    @Test
    fun `24-exposure roll progresses from frame 1 to 24 and completes at final frame`() {
        var state =
            FilmRollStateMachine.createNewRoll(
                rollId = "roll_24",
                cameraId = "d_98_disposable",
                capacity = 24,
                nowEpochMs = 1_000L
            )

        assertEquals(0, state.shotsTaken)
        assertEquals(24, state.remainingShots)
        assertFalse(state.isCompleted)
        assertNull(state.completedAtEpochMs)

        for (frame in 1..24) {
            val result = FilmRollStateMachine.advanceShot(state, nowEpochMs = 1_000L + frame)
            assertEquals(frame, result.assignedFrameNumber)
            state = result.updatedState
            assertEquals(frame, state.shotsTaken)
            assertEquals(24 - frame, state.remainingShots)
            if (frame < 24) {
                assertFalse(result.rollCompleted)
                assertFalse(state.isCompleted)
                assertNull(state.completedAtEpochMs)
            } else {
                assertTrue(result.rollCompleted)
                assertTrue(state.isCompleted)
                assertEquals(1_024L, state.completedAtEpochMs)
            }
        }
    }

    @Test
    fun `36-exposure roll progresses to boundary and rejects extra shot`() {
        var state =
            FilmRollStateMachine.createNewRoll(
                rollId = "roll_36",
                cameraId = "rf_77_classic",
                capacity = 36,
                nowEpochMs = 2_000L
            )

        for (frame in 1..36) {
            val result = FilmRollStateMachine.advanceShot(state, nowEpochMs = 2_000L + frame)
            state = result.updatedState
            assertEquals(frame == 36, result.rollCompleted)
        }

        assertEquals(36, state.shotsTaken)
        assertEquals(0, state.remainingShots)
        assertTrue(state.isCompleted)

        val thrown =
            runCatching {
                FilmRollStateMachine.advanceShot(state, nowEpochMs = 3_000L)
            }.exceptionOrNull()
        assertNotNull(thrown)
        assertTrue(thrown is IllegalStateException)
    }

    @Test
    fun `loadNewRoll deactivates previous roll and resets counter to zero`() {
        val completedRoll =
            FilmRollState(
                rollId = "roll_old",
                cameraId = "d_98_disposable",
                capacity = 24,
                shotsTaken = 24,
                createdAtEpochMs = 1_000L,
                completedAtEpochMs = 5_000L,
                isActive = true
            )

        val (archived, fresh) =
            FilmRollStateMachine.loadNewRoll(
                previous = completedRoll,
                newRollId = "roll_new",
                cameraId = "lomo_94_grunge",
                capacity = 36,
                nowEpochMs = 6_000L
            )

        assertNotNull(archived)
        assertFalse(archived!!.isActive)
        assertEquals(5_000L, archived.completedAtEpochMs)

        assertEquals("roll_new", fresh.rollId)
        assertEquals("lomo_94_grunge", fresh.cameraId)
        assertEquals(36, fresh.capacity)
        assertEquals(0, fresh.shotsTaken)
        assertEquals(36, fresh.remainingShots)
        assertTrue(fresh.isActive)
        assertNull(fresh.completedAtEpochMs)
    }
}
