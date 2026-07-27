package io.github.mabrur.streamly.data.remote

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * MockEngine serving the bundled catalog.
 *
 * Only the catalog API is faked. HLS media is fetched over the real network by
 * Media3's own DataSource — otherwise there would be no genuine adaptive streaming
 * and nothing real to download.
 *
 * @param readAsset supplies the raw catalog.json contents.
 * @param failEveryNth every Nth request fails, so loading and error states are real
 *   behaviour rather than staged. Pass 0 to disable.
 * @param latencyMillis artificial delay range, so the loading state is observable.
 */
internal fun mockCatalogEngine(
    readAsset: () -> String,
    failEveryNth: Int = 8,
    latencyMillis: LongRange = 300L..600L,
): MockEngine {
    val counter = AtomicInteger(0)
    return MockEngine { request ->
        delay(Random.nextLong(latencyMillis.first, latencyMillis.last + 1))

        val n = counter.incrementAndGet()
        if (failEveryNth > 0 && n % failEveryNth == 0) {
            return@MockEngine respondError(HttpStatusCode.ServiceUnavailable)
        }

        when (request.url.toString()) {
            CATALOG_URL -> respond(
                content = readAsset(),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )

            else -> respondError(HttpStatusCode.NotFound)
        }
    }
}
