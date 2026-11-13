package com.themasum.aayna.core.gl

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLUtils
import com.themasum.aayna.core.model.FrameWindowMeta
import com.themasum.aayna.core.model.GrungeAtlasMeta
import com.themasum.aayna.core.model.StampAtlasMeta
import org.json.JSONObject

object AssetMetadataLoader {
    const val DEFAULT_STAMP_META_PATH = "stamps/stamp_atlas_meta.json"
    const val DEFAULT_GRUNGE_META_PATH = "grunge/grunge_meta.json"

    fun loadStampAtlasMeta(assetManager: AssetManager, assetPath: String = DEFAULT_STAMP_META_PATH): StampAtlasMeta {
        val raw = assetManager.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parseStampAtlasMeta(raw)
    }

    fun parseStampAtlasMeta(rawJson: String): StampAtlasMeta {
        val root = JSONObject(rawJson)
        val atlasesObj = root.optJSONObject("atlases")
        val atlases = mutableMapOf<String, String>()
        if (atlasesObj != null) {
            val keys = atlasesObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                atlases[key] = atlasesObj.getString(key)
            }
        }
        return StampAtlasMeta(
            atlasWidth = root.getInt("atlasWidth"),
            atlasHeight = root.getInt("atlasHeight"),
            columns = root.getInt("columns"),
            rows = root.getInt("rows"),
            cellWidth = root.getInt("cellWidth"),
            cellHeight = root.getInt("cellHeight"),
            glyphPadding = root.getInt("glyphPadding"),
            chars = root.getString("chars"),
            atlases = atlases
        )
    }

    fun loadGrungeAtlasMeta(assetManager: AssetManager, assetPath: String = DEFAULT_GRUNGE_META_PATH): GrungeAtlasMeta {
        val raw = assetManager.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parseGrungeAtlasMeta(raw)
    }

    fun parseGrungeAtlasMeta(rawJson: String): GrungeAtlasMeta {
        val root = JSONObject(rawJson)

        val textures = mutableMapOf<String, String>()
        val texturesObj = root.optJSONObject("textures")
        if (texturesObj != null) {
            val keys = texturesObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                textures[key] = texturesObj.getString(key)
            }
        }

        val frames = mutableMapOf<String, FrameWindowMeta>()
        val framesObj = root.optJSONObject("frames")
        if (framesObj != null) {
            val keys = framesObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val f = framesObj.getJSONObject(key)
                val windowArr = f.optJSONArray("windowPx")
                val windowPx =
                    if (windowArr != null) {
                        List(windowArr.length()) { idx -> windowArr.getInt(idx) }
                    } else {
                        emptyList()
                    }
                frames[key] =
                    FrameWindowMeta(
                        uMin = f.getDouble("uMin").toFloat(),
                        vMin = f.getDouble("vMin").toFloat(),
                        uMax = f.getDouble("uMax").toFloat(),
                        vMax = f.getDouble("vMax").toFloat(),
                        widthPx = f.getInt("widthPx"),
                        heightPx = f.getInt("heightPx"),
                        windowPx = windowPx
                    )
            }
        }

        return GrungeAtlasMeta(
            textures = textures,
            frames = frames
        )
    }

    fun decodeAssetBitmap(assetManager: AssetManager, assetPath: String): Bitmap {
        val options =
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inPremultiplied = false
            }
        return assetManager.open(assetPath).use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
                ?: throw IllegalStateException("Failed to decode asset bitmap: $assetPath")
        }
    }

    fun uploadTexture2D(bitmap: Bitmap, recycleBitmap: Boolean = false, clampToEdge: Boolean = true): Int {
        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        val textureId = textureIds[0]
        check(textureId != 0) { "Failed to generate GL_TEXTURE_2D handle" }

        val wrapMode = if (clampToEdge) GLES30.GL_CLAMP_TO_EDGE else GLES30.GL_REPEAT
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrapMode)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrapMode)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        if (recycleBitmap) {
            bitmap.recycle()
        }
        return textureId
    }
}
