package com.themasum.aayna.feature.viewfinder

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.themasum.aayna.core.model.CameraCatalog
import com.themasum.aayna.core.model.SkinCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SkinAssetsVerificationTest {
    @Test
    fun skinManifestAndAllSkinAndIconAssetsExistAndDecode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = context.assets

        val rawManifest =
            assets.open(ParsedSkinManifest.MANIFEST_ASSET_PATH).bufferedReader().use { it.readText() }
        val manifest = SkinManifestParser.parse(rawManifest)

        assertEquals(24, manifest.skins.size)
        assertEquals(84, manifest.cameraIcons.size)

        assets.open(manifest.screenGlassSheenAssetPath).use { input ->
            val bmp = BitmapFactory.decodeStream(input)
            assertNotNull("Failed to decode ${manifest.screenGlassSheenAssetPath}", bmp)
            bmp?.recycle()
        }

        for (skin in SkinCatalog.all) {
            val entry = manifest.skinForId(skin.skinId)
            assertNotNull("Missing skin entry ${skin.skinId}", entry)
            val assetPaths =
                listOf(
                    entry!!.chassisAssetPath,
                    entry.bezelAssetPath,
                    entry.shutterAssetPath
                )
            for (path in assetPaths) {
                assertTrue("Blank asset path for ${skin.skinId}", path.isNotBlank())
                assets.open(path).use { input ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(input, null, bounds)
                    assertTrue("Invalid dimensions for $path", bounds.outWidth > 0 && bounds.outHeight > 0)
                }
            }
        }

        for (camera in CameraCatalog.all) {
            val iconPath = manifest.iconPathForCamera(camera.id)
            assets.open(iconPath).use { input ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, bounds)
                assertTrue("Invalid icon dimensions for $iconPath", bounds.outWidth > 0 && bounds.outHeight > 0)
            }
        }

        assets.open(CameraCatalog.CAROUSEL_BADGE_ICON_ASSET).use { input ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, bounds)
            assertTrue(bounds.outWidth > 0 && bounds.outHeight > 0)
        }
    }
}
