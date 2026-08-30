package com.jaysay.coursetable.data.preferences

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 将用户授权的单张背景图重编码到应用私有目录。
 * 重编码会去除 EXIF/位置等元数据，并限制尺寸以控制内存与磁盘占用。
 */
object CustomBackgroundStore {
    private const val DIRECTORY = "appearance"
    private const val FILE_NAME = "custom_background.jpg"
    private const val TEMP_FILE_NAME = "custom_background.tmp"
    private const val BACKUP_FILE_NAME = "custom_background.jpg.bak"
    internal const val MAX_DIMENSION = 2160

    fun backgroundFile(context: Context): File = File(File(context.filesDir, DIRECTORY), FILE_NAME)

    suspend fun import(context: Context, uri: Uri): Long = withContext(Dispatchers.IO) {
        val bitmap = decodeBounded(context, uri)
            ?: throw IllegalArgumentException("无法读取该图片，请选择 JPG、PNG 或 WebP 图片")
        try {
            val target = backgroundFile(context)
            val directory = target.parentFile ?: error("背景目录无效")
            if (!directory.exists() && !directory.mkdirs()) error("无法创建背景目录")
            val temporary = File(directory, TEMP_FILE_NAME)
            FileOutputStream(temporary, false).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)) { "背景图保存失败" }
                stream.fd.sync()
            }
            val backup = File(directory, BACKUP_FILE_NAME)
            backup.delete()
            if (target.exists() && !target.renameTo(backup)) {
                temporary.delete()
                error("无法保护旧背景图")
            }
            runCatching {
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
            }.onFailure {
                target.delete()
                if (backup.exists()) backup.renameTo(target)
            }.getOrThrow()
            // 清理失败不影响新背景；下次导入或清除时会再尝试。
            backup.delete()
            max(System.currentTimeMillis(), target.lastModified()).coerceAtLeast(1L)
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        val target = backgroundFile(context)
        if (target.exists() && !target.delete()) error("自定义背景删除失败")
        File(target.parentFile, TEMP_FILE_NAME).delete()
        File(target.parentFile, BACKUP_FILE_NAME).delete()
    }

    fun decodeStored(context: Context): Bitmap? {
        val file = backgroundFile(context)
        if (!file.isFile) return null
        // 读取端按显示上限降采样：存储端允许到 2160px，但显示 2400px 内足够，
        // 避免 2160×3840 全尺寸位图在低端机常驻 30MB+ 内存。
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, DECODE_TARGET_DIMENSION)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    /** 显示用途的解码上限（calculateInSampleSize 为 2 倍语义，最终边长不超过其 2 倍）。 */
    private const val DECODE_TARGET_DIMENSION = 1200

    internal fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int = MAX_DIMENSION): Int {
        if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
        var sample = 1
        while (max(width / sample, height / sample) > maxDimension * 2) sample *= 2
        return sample
    }

    internal fun scaledSize(width: Int, height: Int, maxDimension: Int = MAX_DIMENSION): Pair<Int, Int> {
        require(width > 0 && height > 0 && maxDimension > 0)
        val largest = max(width, height)
        if (largest <= maxDimension) return width to height
        val scale = maxDimension.toFloat() / largest
        return (width * scale).roundToInt().coerceAtLeast(1) to
            (height * scale).roundToInt().coerceAtLeast(1)
    }

    private fun decodeBounded(context: Context, uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val (width, height) = scaledSize(info.size.width, info.size.height)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize(width, height)
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null
            val (targetWidth, targetHeight) = scaledSize(decoded.width, decoded.height)
            val scaled = if (targetWidth == decoded.width && targetHeight == decoded.height) decoded else {
                decoded.scale(targetWidth, targetHeight, true).also { decoded.recycle() }
            }
            applyLegacyExifOrientation(scaled, readExifOrientation(context, uri))
        }
    }

    /** BitmapFactory 在 Android 8.x 不会应用 EXIF 方向，手动校正后再重编码。 */
    private fun readExifOrientation(context: Context, uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun applyLegacyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        if (orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED
        ) return bitmap
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    setRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    setRotate(-90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrElse { return bitmap }.also { transformed ->
            if (transformed !== bitmap) bitmap.recycle()
        }
    }
}
