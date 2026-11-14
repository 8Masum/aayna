package com.themasum.aayna.core.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.themasum.aayna.core.model.CameraProfile
import com.themasum.aayna.core.model.CaptureRecipe
import com.themasum.aayna.core.model.UserSettingsSnapshot
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaStoreVideoOutput(
    val collectionUri: Uri,
    val contentValues: ContentValues,
    val displayName: String,
    val relativePath: String,
    val legacyFile: File?
)

object ExportMetadataFormatter {
    private val fileTimestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.US)

    private val exifDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)

    fun formatFileName(
        profile: CameraProfile,
        recipe: CaptureRecipe,
        suffix: String = "",
        zoneId: ZoneId = ZoneId.systemDefault(),
        extension: String = "jpg"
    ): String {
        val codeToken = sanitizeCodeToken(profile.codeName)
        val timestamp =
            Instant
                .ofEpochMilli(recipe.capturedAtEpochMs)
                .atZone(zoneId)
                .format(fileTimestampFormatter)
        val cleanSuffix =
            suffix
                .trim()
                .replace(Regex("[^A-Za-z0-9_-]+"), "_")
                .trim('_')
        val suffixPart = if (cleanSuffix.isEmpty()) "" else "_$cleanSuffix"
        val ext = extension.trimStart('.').lowercase(Locale.US).ifBlank { "jpg" }
        return "AAYNA_${codeToken}_$timestamp$suffixPart.$ext"
    }

    fun formatVideoFileName(
        profile: CameraProfile,
        capturedAtMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val codeToken = sanitizeCodeToken(profile.codeName)
        val timestamp =
            Instant
                .ofEpochMilli(capturedAtMs)
                .atZone(zoneId)
                .format(fileTimestampFormatter)
        return "AAYNA_${codeToken}_$timestamp.mp4"
    }

    fun formatExifDateTime(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String = Instant
        .ofEpochMilli(epochMs)
        .atZone(zoneId)
        .format(exifDateFormatter)

    fun formatMakerNote(profile: CameraProfile, recipe: CaptureRecipe): String =
        "AAYNA;cam=${profile.id};media=${recipe.mediaType.name};skin=${recipe.skinId.ifBlank { profile.defaultSkinId }}"

    private fun sanitizeCodeToken(codeName: String): String = codeName
        .uppercase(Locale.US)
        .replace(Regex("[^A-Z0-9]+"), "")
        .ifBlank { "CAM" }
}

