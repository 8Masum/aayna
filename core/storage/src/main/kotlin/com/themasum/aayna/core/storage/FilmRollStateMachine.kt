package com.themasum.aayna.core.storage

data class FilmRollState(
    val rollId: String,
    val cameraId: String,
    val capacity: Int,
    val shotsTaken: Int,
    val createdAtEpochMs: Long,
    val completedAtEpochMs: Long? = null,
    val isActive: Boolean = true
) {
    val remainingShots: Int
        get() = (capacity - shotsTaken).coerceAtLeast(0)

    val isCompleted: Boolean
        get() = shotsTaken >= capacity
}

data class RollAdvanceResult(val updatedState: FilmRollState, val assignedFrameNumber: Int, val rollCompleted: Boolean)

object FilmRollStateMachine {
    fun createNewRoll(rollId: String, cameraId: String, capacity: Int, nowEpochMs: Long): FilmRollState {
        val safeCapacity = capacity.coerceAtLeast(1)
        return FilmRollState(
            rollId = rollId,
            cameraId = cameraId,
            capacity = safeCapacity,
            shotsTaken = 0,
            createdAtEpochMs = nowEpochMs,
            completedAtEpochMs = null,
            isActive = true
        )
    }

    fun advanceShot(current: FilmRollState, nowEpochMs: Long): RollAdvanceResult {
        check(!current.isCompleted) {
            "Film roll ${current.rollId} is already completed (${current.shotsTaken}/${current.capacity})"
        }
        val nextShots = current.shotsTaken + 1
        val completed = nextShots >= current.capacity
        val updated =
            current.copy(
                shotsTaken = nextShots,
                completedAtEpochMs = if (completed) nowEpochMs else null,
                isActive = true
            )
        return RollAdvanceResult(
            updatedState = updated,
            assignedFrameNumber = nextShots,
            rollCompleted = completed
        )
    }

    fun loadNewRoll(
        previous: FilmRollState?,
        newRollId: String,
        cameraId: String,
        capacity: Int,
        nowEpochMs: Long
    ): Pair<FilmRollState?, FilmRollState> {
        val archivedPrevious =
            previous?.copy(
                isActive = false,
                completedAtEpochMs = previous.completedAtEpochMs ?: nowEpochMs
            )
        val freshRoll =
            createNewRoll(
                rollId = newRollId,
                cameraId = cameraId,
                capacity = capacity,
                nowEpochMs = nowEpochMs
            )
        return archivedPrevious to freshRoll
    }
}
