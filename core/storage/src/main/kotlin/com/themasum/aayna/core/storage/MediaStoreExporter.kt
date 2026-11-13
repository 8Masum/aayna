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
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ExportMetadataFormatter {
    private val fileTimestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.US)

    private val exifDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)

    fun formatFileName(
        profile: CameraProfile,
        recipe: CaptureRecipe,
        suffix: String = "",
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val codeToken =
            profile.codeName
                .uppercase(Locale.US)
                .replace(Regex("[^A-Z0-9]+"), "")
                .ifBlank { "CAM" }
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
        return "AAYNA_${codeToken}_$timestamp$suffixPart.jpg"
    }

    fun formatExifDateTime(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String = Instant
        .ofEpochMilli(epochMs)
        .atZone(zoneId)
        .format(exifDateFormatter)
}

class MediaStoreExporter(private val context: Context) {
    suspend fun saveJpegToGallery(
        bitmap: Bitmap,
        profile: CameraProfile,
        recipe: CaptureRecipe,
        suffix: String = ""
    ): Uri? = withContext(Dispatchers.IO) {
        val fileName = ExportMetadataFormatter.formatFileName(profile, recipe, suffix)
        val tempFile = File.createTempFile("aayna_export_", ".jpg", context.cacheDir)
        try {
            FileOutputStream(tempFile).use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    "Bitmap JPEG compression failed"
                }
            }
            applyExifMetadata(tempFile, profile, recipe)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeMediaStoreApi29Plus(tempFile, fileName, recipe.capturedAtEpochMs)
            } else {
                writeLegacyPicturesApi26To28(tempFile, fileName)
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun applyExifMetadata(file: File, profile: CameraProfile, recipe: CaptureRecipe) {
        val exifDate = ExportMetadataFormatter.formatExifDateTime(recipe.capturedAtEpochMs)
        val exif = ExifInterface(file)
        exif.setAttribute(ExifInterface.TAG_MAKE, EXIF_MAKE)
        exif.setAttribute(ExifInterface.TAG_MODEL, profile.codeName)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exifDate)
        exif.setAttribute(ExifInterface.TAG_DATETIME, exifDate)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, EXIF_SOFTWARE)
        exif.saveAttributes()
    }

    private fun writeMediaStoreApi29Plus(sourceFile: File, fileName: String, capturedAtEpochMs: Long): Uri? {
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, JPEG_MIME_TYPE)
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
    private fun writeLegacyPicturesApi26To28(sourceFile: File, fileName: String): Uri {
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
            arrayOf(JPEG_MIME_TYPE),
            null
        )
        return Uri.fromFile(destination)
    }

    companion object {
        private const val ALBUM_DIR_NAME = "Aayna"
        private const val JPEG_MIME_TYPE = "image/jpeg"
        private const val JPEG_QUALITY = 95
        const val EXIF_MAKE = "AAYNA"
        const val EXIF_SOFTWARE = "Aayna v1.0"
    }
}