class MediaStoreExporter(private val context: Context) {
    suspend fun saveJpegToGallery(
        bitmap: Bitmap,
        profile: CameraProfile,
        recipe: CaptureRecipe,
        suffix: String = "",
        jpegQuality: Int = UserSettingsSnapshot.DEFAULT_JPEG_QUALITY
    ): Uri? = withContext(Dispatchers.IO) {
        val clampedQuality =
            jpegQuality.coerceIn(
                UserSettingsSnapshot.MIN_JPEG_QUALITY,
                UserSettingsSnapshot.MAX_JPEG_QUALITY
            )
        val fileName = ExportMetadataFormatter.formatFileName(profile, recipe, suffix, extension = "jpg")
        val tempFile = File.createTempFile("aayna_export_", ".jpg", context.cacheDir)
        try {
            FileOutputStream(tempFile).use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, clampedQuality, out)) {
                    "Bitmap JPEG compression failed"
                }
            }
            applyExifMetadata(tempFile, profile, recipe)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeImageMediaStoreApi29Plus(
                    sourceFile = tempFile,
                    fileName = fileName,
                    mimeType = JPEG_MIME_TYPE,
                    capturedAtEpochMs = recipe.capturedAtEpochMs
                )
            } else {
                writeLegacyPicturesApi26To28(
                    sourceFile = tempFile,
                    fileName = fileName,
                    mimeType = JPEG_MIME_TYPE
                )
            }
        } finally {
            tempFile.delete()
        }
    }

    suspend fun saveD3dGifToGallery(
        frames: List<Bitmap>,
        profile: CameraProfile,
        recipe: CaptureRecipe,
        frameDelayMs: Int = GifWigglegramEncoder.DEFAULT_FRAME_DELAY_MS,
        suffix: String = "d3d",
        pingPong: Boolean = true
    ): Uri? = withContext(Dispatchers.IO) {
        require(frames.isNotEmpty()) { "At least one frame is required to export a D3D GIF" }
        val fileName = ExportMetadataFormatter.formatFileName(profile, recipe, suffix, extension = "gif")
        val gifBytes =
            GifWigglegramEncoder.encodeBitmaps(
                bitmaps = frames,
                frameDelayMs = frameDelayMs,
                loopCount = GifWigglegramEncoder.DEFAULT_LOOP_COUNT,
                pingPong = pingPong
            )
        val tempFile = File.createTempFile("aayna_d3d_", ".gif", context.cacheDir)
        try {
            FileOutputStream(tempFile).use { out ->
                out.write(gifBytes)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeImageMediaStoreApi29Plus(
                    sourceFile = tempFile,
                    fileName = fileName,
                    mimeType = GIF_MIME_TYPE,
                    capturedAtEpochMs = recipe.capturedAtEpochMs
                )
            } else {
                writeLegacyPicturesApi26To28(
                    sourceFile = tempFile,
                    fileName = fileName,
                    mimeType = GIF_MIME_TYPE
                )
            }
        } finally {
            tempFile.delete()
        }
    }

    fun createVideoMediaStoreOutput(
        profile: CameraProfile,
        capturedAtMs: Long = System.currentTimeMillis()
    ): MediaStoreVideoOutput = createVideoMediaStoreOutput(context, profile, capturedAtMs)

    fun scanLegacyVideoFile(file: File) {
        MediaScannerConnection.scanFile(
            context.applicationContext,
            arrayOf(file.absolutePath),
            arrayOf(MP4_MIME_TYPE),
            null
        )
    }

    private fun applyExifMetadata(file: File, profile: CameraProfile, recipe: CaptureRecipe) {
        val exifDate = ExportMetadataFormatter.formatExifDateTime(recipe.capturedAtEpochMs)
        val exif = ExifInterface(file)
        exif.setAttribute(ExifInterface.TAG_MAKE, EXIF_MAKE)
        exif.setAttribute(ExifInterface.TAG_MODEL, profile.codeName)
        exif.setAttribute(ExifInterface.TAG_MAKER_NOTE, ExportMetadataFormatter.formatMakerNote(profile, recipe))
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exifDate)
        exif.setAttribute(ExifInterface.TAG_DATETIME, exifDate)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, EXIF_SOFTWARE)
        exif.saveAttributes()
    }

    private fun writeImageMediaStoreApi29Plus(
        sourceFile: File,
        fileName: String,
        mimeType: String,
        capturedAtEpochMs: Long
    ): Uri? {
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM_DIR_NAME")
                put(MediaStore.Images.Media.DATE_TAKEN, capturedAtEpochMs)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            } ?: error("Unable to open output stream for $uri")

            val publishValues =
                ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
            resolver.update(uri, publishValues, null, null)
            uri
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }

    @Suppress("DEPRECATION")
    private fun writeLegacyPicturesApi26To28(sourceFile: File, fileName: String, mimeType: String): Uri {
        val picturesRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val albumDir = File(picturesRoot, ALBUM_DIR_NAME).apply { mkdirs() }
        val destination = File(albumDir, fileName)
        sourceFile.inputStream().use { input ->
            FileOutputStream(destination).use { out ->
                input.copyTo(out)
            }
        }
        MediaScannerConnection.scanFile(
            context.applicationContext,
            arrayOf(destination.absolutePath),
            arrayOf(mimeType),
            null
        )
        return Uri.fromFile(destination)
    }

    companion object {
        private const val ALBUM_DIR_NAME = "Aayna"
        private const val JPEG_MIME_TYPE = "image/jpeg"
        private const val GIF_MIME_TYPE = "image/gif"
        private const val MP4_MIME_TYPE = "video/mp4"
        const val EXIF_MAKE = "AAYNA"
        const val EXIF_SOFTWARE = "Aayna v1.0"

        @Suppress("DEPRECATION")
        fun createVideoMediaStoreOutput(
            context: Context,
            profile: CameraProfile,
            capturedAtMs: Long = System.currentTimeMillis()
        ): MediaStoreVideoOutput {
            val fileName = ExportMetadataFormatter.formatVideoFileName(profile, capturedAtMs)
            val relativePath = "${Environment.DIRECTORY_MOVIES}/$ALBUM_DIR_NAME"
            val values =
                ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, MP4_MIME_TYPE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                        put(MediaStore.Video.Media.DATE_TAKEN, capturedAtMs)
                    }
                }
            val legacyFile =
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    val moviesRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                    File(File(moviesRoot, ALBUM_DIR_NAME).apply { mkdirs() }, fileName)
                } else {
                    null
                }
            return MediaStoreVideoOutput(
                collectionUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                contentValues = values,
                displayName = fileName,
                relativePath = relativePath,
                legacyFile = legacyFile
            )
        }
    }
}
