package com.themasum.aayna.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.themasum.aayna.AppContainer
import com.themasum.aayna.feature.gallery.GalleryStudioRoute
import com.themasum.aayna.feature.viewfinder.ViewfinderRoute
import com.themasum.aayna.ui.DiagnosticsScreen
import com.themasum.aayna.ui.SettingsScreen
import com.themasum.aayna.ui.SplashScreen
import com.themasum.aayna.ui.theme.AaynaFonts

object AaynaRoutes {
    const val SPLASH = "splash"
    const val VIEWFINDER = "viewfinder"
    const val GALLERY = "gallery"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
}

@Composable
fun AaynaNavGraph(
    container: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = AaynaRoutes.SPLASH,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
        modifier = modifier
    ) {
        composable(AaynaRoutes.SPLASH) {
            SplashScreen(
                onFinished = {
                    navController.navigate(AaynaRoutes.VIEWFINDER) {
                        popUpTo(AaynaRoutes.SPLASH) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(AaynaRoutes.VIEWFINDER) {
            ViewfinderRoute(
                onOpenGallery = {
                    navController.navigate(AaynaRoutes.GALLERY) {
                        launchSingleTop = true
                    }
                },
                onOpenSettings = {
                    navController.navigate(AaynaRoutes.SETTINGS) {
                        launchSingleTop = true
                    }
                },
                cameraController = container.cameraController,
                offscreenExporter = container.offscreenExporter,
                preferencesRepository = container.preferencesRepository,
                negativeRepository = container.negativeRepository,
                filmRollRepository = container.filmRollRepository,
                mediaStoreExporter = container.mediaStoreExporter,
                soundEngine = container.soundEngine,
                logger = container.logger,
                onGlDiagnosticsUpdated = { info, statuses ->
                    container.updateGlDiagnostics(info, statuses)
                },
                surfaceProcessor = container.surfaceProcessor,
                thumbnailCache = container.thumbnailMemoryCache,
                ledFontFamily = AaynaFonts.LedSevenSegment,
                dotMatrixFontFamily = AaynaFonts.DotMatrix,
                vcrOsdFontFamily = AaynaFonts.VcrOsd,
                pixelFontFamily = AaynaFonts.PixelLcd,
                condensedFontFamily = AaynaFonts.HardwareCondensed
            )
        }

        composable(AaynaRoutes.GALLERY) {
            GalleryStudioRoute(
                onBack = { navController.popBackStack() },
                offscreenExporter = container.offscreenExporter,
                preferencesRepository = container.preferencesRepository,
                negativeRepository = container.negativeRepository,
                filmRollRepository = container.filmRollRepository,
                mediaStoreExporter = container.mediaStoreExporter,
                soundEngine = container.soundEngine,
                logger = container.logger,
                studioGlRenderer = container.studioGlRenderer,
                thumbnailCache = container.thumbnailMemoryCache,
                ledFontFamily = AaynaFonts.LedSevenSegment,
                dotMatrixFontFamily = AaynaFonts.DotMatrix,
                vcrOsdFontFamily = AaynaFonts.VcrOsd,
                pixelFontFamily = AaynaFonts.PixelLcd,
                condensedFontFamily = AaynaFonts.HardwareCondensed,
                persistentGlEngine = container.persistentGlEngine
            )
        }

        composable(AaynaRoutes.SETTINGS) {
            SettingsScreen(
                preferencesRepository = container.preferencesRepository,
                soundEngine = container.soundEngine,
                onBack = { navController.popBackStack() },
                onOpenDiagnostics = {
                    navController.navigate(AaynaRoutes.DIAGNOSTICS) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(AaynaRoutes.DIAGNOSTICS) {
            DiagnosticsScreen(
                preferencesRepository = container.preferencesRepository,
                cameraController = container.cameraController,
                offscreenExporter = container.offscreenExporter,
                glDiagnosticsFlow = container.glDiagnosticsFlow,
                logger = container.logger,
                onBack = { navController.popBackStack() },
                surfaceProcessor = container.surfaceProcessor,
                studioGlRenderer = container.studioGlRenderer
            )
        }
    }
}
