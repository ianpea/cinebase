package com.cinebase.movieservice.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Serves uploaded artwork files from the local uploads directory.
 *
 * Public artwork URLs start with `UploadStorage.UPLOADS_SEGMENT`, and the database only stores
 * that path, so this mapping is what makes `artworkUrl` resolvable by clients.
 */
@Configuration
class WebConfig(private val storage: UploadStorage) : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val location = storage.uploadsDir.toUri().toString().let { if (it.endsWith("/")) it else "$it/" }
        registry.addResourceHandler("/${UploadStorage.UPLOADS_SEGMENT}/**")
            .addResourceLocations(location)
    }
}
