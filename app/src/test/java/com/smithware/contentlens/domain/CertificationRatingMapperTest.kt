package com.smithware.contentlens.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CertificationRatingMapperTest {
    @Test
    fun movieAndTvCertificationsMapToPreliminaryLensRatings() {
        assertEquals(LensRating.Everyone, certificationToLensRating("G"))
        assertEquals(LensRating.SevenPlus, certificationToLensRating("PG"))
        assertEquals(LensRating.ThirteenPlus, certificationToLensRating("PG-13"))
        assertEquals(LensRating.EighteenPlus, certificationToLensRating("R"))
        assertEquals(LensRating.Everyone, certificationToLensRating("TV-G"))
        assertEquals(LensRating.ThirteenPlus, certificationToLensRating("TV-14"))
        assertEquals(LensRating.EighteenPlus, certificationToLensRating("TV-MA"))
        assertEquals(LensRating.InsufficientData, certificationToLensRating(null))
    }
}
