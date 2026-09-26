package com.zerogram.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext
import android.webkit.MimeTypeMap

data class ScannedFolder(
    val tempId: String,
    val name: String,
    val parentTempId: String?,
    val relativePath: String
)

data class ScannedFile(
    val tempId: String,
    val name: String,
    val parentTempId: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val path: String,
    val relativePath: String
)

data class SkippedItem(
    val name: String,
    val relativePath: String,
    val reason: String
)

data class ScanOptions(
    val uploadHiddenFolders: Boolean = false,
    val uploadHiddenFiles: Boolean = false
)

data class ScanResult(
    val folders: List<ScannedFolder>,
    val files: List<ScannedFile>,
    val skippedItems: List<SkippedItem>,
    val totalSize: Long,
    val emptyFolders: Int
)

class FolderScanner {
    companion object {
        const val SAFE_SCAN_LIMIT = 2_097_119_960L // ~2GB
    }

    suspend fun scanPath(
        rootPath: String,
        options: ScanOptions,
        onProgress: (foldersScanned: Int, filesFound: Int, emptyFolders: Int, totalSize: Long) -> Unit
    ): ScanResult = withContext(Dispatchers.IO) {
        val rootFile = File(rootPath)
        if (!rootFile.exists() || !rootFile.isDirectory) {
            throw IllegalArgumentException("Invalid or inaccessible folder path.")
        }

        val folders = mutableListOf<ScannedFolder>()
        val files = mutableListOf<ScannedFile>()
        val skippedItems = mutableListOf<SkippedItem>()

        var foldersScanned = 0
        var filesFound = 0
        var emptyFolders = 0
        var totalSize = 0L

        var lastProgressTime = 0L

        val rootTempId = UUID.randomUUID().toString()
        val rootFolderName = rootFile.name

        folders.add(
            ScannedFolder(
                tempId = rootTempId,
                name = rootFolderName,
                parentTempId = null,
                relativePath = rootFolderName
            )
        )
        foldersScanned++

        scanDirectory(
            dir = rootFile,
            parentTempId = rootTempId,
            currentRelativePath = rootFolderName,
            options = options,
            folders = folders,
            files = files,
            skippedItems = skippedItems,
            onStatsUpdate = { isFolder, isEmptyFolder, fileSizeBytes ->
                if (isFolder) foldersScanned++
                if (isEmptyFolder) emptyFolders++
                if (fileSizeBytes > 0) {
                    filesFound++
                    totalSize += fileSizeBytes
                }

                val now = System.currentTimeMillis()
                if (now - lastProgressTime > 100) {
                    lastProgressTime = now
                    onProgress(foldersScanned, filesFound, emptyFolders, totalSize)
                }
            }
        )
        
        onProgress(foldersScanned, filesFound, emptyFolders, totalSize)

        ScanResult(
            folders = folders,
            files = files,
            skippedItems = skippedItems,
            totalSize = totalSize,
            emptyFolders = emptyFolders
        )
    }

    private suspend fun scanDirectory(
        dir: File,
        parentTempId: String,
        currentRelativePath: String,
        options: ScanOptions,
        folders: MutableList<ScannedFolder>,
        files: MutableList<ScannedFile>,
        skippedItems: MutableList<SkippedItem>,
        onStatsUpdate: (isFolder: Boolean, isEmptyFolder: Boolean, fileSizeBytes: Long) -> Unit
    ) {
        if (!coroutineContext.isActive) {
            throw CancellationException("Scan cancelled")
        }

        val children = dir.listFiles()
        if (children == null) {
            skippedItems.add(
                SkippedItem(
                    name = dir.name,
                    relativePath = currentRelativePath,
                    reason = "Inaccessible (Permission Denied)"
                )
            )
            return
        }

        if (children.isEmpty()) {
            onStatsUpdate(false, true, 0L)
            return
        }

        for (child in children) {
            if (!coroutineContext.isActive) {
                throw CancellationException("Scan cancelled")
            }

            val childName = child.name
            val isHidden = childName.startsWith(".")
            val childRelativePath = "$currentRelativePath/$childName"

            if (child.isDirectory) {
                if (isHidden && !options.uploadHiddenFolders) continue

                val childTempId = UUID.randomUUID().toString()
                folders.add(
                    ScannedFolder(
                        tempId = childTempId,
                        name = childName,
                        parentTempId = parentTempId,
                        relativePath = childRelativePath
                    )
                )
                onStatsUpdate(true, false, 0L)
                
                scanDirectory(
                    dir = child,
                    parentTempId = childTempId,
                    currentRelativePath = childRelativePath,
                    options = options,
                    folders = folders,
                    files = files,
                    skippedItems = skippedItems,
                    onStatsUpdate = onStatsUpdate
                )
            } else if (child.isFile) {
                if (isHidden && !options.uploadHiddenFiles) continue

                val sizeBytes = child.length()
                if (sizeBytes > SAFE_SCAN_LIMIT) {
                    skippedItems.add(
                        SkippedItem(
                            name = childName,
                            relativePath = childRelativePath,
                            reason = "File exceeds 2GB size limit"
                        )
                    )
                    continue
                }

                val extension = MimeTypeMap.getFileExtensionFromUrl(child.absolutePath)
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension?.lowercase()) ?: "application/octet-stream"
                val fileTempId = UUID.randomUUID().toString()

                files.add(
                    ScannedFile(
                        tempId = fileTempId,
                        name = childName,
                        parentTempId = parentTempId,
                        mimeType = mimeType,
                        sizeBytes = sizeBytes,
                        path = child.absolutePath,
                        relativePath = childRelativePath
                    )
                )
                onStatsUpdate(false, false, sizeBytes)
            }
        }
    }
}
