package io.github.rayzeq

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.server.command.ServerCommandSource
import java.util.concurrent.CompletableFuture

class HomeSuggestionProvider : SuggestionProvider<ServerCommandSource> {
    @Throws(CommandSyntaxException::class)
    override fun getSuggestions(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val player = context.source.player!!
        val playerState = HomesState.getPlayerState(player)

        for (name in playerState.homes.keys) {
            builder.suggest(name)
        }

        return builder.buildFuture()
    }
}