package com.example.geonapominalka.util
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File

/**
 * NotificationChannel.setSound() принимает только content:// Uri, читаемый системным
 * процессом. Некоторые прошивки (например MIUI) при выборе "Добавить" в системном пикере
 * рингтонов возвращают file:// путь напрямую — система такой Uri прочитать не может и
 * молча подставляет звук по умолчанию, без единой ошибки в логах. Чтобы кастомный файл
 * реально заработал как звук уведомления, его нужно зарегистрировать в MediaStore —
 * тогда он становится обычным "публичным" медиа-объектом с валидным content:// Uri.
 */
object NotificationSoundImporter {
    /** content:// Uri возвращает как есть. file:// — копирует файл в MediaStore и
     *  возвращает новый content:// Uri. null при ошибке копирования. */
    fun toPlayableUri(context: Context, uri: Uri): Uri? {
        if (uri.scheme != "file") return uri
        val file = File(uri.path ?: return null)
        if (!file.exists()) return null
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "audio/*"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_NOTIFICATIONS)
            } else {
                @Suppress("DEPRECATION")
                put(
                    MediaStore.Audio.Media.DATA,
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_NOTIFICATIONS), file.name).absolutePath
                )
            }
        }
        val resolver = context.contentResolver
        return try {
            val newUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(newUri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: return null.also { resolver.delete(newUri, null, null) }
            newUri
        } catch (e: Exception) {
            null
        }
    }
}
