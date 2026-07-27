package io.github.mabrur.streamly.data.repository

import io.github.mabrur.streamly.data.remote.CATALOG_URL
import io.github.mabrur.streamly.data.remote.CatalogApi
import io.github.mabrur.streamly.domain.error.AppError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRepositoryImplTest {

    private val payload = """
        {
          "categories": ["All", "Music"],
          "videos": [
            { "id": "v01", "title": "T1", "channelName": "C1", "channelAvatarUrl": "a1",
              "thumbnailUrl": "t1", "hlsUrl": "h1", "durationMs": 1000, "viewCount": 10,
              "publishedAtEpochSeconds": 1700000000, "category": "Music" },
            { "id": "v02", "title": "T2", "channelName": "C2", "channelAvatarUrl": "a2",
              "thumbnailUrl": "t2", "hlsUrl": "h2", "durationMs": 2000, "viewCount": 20,
              "publishedAtEpochSeconds": 1700000001, "category": "Gaming" }
          ],
          "shorts": [
            { "id": "s01", "title": "S1", "channelName": "C1", "hlsUrl": "sh1",
              "likeCount": 5, "commentCount": 2 }
          ],
          "profile": { "name": "N", "email": "e@x.com", "avatarUrl": "av" }
        }
    """.trimIndent()

    private fun repository(engine: MockEngine): CatalogRepositoryImpl {
        val client = HttpClient(engine) {
            // Ktor does NOT throw on non-2xx by default; without this a 503 would be
            // handed to the deserializer and surface as AppError.Unknown, not Network.
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return CatalogRepositoryImpl(CatalogApi(client))
    }

    private fun okEngine() = MockEngine {
        respond(
            content = payload,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }

    @Test
    fun `getHomeFeed returns mapped domain videos`() = runTest {
        val result = repository(okEngine()).getHomeFeed()

        assertEquals(listOf("v01", "v02"), result.getOrThrow().videos.map { it.id })
    }

    @Test
    fun `getShorts returns mapped domain shorts`() = runTest {
        val result = repository(okEngine()).getShorts()

        assertEquals(listOf("s01"), result.getOrThrow().map { it.id })
    }

    @Test
    fun `getVideo returns the requested video`() = runTest {
        val result = repository(okEngine()).getVideo("v02")

        assertEquals("T2", result.getOrThrow().title)
    }

    @Test
    fun `getVideo returns NotFound for an unknown id`() = runTest {
        val result = repository(okEngine()).getVideo("nope")

        assertTrue(result.isFailure)
        assertEquals(AppError.NotFound, result.exceptionOrNull())
    }

    @Test
    fun `getRelated excludes the requested video itself`() = runTest {
        val result = repository(okEngine()).getRelated("v01")

        assertEquals(listOf("v02"), result.getOrThrow().map { it.id })
    }

    @Test
    fun `getProfile returns the mapped profile`() = runTest {
        val result = repository(okEngine()).getProfile()

        assertEquals("e@x.com", result.getOrThrow().email)
    }

    @Test
    fun `transport failure surfaces as AppError Network`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }

        val result = repository(engine).getHomeFeed()

        assertTrue(result.isFailure)
        assertEquals(AppError.Network, result.exceptionOrNull())
    }

    @Test
    fun `malformed payload surfaces as AppError Unknown`() = runTest {
        val engine = MockEngine {
            respond(
                content = "{ not json",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = repository(engine).getHomeFeed()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.Unknown)
    }

    @Test
    fun `catalog url is requested exactly once per call`() = runTest {
        val engine = okEngine()

        repository(engine).getHomeFeed()

        assertEquals(1, engine.requestHistory.size)
        assertEquals(CATALOG_URL, engine.requestHistory.first().url.toString())
    }
}
