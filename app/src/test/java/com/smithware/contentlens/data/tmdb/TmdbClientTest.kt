package com.smithware.contentlens.data.tmdb

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URLEncoder

class TmdbClientTest {
    @Test
    fun missingTokenThrowsConfigurationError() = runBlocking {
        val error = runCatching {
            TmdbClient(readAccessToken = "", apiKey = "").searchAll("Moana")
        }.exceptionOrNull()

        assertTrue(error is TmdbSearchError.MissingToken)
    }

    @Test
    fun unauthorizedResponseIsNotConvertedToEmptyResults() = runBlocking {
        withServer(status = 401, body = """{"status_message":"Invalid token"}""") { baseUrl ->
            val error = runCatching {
                TmdbClient(readAccessToken = "bad-token", baseUrl = baseUrl)
                    .search(RemoteMediaType.Movie, "Moana")
            }.exceptionOrNull()

            assertTrue(error is TmdbSearchError.Authentication)
            assertEquals(401, (error as TmdbSearchError.Authentication).statusCode)
        }
    }

    @Test
    fun serverErrorIsNotConvertedToEmptyResults() = runBlocking {
        withServer(status = 500, body = """{"status_message":"Server problem"}""") { baseUrl ->
            val error = runCatching {
                TmdbClient(readAccessToken = "token", baseUrl = baseUrl)
                    .search(RemoteMediaType.Tv, "Bluey")
            }.exceptionOrNull()

            assertTrue(error is TmdbSearchError.Server)
            assertEquals(500, (error as TmdbSearchError.Server).statusCode)
        }
    }

    @Test
    fun searchRequestEncodesQueryAndParsesResults() = runBlocking {
        withServer(status = 200, body = """{"page":1,"total_pages":1,"total_results":1,"results":[{"id":1,"title":"The Lion King","release_date":"1994-06-24"}]}""") { baseUrl, paths ->
            val page = TmdbClient(readAccessToken = "token", baseUrl = baseUrl)
                .search(RemoteMediaType.Movie, "The Lion King")

            assertEquals("The Lion King", page.results.single().title)
            assertTrue(paths.single().contains("query=The+Lion+King"))
        }
    }

    @Test
    fun searchRequestEncodesPunctuationAndPage() = runBlocking {
        withServer(status = 200, body = """{"page":2,"total_pages":3,"total_results":1,"results":[{"id":1,"title":"WALL-E","release_date":"2008-06-26"}]}""") { baseUrl, paths ->
            val page = TmdbClient(readAccessToken = "token", baseUrl = baseUrl)
                .search(RemoteMediaType.Movie, "WALL-E: A Bug's Life?", page = 2)

            assertEquals(2, page.page)
            assertTrue(page.hasMore)
            val encoded = URLEncoder.encode("WALL-E: A Bug's Life?", Charsets.UTF_8.name())
            assertTrue(paths.single().contains("query=$encoded"))
            assertTrue(paths.single().contains("page=2"))
        }
    }

    @Test
    fun searchAllRequestsMovieAndTvAndMergesResultsWithImages() = runBlocking {
        withRouteServer(
            routes = mapOf(
                "/search/movie" to ok(
                    """{"page":1,"total_pages":2,"total_results":1,"results":[{"id":11,"title":"Star Wars","release_date":"1977-05-25","poster_path":"/movie.jpg","popularity":80,"vote_count":1000}]}"""
                ),
                "/search/tv" to ok(
                    """{"page":1,"total_pages":1,"total_results":1,"results":[{"id":1396,"name":"Star Wars: The Clone Wars","first_air_date":"2008-01-20","poster_path":"/tv.jpg","popularity":120,"vote_count":2000}]}"""
                ),
                "/configuration" to ok(CONFIG_BODY)
            )
        ) { baseUrl, paths ->
            val (page, config) = TmdbClient(readAccessToken = "token", baseUrl = baseUrl)
                .searchAll("Star Wars")

            assertEquals(2, page.results.size)
            assertEquals(RemoteMediaType.Movie, page.results.first().mediaType)
            assertEquals("Star Wars", page.results.first().title)
            assertEquals(RemoteMediaType.Tv, page.results.last().mediaType)
            assertEquals(2, page.totalPages)
            assertEquals(2, page.totalResults)
            assertEquals("https://images.example/t/p/", config.secureBaseUrl)
            assertTrue(paths.any { it.startsWith("/search/movie?") })
            assertTrue(paths.any { it.startsWith("/search/tv?") })
            assertTrue(paths.any { it.startsWith("/configuration") })
        }
    }

    @Test
    fun searchAllRanksPunctuationNormalizedExactMatchesBeforePopularity() = runBlocking {
        withRouteServer(
            routes = mapOf(
                "/search/movie" to ok(
                    """{"page":1,"total_pages":1,"total_results":1,"results":[{"id":10681,"title":"WALL\u00B7E","release_date":"2008-06-26","poster_path":"/walle.jpg","popularity":10,"vote_count":100}]}"""
                ),
                "/search/tv" to ok(
                    """{"page":1,"total_pages":1,"total_results":1,"results":[{"id":1,"name":"The Ramparts of Ice","first_air_date":"2026-01-01","poster_path":"/ice.jpg","popularity":999,"vote_count":10}]}"""
                ),
                "/configuration" to ok(CONFIG_BODY)
            )
        ) { baseUrl, _ ->
            val (page, _) = TmdbClient(readAccessToken = "token", baseUrl = baseUrl)
                .searchAll("WALL-E")

            assertEquals("WALL\u00B7E", page.results.first().title)
            assertEquals(RemoteMediaType.Movie, page.results.first().mediaType)
        }
    }

