package com.themasum.aayna.core.gl

import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

/**
 * Dedicated single-thread GL looper ("AaynaGLThread") backing persistent EGL14 contexts,
 * CameraX [androidx.camera.core.SurfaceProcessor] pipelines, and Photo Studio preview surfaces.
 */
class GLHandlerThread(name: String = DEFAULT_THREAD_NAME) : HandlerThread(name) {
    @Volatile
    private var handlerInternal: Handler? = null

    val handler: Handler
        get() {
            ensureStarted()
            return checkNotNull(handlerInternal) { "$name Handler not initialized" }
        }

    val executor: Executor = Executor { command ->
        post(command::run)
    }

    @Synchronized
    fun ensureStarted() {
        if (!isAlive) {
            start()
            handlerInternal = Handler(looper)
        } else if (handlerInternal == null) {
            handlerInternal = Handler(looper)
        }
    }

    fun isOnGlThread(): Boolean = Thread.currentThread() === this

    fun post(block: () -> Unit): Boolean {
        ensureStarted()
        return handler.post(block)
    }

    fun <T> runBlocking(block: () -> T): T {
        ensureStarted()
        if (isOnGlThread()) {
            return block()
        }
        val latch = CountDownLatch(1)
        val resultRef = AtomicReference<Result<T>>()
        val posted =
            handler.post {
                val result = runCatching(block)
                resultRef.set(result)
                latch.countDown()
            }
        check(posted) { "Failed to post task to $name" }
        latch.await()
        return checkNotNull(resultRef.get()).getOrThrow()
    }

    fun shutdownSafely() {
        if (isAlive) {
            quitSafely()
        }
        handlerInternal = null
    }

    companion object {
        const val DEFAULT_THREAD_NAME = "AaynaGLThread"
    }
}
