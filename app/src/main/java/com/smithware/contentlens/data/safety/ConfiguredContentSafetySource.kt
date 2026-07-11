package com.smithware.contentlens.data.safety

import com.smithware.contentlens.BuildConfig
import com.smithware.contentlens.data.tmdb.TmdbTitleDetails

class ConfiguredContentSafetySource(
    private val proxy: ContentSafetySource = ContentLensProxySafetyClient(),
    private val directProvider: ContentSafetySource = DoesTheDogDieClient(),
    private val proxyBaseUrl: String = BuildConfig.CONTENTLENS_API_BASE_URL
) : ContentSafetySource {
    override suspend fun reportFor(details: TmdbTitleDetails): ExternalSafetyReport? {
        return if (proxyBaseUrl.isNotBlank()) {
            proxy.reportFor(details)
        } else {
            directProvider.reportFor(details)
        }
    }
}
