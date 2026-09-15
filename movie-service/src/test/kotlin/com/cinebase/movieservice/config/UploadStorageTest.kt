package com.cinebase.movieservice.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.web.MockMultipartFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class UploadStorageTest {

    @TempDir
    lateinit var uploadsDir: Path

    private fun storage(): UploadStorage =
        UploadStorage(StorageProperties(uploadsDir.toString())).also { it.init() }

    private fun imageFile(bytes: ByteArray = ByteArray(4)) =
        MockMultipartFile("file", "poster.png", "image/png", bytes)

    @Test
    fun `storeArtwork writes the bytes and returns a public url`() {
        val storage = storage()

        val url = storage.storeArtwork(imageFile(ByteArray(16) { 1 }), ".png")

        assertThat(url).startsWith("/uploads/artworks/").endsWith(".png")
        val stored = storage.uploadsDir.resolve(url.removePrefix("/uploads/"))
        assertThat(stored).isRegularFile()
        assertThat(Files.readAllBytes(stored)).hasSize(16)
    }

    @Test
    fun `storeArtwork gives every file a unique name`() {
        val storage = storage()

        val first = storage.storeArtwork(imageFile(), ".png")
        val second = storage.storeArtwork(imageFile(), ".png")

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `deleteByUrl removes the stored file`() {
        val storage = storage()
        val url = storage.storeArtwork(imageFile(), ".jpg")

        assertThat(storage.deleteByUrl(url)).isTrue()
        assertThat(storage.deleteByUrl(url)).isFalse()
    }

    @Test
    fun `deleteByUrl ignores urls that do not point into the uploads directory`() {
        val storage = storage()
        val outside = uploadsDir.parent.resolve("keep-me.txt")
        Files.writeString(outside, "important")

        assertThat(storage.deleteByUrl("/etc/passwd")).isFalse()
        assertThat(storage.deleteByUrl("https://example.com/a.png")).isFalse()
        assertThat(storage.deleteByUrl("/uploads/../keep-me.txt")).isFalse()
        assertThat(outside).isRegularFile()
    }

    @Test
    fun `deleteByUrl works with a relative uploads directory`() {
        val relative = File("build/test-uploads-relative")
        relative.deleteRecursively()
        val storage = UploadStorage(StorageProperties(relative.path)).also { it.init() }
        try {
            val url = storage.storeArtwork(imageFile(), ".png")

            assertThat(storage.deleteByUrl(url)).isTrue()
        } finally {
            relative.deleteRecursively()
        }
    }
}
