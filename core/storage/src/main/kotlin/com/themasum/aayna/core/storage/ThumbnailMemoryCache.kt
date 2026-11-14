package com.themasum.aayna.core.storage

import android.graphics.Bitmap
import java.io.File

/**
 * Thread-safe, byte-bounded LRU cache for decoded gallery thumbnails, keyed by
 * `path@lastModifiedMs` so edited or re-saved thumbnails automatically miss stale entries.
 */
class ByteSizedLruCache<V : Any>(val maxSizeBytes: Int, private val sizeOfValue: (V) -> Int) {
    init {
        require(maxSizeBytes > 0) { "maxSizeBytes must be > 0, was $maxSizeBytes" }
    }

    private val lock = Any()
    private val map = LinkedHashMap<String, V>(INITIAL_CAPACITY, LOAD_FACTOR, true)
    private var currentSizeBytes: Int = 0

    fun sizeBytes(): Int = synchronized(lock) { currentSizeBytes }

    fun entryCount(): Int = synchronized(lock) { map.size }

    fun get(key: String): V? = synchronized(lock) { map[key] }

    fun put(key: String, value: V): V? {
        val valueSize = sizeOfValue(value).coerceAtLeast(1)
        synchronized(lock) {
            val previous = map.put(key, value)
            if (previous != null) {
                currentSizeBytes -= sizeOfValue(previous).coerceAtLeast(1)
            }
            currentSizeBytes += valueSize
            trimToSizeLocked(maxSizeBytes)
            return previous
        }
    }

    fun remove(key: String): V? = synchronized(lock) {
        val removed = map.remove(key)
        if (removed != null) {
            currentSizeBytes -= sizeOfValue(removed).coerceAtLeast(1)
        }
        removed
    }

    fun removeByPrefix(prefix: String): Int = synchronized(lock) {
        val keysToRemove = map.keys.filter { it.startsWith(prefix) }
        keysToRemove.forEach { key ->
            val removed = map.remove(key)
            if (removed != null) {
                currentSizeBytes -= sizeOfValue(removed).coerceAtLeast(1)
            }
        }
        keysToRemove.size
    }

    fun snapshotKeys(): List<String> = synchronized(lock) { map.keys.toList() }

    fun evictAll() {
        synchronized(lock) {
            map.clear()
            currentSizeBytes = 0
        }
    }

    private fun trimToSizeLocked(limitBytes: Int) {
        val iterator = map.entries.iterator()
        while (currentSizeBytes > limitBytes && iterator.hasNext()) {
            val oldest = iterator.next()
            currentSizeBytes -= sizeOfValue(oldest.value).coerceAtLeast(1)
            iterator.remove()
        }
    }

    private companion object {
        const val INITIAL_CAPACITY = 32
        const val LOAD_FACTOR = 0.75f
    }
}

class ThumbnailMemoryCache(maxMemoryBytes: Int = defaultMaxBytes()) {
    private val lru =
        ByteSizedLruCache<Bitmap>(maxMemoryBytes) { bitmap ->
            bitmap.allocationByteCount.coerceAtLeast(1)
        }

    val maxSizeBytes: Int
        get() = lru.maxSizeBytes

    fun sizeBytes(): Int = lru.sizeBytes()

    fun entryCount(): Int = lru.entryCount()

    fun get(path: String, lastModifiedMs: Long): Bitmap? = lru.get(cacheKey(path, lastModifiedMs))

    fun get(file: File): Bitmap? = get(file.absolutePath, file.lastModified())

    fun put(path: String, lastModifiedMs: Long, bitmap: Bitmap): Bitmap? {
        if (bitmap.isRecycled) return null
        return lru.put(cacheKey(path, lastModifiedMs), bitmap)
    }

    fun put(file: File, bitmap: Bitmap): Bitmap? = put(file.absolutePath, file.lastModified(), bitmap)

    fun invalidatePath(path: String): Int = lru.removeByPrefix("$path$KEY_SEPARATOR")

    fun evictAll() {
        lru.evictAll()
    }

    companion object {
        private const val KEY_SEPARATOR = "@"
        private const val DEFAULT_MIN_CACHE_BYTES = 8 * 1024 * 1024
        private const val DEFAULT_MAX_CACHE_BYTES = 32 * 1024 * 1024

        fun cacheKey(path: String, lastModifiedMs: Long): String = "$path$KEY_SEPARATOR$lastModifiedMs"

        fun defaultMaxBytes(): Int {
            val maxHeapBytes = Runtime.getRuntime().maxMemory().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            return (maxHeapBytes / 8).coerceIn(DEFAULT_MIN_CACHE_BYTES, DEFAULT_MAX_CACHE_BYTES)
        }
    }
}
