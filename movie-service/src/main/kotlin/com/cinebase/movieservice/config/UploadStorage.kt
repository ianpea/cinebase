package com.cinebase.movieservice.config

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Resolves the upload directory and ensures it exists on startup. */
@Component
class UploadStorage(properties: StorageProperties) {

    val uploadsDir: Path = Paths.get(properties.uploadsDir).toAbsolutePath().normalize()

    @PostConstruct
    fun init() {
        Files.createDirectories(uploadsDir)
    }
}
