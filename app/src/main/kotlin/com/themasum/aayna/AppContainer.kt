package com.themasum.aayna

import android.content.Context
import com.themasum.aayna.core.camera.AaynaCameraController
import com.themasum.aayna.core.gl.OffscreenGlExporter
import com.themasum.aayna.core.model.GlDiagnosticsInfo
import com.themasum.aayna.core.model.PassShaderStatus
import com.themasum.aayna.core.storage.AaynaLogger
import com.themasum.aayna.core.storage.AaynaPreferencesRepository
import com.themasum.aayna.core.storage.AaynaSoundEngine
import com.themasum.aayna.core.storage.FilmRollRepository
import com.themasum.aayna.core.storage.MediaStoreExporter
import com.themasum.aayna.core.storage.NegativeRepository
import com.themasum.aayna.core.storage.db.AaynaDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext

    val logger: AaynaLogger =
        AaynaLogger.getInstance(appContext).apply {
            installUncaughtExceptionHandler()
        }

    val database: AaynaDatabase = AaynaDatabase.getInstance(appContext)
    val preferencesRepository: AaynaPreferencesRepository = AaynaPreferencesRepository(appContext)
    val negativeRepository: NegativeRepository =
        NegativeRepository(
            context = appContext,
            negativeDao = database.negativeDao()
        )
    val filmRollRepository: FilmRollRepository = FilmRollRepository(database.filmRollDao())
    val mediaStoreExporter: MediaStoreExporter = MediaStoreExporter(appContext)
    val soundEngine: AaynaSoundEngine = AaynaSoundEngine(appContext)
    val cameraController: AaynaCameraController = AaynaCameraController(appContext)
    val offscreenExporter: OffscreenGlExporter = OffscreenGlExporter(appContext.assets)

    private val _glDiagnosticsFlow =
        MutableStateFlow<Pair<GlDiagnosticsInfo?, List<PassShaderStatus>>>(null to emptyList())
    val glDiagnosticsFlow: StateFlow<Pair<GlDiagnosticsInfo?, List<PassShaderStatus>>> =
        _glDiagnosticsFlow.asStateFlow()

    fun updateGlDiagnostics(info: GlDiagnosticsInfo?, statuses: List<PassShaderStatus>) {
        _glDiagnosticsFlow.value = info to statuses
    }

    fun release() {
        cameraController.release()
        soundEngine.release()
        offscreenExporter.close()
    }
}
