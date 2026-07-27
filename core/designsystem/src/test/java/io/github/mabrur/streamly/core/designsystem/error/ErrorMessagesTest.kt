package io.github.mabrur.streamly.core.designsystem.error

import io.github.mabrur.streamly.core.designsystem.R
import io.github.mabrur.streamly.domain.error.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMessagesTest {

    @Test
    fun `network error maps to the network strings`() {
        assertEquals(R.string.error_network_title, AppError.Network.titleResId())
        assertEquals(R.string.error_network_body, AppError.Network.bodyResId())
    }

    @Test
    fun `not found maps to the not-found strings`() {
        assertEquals(R.string.error_not_found_title, AppError.NotFound.titleResId())
        assertEquals(R.string.error_not_found_body, AppError.NotFound.bodyResId())
    }

    @Test
    fun `storage error maps to the storage strings`() {
        assertEquals(R.string.error_storage_title, AppError.Storage.titleResId())
        assertEquals(R.string.error_storage_body, AppError.Storage.bodyResId())
    }

    @Test
    fun `unknown error maps to the generic strings`() {
        val error = AppError.Unknown("boom")
        assertEquals(R.string.error_generic_title, error.titleResId())
        assertEquals(R.string.error_generic_body, error.bodyResId())
    }

    @Test
    fun `network and unknown errors are retryable`() {
        assertTrue(AppError.Network.isRetryable())
        assertTrue(AppError.Unknown("boom").isRetryable())
    }

    @Test
    fun `not found and storage errors are not retryable`() {
        assertFalse(AppError.NotFound.isRetryable())
        assertFalse(AppError.Storage.isRetryable())
    }
}
