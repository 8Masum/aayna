package com.themasum.aayna.core.storage

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class AaynaLogger(
    private val logFile: File? = null,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    private val maxRingEntries: Int = DEFAULT_MAX_RING_ENTRIES,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    private val lock = Any()
    private val ringBuffer = ArrayDeque<String>()

    fun logInfo(tag: String, message: String) {
        append(level = "I", tag = tag, message = message, throwable = null)
    }

    fun logWarn(tag: String, message: String, throwable: Throwable? = null) {
        append(level = "W", tag = tag, message = message, throwable = throwable)
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        append(level = "E", tag = tag, message = message, throwable = throwable)
    }

    fun readAllLogs(): String = synchronized(lock) {
        val file = logFile
        if (file != null && file.exists()) {
            runCatching { file.readText(Charsets.UTF_8) }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: ringBuffer.joinToString(separator = "\n")
        } else {
            ringBuffer.joinToString(separator = "\n")
        }
    }

    fun clear() {
        synchronized(lock) {
            ringBuffer.clear()
            logFile?.let { file ->
                if (file.exists()) {
                    file.writeText("", Charsets.UTF_8)
                }
            }
        }
    }

    fun installUncaughtExceptionHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logError(
                tag = "UncaughtException",
                message = "Fatal crash on thread ${thread.name}",
                throwable = throwable
            )
            previous?.uncaughtException(thread, throwable)
        }
    }

    internal fun formatLine(
        level: String,
        tag: String,
        message: String,
        timestampMs: Long = System.currentTimeMillis(),
        throwable: Throwable? = null
    ): String {
        val ts =
            Instant
                .ofEpochMilli(timestampMs)
                .atZone(zoneId)
                .format(timestampFormatter)
        val base = "$ts [$level/$tag] $message"
        if (throwable == null) {
            return base
        }
        val stackWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stackWriter))
        return "$base\n${stackWriter.toString().trimEnd()}"
    }

    private fun append(level: String, tag: String, message: String, throwable: Throwable?) {
        val entry = formatLine(level = level, tag = tag, message = message, throwable = throwable)
        synchronized(lock) {
            if (ringBuffer.size >= maxRingEntries) {
                ringBuffer.removeFirst()
            }
            ringBuffer.addLast(entry)
            writeToFileLocked(entry)
        }
    }

    private fun writeToFileLocked(entry: String) {
        val file = logFile ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val payload = "$entry\n"
            val payloadBytes = payload.toByteArray(Charsets.UTF_8)
            val existingSize = if (file.exists()) file.length() else 0L
            if (existingSize + payloadBytes.size > maxFileBytes) {
                trimFileLocked(file, payloadBytes)
            } else {
                file.appendText(payload, Charsets.UTF_8)
            }
        }
    }

    private fun trimFileLocked(file: File, incomingBytes: ByteArray) {
        val maxInt = maxFileBytes.coerceAtLeast(1L).toInt()
        if (incomingBytes.size >= maxInt) {
            val sliced = incomingBytes.copyOfRange(incomingBytes.size - maxInt, incomingBytes.size)
            file.writeBytes(sliced)
            return
        }
        val existingBytes = if (file.exists()) file.readBytes() else ByteArray(0)
        val targetKeep = (maxInt - incomingBytes.size).coerceAtLeast(0)
        val keptStart = (existingBytes.size - targetKeep).coerceAtLeast(0)
        val trimmed =
            if (keptStart < existingBytes.size) {
                // Advance to the next newline so we don't leave a half-line at the top
                var newlineIdx = keptStart
                while (newlineIdx < existingBytes.size && existingBytes[newlineIdx] != '\n'.code.toByte()) {
                    newlineIdx++
                }
                val cleanStart =
                    if (newlineIdx < existingBytes.size) newlineIdx + 1 else keptStart
                existingBytes.copyOfRange(cleanStart, existingBytes.size)
            } else {
                ByteArray(0)
            }
        file.writeBytes(trimmed + incomingBytes)
    }

    companion object {
        const val DEFAULT_MAX_FILE_BYTES: Long = 256L * 1024L
        const val DEFAULT_MAX_RING_ENTRIES: Int = 400
        private const val LOGS_DIR = "logs"
        private const val LOG_FILE_NAME = "aayna.log"

        private val timestampFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

        @Volatile
        private var sharedInstance: AaynaLogger? = null

        fun getInstance(context: Context): AaynaLogger = sharedInstance ?: synchronized(this) {
            sharedInstance ?: AaynaLogger(
                logFile = File(File(context.applicationContext.filesDir, LOGS_DIR), LOG_FILE_NAME)
            ).also { logger ->
                sharedInstance = logger
            }
        }
    }
}
