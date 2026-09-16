package com.cinebase.movieservice.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

/**
 * Only the public URL is persisted in the database; the bytes live on local disk.
 */
@Component
class UploadStorage(properties: StorageProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    val uploadsDir: Path = Paths.get(properties.uploadsDir).toAbsolutePath().normalize()

    private val artworksDir: Path = uploadsDir.resolve(ARTWORKS_SEGMENT)

    @PostConstruct
    fun init() {
        Files.createDirectories(artworksDir)
    }

    fun storeArtwork(file: MultipartFile, extension: String): String {
        val fileName = "${UUID.randomUUID()}$extension"
        val target = artworksDir.resolve(fileName)
        file.inputStream.use { input ->
            Files.newOutputStream(target).use { output -> input.copyTo(output) }
        }
        return "/$UPLOADS_SEGMENT/$ARTWORKS_SEGMENT/$fileName"
    }

    /**
     * Deletes the file behind a previously stored public URL.
     *
     * Unrecognised or escaping paths are ignored so a bad DB value can never delete arbitrary files.
     */
    internal fun deleteByUrl(url: String): Boolean {
        val prefix = "/$UPLOADS_SEGMENT/"
        if (!url.startsWith(prefix)) return false
        val target = uploadsDir.resolve(url.removePrefix(prefix)).normalize()
        if (!target.startsWith(uploadsDir)) return false
        return Files.deleteIfExists(target)
    }

    /**
     * Defers removal to after the commit — a rollback must not delete files the database still
     * references, so an orphaned file is the acceptable failure.
     */
    fun deleteByUrlAfterCommit(url: String) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(url)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = deleteQuietly(url)
            },
        )
    }

    private fun deleteQuietly(url: String) {
        try {
            deleteByUrl(url)
        } catch (ex: IOException) {
            log.warn("Could not delete artwork file '{}': {}", url, ex.message)
        }
    }

    companion object {
        const val UPLOADS_SEGMENT = "uploads"
        const val ARTWORKS_SEGMENT = "artworks"
    }
}
