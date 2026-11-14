package com.themasum.aayna.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ThumbnailMemoryCacheTest {
    @Test
    fun `cacheKey combines path and lastModifiedMs`() {
        val key1 = ThumbnailMemoryCache.cacheKey("/data/thumbs/a_thumb.jpg", 1_000L)
        val key2 = ThumbnailMemoryCache.cacheKey("/data/thumbs/a_thumb.jpg", 2_000L)
        assertEquals("/data/thumbs/a_thumb.jpg@1000", key1)
        assertNotEquals(key1, key2)
    }

    @Test
    fun `byte sized LRU evicts least recently used entry when exceeding capacity`() {
        val cache = ByteSizedLruCache<ByteArray>(maxSizeBytes = 20) { it.size }
        val keyA = ThumbnailMemoryCache.cacheKey("/thumbs/a.jpg", 1L)
        val keyB = ThumbnailMemoryCache.cacheKey("/thumbs/b.jpg", 1L)
        val keyC = ThumbnailMemoryCache.cacheKey("/thumbs/c.jpg", 1L)

        cache.put(keyA, ByteArray(8))
        cache.put(keyB, ByteArray(8))
        // Touch keyA so keyB becomes least recently used.
        assertNotNull(cache.get(keyA))

        cache.put(keyC, ByteArray(8))

        assertNotNull(cache.get(keyA))
        assertNull(cache.get(keyB))
        assertNotNull(cache.get(keyC))
        assertEquals(16, cache.sizeBytes())
    }

    @Test
    fun `removeByPrefix invalidates all timestamp versions for a file path`() {
        val cache = ByteSizedLruCache<ByteArray>(maxSizeBytes = 100) { it.size }
        val path = "/thumbs/rec1_thumb.jpg"
        cache.put(ThumbnailMemoryCache.cacheKey(path, 100L), ByteArray(10))
        cache.put(ThumbnailMemoryCache.cacheKey(path, 200L), ByteArray(10))
        cache.put(ThumbnailMemoryCache.cacheKey("/thumbs/rec2_thumb.jpg", 100L), ByteArray(10))

        val removed = cache.removeByPrefix("$path@")
        assertEquals(2, removed)
        assertEquals(1, cache.entryCount())
        assertEquals(10, cache.sizeBytes())
    }
}
