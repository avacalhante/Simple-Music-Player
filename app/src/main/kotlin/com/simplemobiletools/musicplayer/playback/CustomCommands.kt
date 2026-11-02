package com.simplemobiletools.musicplayer.playback

import android.content.Context
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.helpers.PATH

/**
 * Enum class representing custom commands that are used within the app and by media controller clients (e.g. system media controls).
 */
enum class CustomCommands(val customAction: String) {
    CLOSE_PLAYER(customAction = PATH + "CLOSE_PLAYER"),
    RELOAD_CONTENT(customAction = PATH + "RELOAD_CONTENT"),
    TOGGLE_SLEEP_TIMER(customAction = PATH + "TOGGLE_SLEEP_TIMER"),
    TOGGLE_SKIP_SILENCE(customAction = PATH + "TOGGLE_SKIP_SILENCE"),
    SET_NEXT_ITEM(customAction = PATH + "SET_NEXT_ITEM"),
    SET_SHUFFLE_ORDER(customAction = PATH + "SET_SHUFFLE_ORDER");

    val sessionCommand = SessionCommand(customAction, Bundle.EMPTY)

    companion object {
        fun fromSessionCommand(sessionCommand: SessionCommand): CustomCommands? {
            return values().find { it.customAction == sessionCommand.customAction }
        }
    }
}

internal val customCommands = CustomCommands.values().map { it.sessionCommand }

/**
 * Buttons shown next to the default playback controls in the media notification.
 * The shuffle button sends a player command instead of a custom one, so the player toggles its own shuffle mode
 * and reports it back like any other client would; [PlaybackService] rebuilds the layout on state changes so the
 * icon keeps matching the player.
 */
internal fun Context.getCustomLayout(isShuffleEnabled: Boolean): List<CommandButton> {
    val shuffleButton = CommandButton.Builder(if (isShuffleEnabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
        .setDisplayName(getString(if (isShuffleEnabled) R.string.disable_shuffle else R.string.enable_shuffle))
        .setPlayerCommand(Player.COMMAND_SET_SHUFFLE_MODE)
        .build()

    val closeButton = CommandButton.Builder()
        .setDisplayName(getString(com.simplemobiletools.commons.R.string.close))
        .setSessionCommand(CustomCommands.CLOSE_PLAYER.sessionCommand)
        .setIconResId(com.simplemobiletools.commons.R.drawable.ic_cross_vector)
        .build()

    return listOf(shuffleButton, closeButton)
}
