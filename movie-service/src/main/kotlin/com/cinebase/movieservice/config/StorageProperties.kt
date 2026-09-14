package com.cinebase.movieservice.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Local artwork storage configuration. DB stores only the URL/path. */
@ConfigurationProperties(prefix = "app")
data class StorageProperties(
    var uploadsDir: String = "uploads",
)
