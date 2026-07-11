package com.smithware.contentlens.domain

fun certificationToLensRating(certification: String?): LensRating {
    return when (certification?.uppercase()?.trim()) {
        "G", "TV-G", "TV-Y" -> LensRating.Everyone
        "PG", "TV-Y7", "TV-PG" -> LensRating.SevenPlus
        "PG-13", "TV-14" -> LensRating.ThirteenPlus
        "R", "NC-17", "TV-MA" -> LensRating.EighteenPlus
        null, "" -> LensRating.InsufficientData
        else -> LensRating.InsufficientData
    }
}
