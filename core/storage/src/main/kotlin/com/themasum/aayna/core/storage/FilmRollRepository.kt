package com.themasum.aayna.core.storage

import com.themasum.aayna.core.storage.db.FilmRollDao
import com.themasum.aayna.core.storage.db.FilmRollEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class FilmRollRepository(private val filmRollDao: FilmRollDao) {
    val activeRollFlow: Flow<FilmRollEntity?> = filmRollDao.observeActiveRoll()

    val allRollsFlow: Flow<List<FilmRollEntity>> = filmRollDao.observeAll()

    suspend fun getOrCreateActiveRoll(
        cameraId: String,
        capacity: Int,
        nowEpochMs: Long = System.currentTimeMillis()
    ): FilmRollEntity {
        val existing = filmRollDao.getActiveRoll()
        if (existing != null) {
            return existing
        }
        val fresh =
            FilmRollStateMachine
                .createNewRoll(
                    rollId = generateRollId(),
                    cameraId = cameraId,
                    capacity = capacity,
                    nowEpochMs = nowEpochMs
                ).toEntity()
        filmRollDao.upsert(fresh)
        return fresh
    }

    suspend fun recordShot(
        cameraId: String,
        capacity: Int,
        nowEpochMs: Long = System.currentTimeMillis()
    ): RollAdvanceResult {
        val current = getOrCreateActiveRoll(cameraId, capacity, nowEpochMs).toState()
        val result = FilmRollStateMachine.advanceShot(current, nowEpochMs)
        filmRollDao.upsert(result.updatedState.toEntity())
        return result
    }

    suspend fun loadNewRoll(
        cameraId: String,
        capacity: Int,
        nowEpochMs: Long = System.currentTimeMillis()
    ): FilmRollEntity {
        val current = filmRollDao.getActiveRoll()?.toState()
        val (archived, fresh) =
            FilmRollStateMachine.loadNewRoll(
                previous = current,
                newRollId = generateRollId(),
                cameraId = cameraId,
                capacity = capacity,
                nowEpochMs = nowEpochMs
            )
        filmRollDao.deactivateAll()
        if (archived != null) {
            filmRollDao.upsert(archived.toEntity())
        }
        val freshEntity = fresh.toEntity()
        filmRollDao.upsert(freshEntity)
        return freshEntity
    }

    private fun generateRollId(): String = "roll_${UUID.randomUUID()}"

    private fun FilmRollEntity.toState(): FilmRollState = FilmRollState(
        rollId = rollId,
        cameraId = cameraId,
        capacity = capacity,
        shotsTaken = shotsTaken,
        createdAtEpochMs = createdAtEpochMs,
        completedAtEpochMs = completedAtEpochMs,
        isActive = isActive
    )

    private fun FilmRollState.toEntity(): FilmRollEntity = FilmRollEntity(
        rollId = rollId,
        cameraId = cameraId,
        capacity = capacity,
        shotsTaken = shotsTaken,
        createdAtEpochMs = createdAtEpochMs,
        completedAtEpochMs = completedAtEpochMs,
        isActive = isActive
    )
}
