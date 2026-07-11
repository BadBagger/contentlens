package com.smithware.contentlens.data.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageUrlBuilderTest {
    @Test
    fun posterUsesConfiguredBaseAndPreferredMediumSize() {
        val builder = ImageUrlBuilder(
            TmdbImageConfiguration(
                secureBaseUrl = "https://images.example/t/p/",
                posterSizes = listOf("w92", "w342", "original")
            )
        )

        assertEquals(
            "https://images.example/t/p/w342/poster.jpg",
            builder.poster("/poster.jpg")
        )
    }

    @Test
    fun backdropUsesConfiguredLargeSize() {
        val builder = ImageUrlBuilder(
            TmdbImageConfiguration(
                secureBaseUrl = "https://images.example/t/p/",
                backdropSizes = listOf("w780", "w1280", "original")
            )
        )

        assertEquals(
            "https://images.example/t/p/w1280/backdrop.jpg",
            builder.backdrop("backdrop.jpg")
        )
    }

    @Test
    fun blankPathReturnsNull() {
        assertNull(ImageUrlBuilder().poster(null))
        assertNull(ImageUrlBuilder().poster(""))
    }
}
