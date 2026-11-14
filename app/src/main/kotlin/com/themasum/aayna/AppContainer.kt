package com.themasum.aayna

import android.content.Context
import com.themasum.aayna.core.camera.AaynaCameraController
import com.themasum.aayna.core.gl.AaynaPersistentGlEngine
import com.themasum.aayna.core.gl.AaynaSurfaceProcessor
import com.themasum.aayna.core.gl.OffscreenGlExporter
import com.themasum.aayna.core.gl.StudioGlPreviewRenderer
import com.themasum.aayna.core.model.GlDiagnosticsInfo
import com.themasum.aayna.core.model.PassShaderStatus
import com.themasum.aayna.core.storage.AaynaLogger
import com.themasum.aayna.core.storage.AaynaPreferencesRepository
import com.themasum.aayna.core.storage.AaynaSoundEngine
import com.themasum.aayna.core.storage.FilmRollRepository
import com.themasum.aayna.core.storage.MediaStoreExporter
import com.themasum.aayna.core.storage.NegativeRepository
import com.themasum.aayna.core.storage.ThumbnailMemoryCache
import com.themasum.aayna.core.storage.db.AaynaDatabase
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext
    private val released = AtomicBoolean(false)

    val logger: AaynaLogger =
        AaynaLogger.getInstance(appContext).apply {
            installUncaughtExceptionHandler()
        }

    val database: AaynaDatabase = AaynaDatabase.getInstance(appContext)
    val preferencesRepository: AaynaPreferencesRepository = AaynaPreferencesRepository(appContext)
    val thumbnailMemoryCache: ThumbnailMemoryCache = ThumbnailMemoryCache()
    val negativeRepository: NegativeRepository =
        NegativeRepository(
            context = appContext,
            negativeDao = database.negativeDao(),
            thumbnailCache = thumbnailMemoryCache
        )
    val filmRollRepository: FilmRollRepository = FilmRollRepository(database.filmRollDao())
    val mediaStoreExporter: MediaStoreExporter = MediaStoreExporter(appContext)
    val soundEngine: AaynaSoundEngine = AaynaSoundEngine(appContext)
    val cameraController: AaynaCameraController = AaynaCameraController(appContext)

    val persistentGlEngine: AaynaPersistentGlEngine = AaynaPersistentGlEngine(appContext.assets)
    val surfaceProcessor: AaynaSurfaceProcessor = AaynaSurfaceProcessor(persistentGlEngine)
    val studioGlRenderer: StudioGlPreviewRenderer = StudioGlPreviewRenderer(persistentGlEngine)
    val offscreenExporter: OffscreenGlExporter = OffscreenGlExporter(persistentGlEngine)

    private val _glDiagnosticsFlow =
        MutableStateFlow<Pair<GlDiagnosticsInfo?, List<PassShaderStatus>>>(null to emptyList())
    val glDiagnosticsFlow: StateFlow<Pair<GlDiagnosticsInfo?, List<PassShaderStatus>>> =
        _glDiagnosticsFlow.asStateFlow()

    fun updateGlDiagnostics(info: GlDiagnosticsInfo?, statuses: List<PassShaderStatus>) {
        _glDiagnosticsFlow.value = info to statuses
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        cameraController.release()
        soundEngine.release()
        surfaceProcessor.release()
        studioGlRenderer.release()
        offscreenExporter.close()
        thumbnailMemoryCache.evictAll()
        persistentGlEngine.close()
    }
}
