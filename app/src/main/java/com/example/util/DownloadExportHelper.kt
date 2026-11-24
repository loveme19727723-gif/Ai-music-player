package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.SongRecord
import java.io.File
import java.io.FileOutputStream

object DownloadExportHelper {

    fun saveBitmapAsCoverPng(context: Context, songId: String, bitmap: Bitmap): File {
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val safeId = songId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "cover_${System.currentTimeMillis()}" }
        val outFile = File(dir, "$safeId.png")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, fos)
        }
        return outFile
    }

    fun saveLyricsToFile(context: Context, song: SongRecord): File {
        val dir = File(context.filesDir, "lyrics").apply { mkdirs() }
        val safeId = song.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "lyrics_${System.currentTimeMillis()}" }
        val outFile = File(dir, "$safeId.txt")
        val content = buildString {
            appendLine(song.title)
            appendLine("${song.genre} | ${song.mood} | ${song.language} | ${song.bpm} BPM")
            appendLine("=".repeat(40))
            appendLine()
            appendLine(song.lyrics)
        }
        outFile.writeText(content)
        return outFile
    }

    fun exportToDeviceDownloads(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String,
        collectionUri: Uri? = null
    ): Result<String> {
        return try {
            require(sourceFile.exists()) { "File does not exist yet." }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val targetUri = collectionUri ?: MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val relativePath = when (targetUri) {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI -> "${Environment.DIRECTORY_MUSIC}/AIMusicWorld"
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI -> "${Environment.DIRECTORY_MOVIES}/AIMusicWorld"
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI -> "${Environment.DIRECTORY_PICTURES}/AIMusicWorld"
                    else -> "${Environment.DIRECTORY_DOWNLOADS}/AIMusicWorld"
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val itemUri = resolver.insert(targetUri, values)
                    ?: throw IllegalStateException("Could not create MediaStore entry.")
                resolver.openOutputStream(itemUri)?.use { out ->
                    sourceFile.inputStream().use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
                Result.success("Saved to $relativePath/$displayName")
            } else {
                val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.filesDir
                val dest = File(extDir, displayName)
                sourceFile.copyTo(dest, overwrite = true)
                Result.success("Saved to ${dest.absolutePath}")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share $title"))
    }

    fun shareSongText(context: Context, song: SongRecord) {
        val text = "${song.title} (${song.genre} • ${song.mood})\n\n${song.lyrics}\n\nCreated with AI Music World"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, song.title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share Song"))
    }

    fun copySongLink(context: Context, song: SongRecord) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val link = "https://aimusicworld.app/track/${song.id.ifBlank { song.title.hashCode() }}"
        clipboard.setPrimaryClip(ClipData.newPlainText("Song Link", link))
    }
}
