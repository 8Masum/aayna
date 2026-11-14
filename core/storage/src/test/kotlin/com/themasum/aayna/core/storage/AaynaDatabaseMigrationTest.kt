package com.themasum.aayna.core.storage

import com.themasum.aayna.core.model.MediaType
import com.themasum.aayna.core.storage.db.AaynaDatabase
import com.themasum.aayna.core.storage.db.NegativeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AaynaDatabaseMigrationTest {
    @Test
    fun `database version is 2 and migration covers 1 to 2`() {
        assertEquals(2, AaynaDatabase.DATABASE_VERSION)
        assertEquals(1, AaynaDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AaynaDatabase.MIGRATION_1_2.endVersion)
    }

    @Test
    fun `migration 1 to 2 alters negatives with non-destructive defaults`() {
        val statements = AaynaDatabase.MIGRATION_1_2_STATEMENTS
        assertEquals(7, statements.size)
        assertTrue(statements.contains("ALTER TABLE negatives ADD COLUMN mediaType TEXT NOT NULL DEFAULT 'STILL'"))
        assertTrue(statements.contains("ALTER TABLE negatives ADD COLUMN skinId TEXT NOT NULL DEFAULT ''"))
        assertTrue(statements.contains("ALTER TABLE negatives ADD COLUMN videoDurationMs INTEGER NOT NULL DEFAULT 0"))
        assertTrue(statements.contains("ALTER TABLE negatives ADD COLUMN burstFrameCount INTEGER NOT NULL DEFAULT 1"))
        assertTrue(statements.contains("ALTER TABLE negatives ADD COLUMN mediaUri TEXT"))
        assertTrue(statements.contains("ALTER TABLE negatives ADD COLUMN isTrashed INTEGER NOT NULL DEFAULT 0"))
        assertTrue(statements.contains("ALTER TABLE negatives ADD COLUMN trashedAtEpochMs INTEGER"))
    }

    @Test
    fun `negative entity defaults match migration 1 to 2 defaults`() {
        val entity =
            NegativeEntity(
                recipeId = "rec-1",
                cameraId = "z30",
                capturedAtEpochMs = 1_700_000_000_000L,
                cleanImagePath = "/tmp/clean.jpg",
                thumbnailPath = "/tmp/thumb.jpg",
                recipeJson = "{}"
            )

        assertEquals(MediaType.STILL.name, entity.mediaType)
        assertEquals(MediaType.STILL, entity.resolvedMediaType())
        assertEquals("", entity.skinId)
        assertEquals(0L, entity.videoDurationMs)
        assertEquals(1, entity.burstFrameCount)
        assertNull(entity.mediaUri)
        assertFalse(entity.isTrashed)
        assertNull(entity.trashedAtEpochMs)

        val fallback = entity.copy(mediaType = "UNKNOWN_FUTURE_TYPE")
        assertEquals(MediaType.STILL, fallback.resolvedMediaType())
    }
}
