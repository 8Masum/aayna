package com.themasum.aayna.core.storage.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "film_rolls")
data class FilmRollEntity(
    @PrimaryKey
    val rollId: String,
    val cameraId: String,
    val capacity: Int,
    val shotsTaken: Int,
    val createdAtEpochMs: Long,
    val completedAtEpochMs: Long? = null,
    val isActive: Boolean
)