    @Test
    fun searchAllTriesMiddleDotVariantForHyphenatedTitles() = runBlocking {
        withDynamicServer({ uri ->
            when {
                uri.path == "/configuration" -> ok(CONFIG_BODY)
                uri.path == "/search/movie" && uri.rawQuery.orEmpty().contains("query=WALL-E") ->
                    ok("""{"page":1,"total_pages":1,"total_results":1,"results":[{"id":1,"title":"Wall Engravings","release_date":"1968-02-07","poster_path":"/wall.jpg","popularity":1}]}""")
                uri.path == "/search/movie" && uri.rawQuery.orEmpty().contains("query=WALL%C2%B7E") ->
                    ok("""{"page":1,"total_pages":1,"total_results":1,"results":[{"id":10681,"title":"WALL\u00B7E","release_date":"2008-06-26","poster_path":"/walle.jpg","popularity":10}]}""")
                uri.path == "/search/tv" -> ok("""{"page":1,"total_pages":1,"total_results":0,"results":[]}""")
                else -> 404 to """{"status_message":"Not found"}"""
            }
        }) { baseUrl, paths ->
            val (page, _) = TmdbClient(readAccessToken = "token", baseUrl = baseUrl)
                .searchAll("WALL-E")

            assertEquals("WALL\u00B7E", page.results.first().title)
            assertTrue(paths.any { it.contains("query=WALL-E") })
            assertTrue(paths.any { it.contains("query=WALL%C2%B7E") })
        }
    }

    @Test
    fun emptySearchDoesNotHitNetwork() = runBlocking {
        withRouteServer(routes = emptyMap()) { baseUrl, paths ->
            val (page, _) = TmdbClient(readAccessToken = "token", baseUrl = baseUrl)
                .searchAll(" ")

            assertEquals(0, page.results.size)
            assertEquals(0, page.totalResults)
            assertEquals(emptyList<String>(), paths)
        }
    }

    @Test
    fun malformedSearchResponseIsParsingError() = runBlocking {
        withServer(status = 200, body = """{"page":1,"results":""") { baseUrl ->
            val error = runCatching {
                TmdbClient(readAccessToken = "token", baseUrl = baseUrl)
                    .search(RemoteMediaType.Movie, "Moana")
            }.exceptionOrNull()

            assertTrue(error is TmdbSearchError.Parsing)
        }
    }

    @Test
    fun malformedConfigurationResponseIsParsingError() = runBlocking {
        withRouteServer(
            routes = mapOf(
                "/search/movie" to ok("""{"page":1,"total_pages":1,"total_results":0,"results":[]}"""),
                "/search/tv" to ok("""{"page":1,"total_pages":1,"total_results":0,"results":[]}"""),
                "/configuration" to ok("""{"bad":true}""")
            )
        ) { baseUrl, _ ->
            val error = runCatching {
                TmdbClient(readAccessToken = "token", baseUrl = baseUrl)
                    .searchAll("Moana")
            }.exceptionOrNull()

            assertTrue(error is TmdbSearchError.Parsing)
        }
    }

    @Test
    fun networkFailureIsOfflineError() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val error = runCatching {
            TmdbClient(readAccessToken = "token", baseUrl = "http://127.0.0.1:$port")
                .search(RemoteMediaType.Movie, "Moana")
        }.exceptionOrNull()

        assertTrue(error is TmdbSearchError.Offline)
    }

    @Test
    fun apiKeyAuthenticationAppendsApiKeyWithoutBearerToken() = runBlocking {
        withServer(status = 200, body = """{"page":1,"total_pages":1,"total_results":0,"results":[]}""") { baseUrl, paths ->
            TmdbClient(readAccessToken = "", apiKey = "abc123", baseUrl = baseUrl)
                .search(RemoteMediaType.Tv, "Bluey")

            val path = paths.single()
            assertTrue(path.contains("query=Bluey"))
            assertTrue(path.contains("api_key=abc123"))
        }
    }

    private suspend fun withServer(
        status: Int,
        body: String,
        block: suspend (baseUrl: String) -> Unit
    ) {
        withServer(status, body) { baseUrl, _ -> block(baseUrl) }
    }

    private suspend fun withServer(
        status: Int,
        body: String,
        block: suspend (baseUrl: String, paths: List<String>) -> Unit
    ) {
        val paths = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            paths += exchange.requestURI.toString()
            exchange.sendResponseHeaders(status, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}", paths)
        } finally {
            server.stop(0)
        }
    }

    private suspend fun withRouteServer(
        routes: Map<String, Pair<Int, String>>,
        block: suspend (baseUrl: String, paths: List<String>) -> Unit
    ) {
        withDynamicServer({ uri -> routes[uri.path] ?: (404 to """{"status_message":"Not found"}""") }, block)
    }

    private suspend fun withDynamicServer(
        responseFor: (java.net.URI) -> Pair<Int, String>,
        block: suspend (baseUrl: String, paths: List<String>) -> Unit
    ) {
        val paths = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            paths += exchange.requestURI.toString()
            val (status, body) = responseFor(exchange.requestURI)
            exchange.sendResponseHeaders(status, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}", paths)
        } finally {
            server.stop(0)
        }
    }

    private fun ok(body: String): Pair<Int, String> = 200 to body

    private companion object {
        const val CONFIG_BODY = """
            {
              "images": {
                "secure_base_url": "https://images.example/t/p/",
                "poster_sizes": ["w342", "original"],
                "backdrop_sizes": ["w780", "original"],
                "profile_sizes": ["w185", "original"],
                "logo_sizes": ["w154", "original"]
              }
            }
        """
    }
}

