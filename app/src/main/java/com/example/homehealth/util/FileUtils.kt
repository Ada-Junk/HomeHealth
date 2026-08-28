package com.example.homehealth.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/** 文件工具：图片压缩转 Base64、文件分享 */
object FileUtils {

    /** 压缩图片并转 Base64（用于上传解析服务）。按最长边降采样，防止大图解码 OOM。 */
    fun compressImageToBase64(file: File, maxDim: Int = 1600, quality: Int = 85): String {
        if (!file.exists()) throw IOException("图片文件不存在")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("图片解码失败")
        // 只要最长边仍超过 maxDim 就继续减半采样（4000×3000 → 1000×750，约 3MB 位图）
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDim) {
            sample *= 2
        }
        val bitmap = runCatching {
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        }.getOrNull() ?: throw IOException("图片解码失败")
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        bitmap.recycle()
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    /** 通过系统分享面板分享文件 */
    fun shareFile(context: Context, file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
