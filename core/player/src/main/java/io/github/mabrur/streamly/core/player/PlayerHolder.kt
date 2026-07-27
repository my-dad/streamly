package io.github.mabrur.streamly.core.player

import androidx.media3.common.Player

/**
 * Owns one [Player] instance for one screen.
 *
 * Deliberately narrow: transport controls (play/pause/seek/mute) are driven by the
 * media3-compose state holders directly against [player], so this interface only covers
 * what the ViewModel genuinely owns — media selection and the lifecycle. See D-008.
 */
interface PlayerHolder {
    val player: Player

    /**
     * Prepares [videoId] and seeks to [startPositionMs]. Does not start playback.
     *
     * [videoId] is not decoration: if that video has been downloaded, it is played from
     * local storage and [hlsUrl] is never touched.
     */
    fun setMedia(videoId: String, hlsUrl: String, startPositionMs: Long = 0L)

    fun pause()

    fun resume()

    fun currentPositionMs(): Long

    /** Idempotent. Calling twice must not throw. */
    fun release()
}
