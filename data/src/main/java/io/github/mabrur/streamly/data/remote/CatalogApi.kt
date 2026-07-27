package io.github.mabrur.streamly.data.remote

import io.github.mabrur.streamly.data.remote.dto.CatalogDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

internal const val CATALOG_URL = "https://streamly.local/catalog.json"

internal class CatalogApi(private val client: HttpClient) {
    suspend fun fetchCatalog(): CatalogDto = client.get(CATALOG_URL).body()
}
