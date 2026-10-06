package com.zerogram.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore

object UriUtils {
    fun getPath(context: Context, uri: Uri): String? {
        val isTreeUri = uri.path?.startsWith("/tree/") == true
        if (isTreeUri) {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            if (docId.startsWith("raw:")) {
                return docId.substring(4)
            }
            val split = docId.split(":")
            val type = split[0]
            if ("primary".equals(type, ignoreCase = true)) {
                return Environment.getExternalStorageDirectory().toString() + "/" + split.getOrNull(1).orEmpty()
            } else {
                val fallbackPath = getDataColumn(context, uri, null, null)
                if (fallbackPath != null) return fallbackPath
                return "/storage/$type/" + split.getOrNull(1).orEmpty()
            }
        }
        
        if (DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory().toString() + "/" + split.getOrNull(1).orEmpty()
                } else {
                    return "/storage/$type/" + split.getOrNull(1).orEmpty()
                }
            } else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                if (id.startsWith("raw:")) {
                    return id.substring(4)
                }
                
                val contentUriPrefixesToTry = arrayOf(
                    "content://downloads/public_downloads",
                    "content://downloads/my_downloads",
                    "content://downloads/all_downloads"
                )
                
                for (prefix in contentUriPrefixesToTry) {
                    try {
                        val contentUri = ContentUris.withAppendedId(Uri.parse(prefix), id.toLong())
                        val path = getDataColumn(context, contentUri, null, null)
                        if (path != null) return path
                    } catch (e: Exception) {
                        // Ignore non-numeric IDs like msf:123
                    }
                }
                
                // Fallback for modern Android downloads
                val directPath = getDataColumn(context, uri, null, null)
                if (directPath != null) return directPath
                
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val type = split[0]
                var contentUri: Uri? = null
                when (type) {
                    "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    "document" -> contentUri = MediaStore.Files.getContentUri("external")
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(split.getOrNull(1) ?: "")
                if (contentUri != null) {
                    val path = getDataColumn(context, contentUri, selection, selectionArgs)
                    if (path != null) return path
                }
            }
            
            // Generic fallback for any Document URI
            val fallbackPath = getDataColumn(context, uri, null, null)
            if (fallbackPath != null) return fallbackPath
            
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            return getDataColumn(context, uri, null, null)
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return null
    }

    private fun getDataColumn(context: Context, uri: Uri, selection: String?, selectionArgs: Array<String>?): String? {
        var cursor: android.database.Cursor? = null
        val column = "_data"
        val projection = arrayOf(column)
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(index)
            }
        } catch (e: Exception) {
            // Ignore
        } finally {
            cursor?.close()
        }
        return null
    }

    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    fun copyUriToCache(context: Context, uri: Uri): java.io.File? {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            var fileName = "temp_file_${System.currentTimeMillis()}"
            
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                }
            }
            
            val cacheFile = java.io.File(context.cacheDir, fileName)
            java.io.FileOutputStream(cacheFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            return cacheFile
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
