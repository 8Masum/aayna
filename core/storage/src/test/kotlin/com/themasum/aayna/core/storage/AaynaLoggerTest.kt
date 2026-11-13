package com.themasum.aayna.core.storage

import java.io.File
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AaynaLoggerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `formatLine formats timestamp level tag and optional throwable`() {
        val logger = AaynaLogger(zoneId = ZoneOffset.UTC)
        val line =
            logger.formatLine(
                level = "I",
                tag = "CameraEngine",
                message = "Pipeline ready",
                timestampMs = 1_700_000_000_123L
            )

        assertEquals("2023-11-14 22:13:20.123 [I/CameraEngine] Pipeline ready", line)

        val errorLine =
            logger.formatLine(
                level = "E",
                tag = "GlRenderer",
                message = "Shader failed",
                timestampMs = 1_700_000_000_123L,
                throwable = IllegalStateException("compile error")
            )
        assertTrue(errorLine.startsWith("2023-11-14 22:13:20.123 [E/GlRenderer] Shader failed"))
        assertTrue(errorLine.contains("IllegalStateException: compile error"))
    }

    @Test
    fun `ring buffer evicts oldest entries beyond maxRingEntries`() {
        val logger = AaynaLogger(logFile = null, maxRingEntries = 3, zoneId = ZoneOffset.UTC)
        logger.logInfo("Test", "one")
        logger.logWarn("Test", "two")
        logger.logError("Test", "three")
        logger.logInfo("Test", "four")

        val logs = logger.readAllLogs()
        assertFalse(logs.contains("one"))
        assertTrue(logs.contains("two"))
        assertTrue(logs.contains("three"))
        assertTrue(logs.contains("four"))
    }

    @Test
    fun `file logger caps log file size and preserves recent entries`() {
        val logFile = File(tempFolder.root, "logs/aayna.log")
        val maxBytes = 512L
        val logger =
            AaynaLogger(
                logFile = logFile,
                maxFileBytes = maxBytes,
                maxRingEntries = 50,
                zoneId = ZoneOffset.UTC
            )

        repeat(40) { index ->
            logger.logInfo("CapTest", "entry-$index-abcdefghijklmnopqrstuvwxyz")
        }

        assertTrue(logFile.exists())
        assertTrue(
            "Expected logFile.length() <= $maxBytes, actual=${logFile.length()}",
            logFile.length() <= maxBytes
        )
        val content = logger.readAllLogs()
        assertTrue(content.contains("entry-39-"))
        assertFalse(content.contains("entry-0-"))
    }
}
