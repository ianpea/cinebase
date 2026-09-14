package com.cinebase.movieservice.config

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

/**
 * Resolves the upload directory, ensures it exists on startup, and stores/removes artwork files.
 *
 * Only the resulting public URL is persisted in the database; the bytes live on local disk.
 */
@Component
class UploadStorage(properties: StorageProperties) {

    val uploadsDir: Path = Paths.get(properties.uploadsDir).toAbsolutePath().normalize()

    private val artworksDir: Path = uploadsDir.resolve(ARTWORKS_SEGMENT)

    @PostConstruct
    fun init() {
        Files.createDirectories(artworksDir)
    }

    /** Writes [file] under `artworks/` and returns the public URL path used by the frontend. */
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
    fun deleteByUrl(url: String): Boolean {
        val prefix = "/$UPLOADS_SEGMENT/"
        if (!url.startsWith(prefix)) return false
        val target = uploadsDir.resolve(url.removePrefix(prefix)).normalize()
        if (!target.startsWith(uploadsDir)) return false
        return Files.deleteIfExists(target)
    }

    companion object {
        const val UPLOADS_SEGMENT = "uploads"
        const val ARTWORKS_SEGMENT = "artworks"
    }
}
