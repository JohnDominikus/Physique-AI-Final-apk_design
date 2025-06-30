package com.example.physiqueaiapkfinal

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.physiqueaiapkfinal.visionutils.FrameMetadata
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.jvm.Throws

object BitmapUtils {

    @Throws(IOException::class)
    fun getBitmapFromContentUri(contentResolver: ContentResolver?, imageUri: Uri?): Bitmap? {
        return contentResolver?.let { contentResolver ->
            imageUri?.let { imageUri ->
                try {
                    // Ang bagong paraan para sa Android 14 (API 34) at mas bago
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        contentResolver.openInputStream(imageUri)?.use { inputStream ->
                            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                            val orientation = getExifOrientationTag(contentResolver, imageUri)
                            return applyOrientationToBitmap(bitmap, orientation)
                        }
                    } else {
                        // Ang dating paraan para sa mas lumang Android versions
                        val decodedBitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                        val orientation = getExifOrientationTag(contentResolver, imageUri)
                        return applyOrientationToBitmap(decodedBitmap, orientation)
                    }
                } catch (e: Exception) {
                    Log.e("BitmapUtils", "Error reading bitmap: ${e.message}")
                    null
                }
            }
        }
    }

    private fun applyOrientationToBitmap(bitmap: Bitmap?, orientation: Int): Bitmap? {
        if (bitmap == null) return null

        var rotationDegrees = 0
        var flipX = false
        var flipY = false

        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipX = true
            ExifInterface.ORIENTATION_ROTATE_90 -> rotationDegrees = 90
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                rotationDegrees = 90
                flipX = true
            }
            ExifInterface.ORIENTATION_ROTATE_180 -> rotationDegrees = 180
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipY = true
            ExifInterface.ORIENTATION_ROTATE_270 -> rotationDegrees = -90
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                rotationDegrees = -90
                flipX = true
            }
            ExifInterface.ORIENTATION_UNDEFINED, ExifInterface.ORIENTATION_NORMAL -> {}
            else -> {}
        }

        return rotateBitmap(bitmap, rotationDegrees, flipX, flipY)
    }

    private fun getExifOrientationTag(resolver: ContentResolver, imageUri: Uri): Int {
        if (ContentResolver.SCHEME_CONTENT != imageUri.scheme
            && ContentResolver.SCHEME_FILE != imageUri.scheme
        ) {
            return 0
        }
        var exif: ExifInterface
        try {
            resolver.openInputStream(imageUri)?.use { inputStream ->
                if (inputStream == null) {
                    return 0
                }
                exif = ExifInterface(inputStream)
                return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
            return 0
        } catch (e: IOException) {
            Log.e("TAG", "failed to open file to get exif orientation: $imageUri", e)
            return 0
        }
    }

    private fun rotateBitmap(
        bitmap: Bitmap, rotationDegrees: Int, flipX: Boolean, flipY: Boolean
    ): Bitmap? {
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        matrix.postScale(if (flipX) -1.0f else 1.0f, if (flipY) -1.0f else 1.0f)
        val rotatedBitmap =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }
        return rotatedBitmap
    }

    /** Converts ByteBuffer + metadata (NV21) to Bitmap */
    fun getBitmap(data: ByteBuffer, metadata: FrameMetadata): Bitmap {
        data.rewind()
        val yuvImage = android.graphics.YuvImage(
            data.array(),
            ImageFormat.NV21,
            metadata.width,
            metadata.height,
            null
        )
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, metadata.width, metadata.height), 100, out)
        val imageBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
}
