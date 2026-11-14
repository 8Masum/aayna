package com.themasum.aayna

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @Test
    fun allFiveCustomTtfFontsLoadFromResources() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fontIds =
            listOf(
                R.font.aayna_led_seven_segment,
                R.font.aayna_dot_matrix,
                R.font.aayna_vcr_osd,
                R.font.aayna_pixel_lcd,
                R.font.aayna_hardware_condensed
            )

        fontIds.forEach { fontResId ->
            val typeface = ResourcesCompat.getFont(context, fontResId)
            assertNotNull("Expected font resource $fontResId to load cleanly", typeface)
        }
    }

    @Test
    fun manifestDeclaresZeroInternetPermissionAndIncludesCameraAndAudio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_PERMISSIONS
                )
            }
        val permissions = packageInfo.requestedPermissions?.toSet().orEmpty()

        assertFalse(
            "Aayna must remain strictly offline with zero INTERNET permission",
            permissions.contains(Manifest.permission.INTERNET)
        )
        assertTrue(
            "CAMERA permission must be declared",
            permissions.contains(Manifest.permission.CAMERA)
        )
        assertTrue(
            "RECORD_AUDIO permission must be declared for DCR video recording",
            permissions.contains(Manifest.permission.RECORD_AUDIO)
        )
    }

    @Test
    fun appContainerWiresSharedResourcesAndReleasesIdempotently() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = AppContainer(context)
        try {
            assertNotNull(container.persistentGlEngine)
            assertNotNull(container.surfaceProcessor)
            assertNotNull(container.studioGlRenderer)
            assertNotNull(container.thumbnailMemoryCache)
            assertNotNull(container.offscreenExporter)
        } finally {
            container.release()
            container.release()
        }
    }
}
