package com.smithware.contentlens.data.tmdb

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

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
}
