package io.github.rayzeq

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.datafixers.util.Pair
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.inventory.Inventories
import net.minecraft.network.packet.s2c.play.PositionFlag
import net.minecraft.predicate.item.ItemPredicate
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.math.roundToLong


object Homes : ModInitializer {
    const val MOD_ID: String = "homes"

    private val logger = LoggerFactory.getLogger("homes")

    override fun onInitialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            val sethome = dispatcher.register(
                CommandManager.literal("sethome")
                    .requires { source -> source.player !== null }
                    .then(
                        CommandManager
                            .argument("name", StringArgumentType.word())
                            .suggests(HomeSuggestionProvider())
                            .executes { context: CommandContext<ServerCommandSource> ->
                                val name = StringArgumentType.getString(context, "name")
                                val player = context.source.player!!

                                val world = player.world
                                val pos = player.pos
                                val playerState = HomesState.getPlayerState(player)

                                if (playerState.homes.containsKey(name)) {
                                    playerState.homes[name] = Pair(world.registryKey.value, pos)

                                    context.source.sendFeedback(
                                        { Text.literal("Home $name successfully changed") },
                                        false
                                    )
                                    1
                                } else if (playerState.homes.size >= playerState.maxHomes) {
                                    context.source.sendError(Text.literal("You already have the maximal number of homes"))
                                    0
                                } else {
                                    playerState.homes[name] = Pair(world.registryKey.value, pos)

                                    context.source.sendFeedback(
                                        { Text.literal("Home $name successfully created") },
                                        false
                                    )
                                    1
                                }
                            }
                    )
            )
            dispatcher.register(CommandManager.literal("sh").redirect(sethome))

            val home = dispatcher.register(
                CommandManager.literal("home")
                    .requires { source -> source.player !== null }
                    .executes(Homes::executeHome)
                    .then(
                        CommandManager
                            .argument("name", StringArgumentType.word())
                            .suggests(HomeSuggestionProvider())
                            .executes { context: CommandContext<ServerCommandSource> ->
                                val name = StringArgumentType.getString(context, "name")
                                val player = context.source.player!!
                                val playerState = HomesState.getPlayerState(player)

                                val location = playerState.homes[name];
                                if (location == null) {
                                    context.source.sendError(Text.literal("Home $name not found"))
                                    0
                                } else {
                                    val dimension = location.first
                                    val pos = location.second

                                    val world = context.source.server.getWorld(
                                        RegistryKey.of(RegistryKeys.WORLD, dimension)
                                    )

                                    val vehicle = player.vehicle
                                    if (vehicle != null) {
                                        vehicle.teleport(
                                            world,
                                            pos.x, pos.y, pos.z,
                                            EnumSet.noneOf(PositionFlag::class.java),
                                            vehicle.yaw, vehicle.pitch,
                                            false
                                        )
                                    } else {
                                        player.teleport(
                                            world,
                                            pos.x, pos.y, pos.z,
                                            EnumSet.noneOf(PositionFlag::class.java),
                                            player.yaw, player.pitch,
                                            false
                                        )
                                    }

                                    context.source.sendFeedback(
                                        { Text.literal("Teleporting to $name") },
                                        false
                                    )
                                    1
                                }
                            }
                    )
            )
            dispatcher.register(
                CommandManager.literal("h")
                    .requires { source -> source.player !== null }
                    .executes(Homes::executeHome)
                    .redirect(home)
            );

            val delhome = dispatcher.register(
                CommandManager
                    .literal("delhome")
                    .requires { source -> source.player !== null }
                    .then(
                        CommandManager
                            .argument("name", StringArgumentType.word())
                            .suggests(HomeSuggestionProvider())
                            .executes { context: CommandContext<ServerCommandSource> ->
                                val name = StringArgumentType.getString(context, "name")
                                val player = context.source.player!!
                                val playerState = HomesState.getPlayerState(player)

                                val oldHome = playerState.homes.remove(name)
                                if (oldHome != null) {
                                    context.source.sendFeedback(
                                        { Text.literal("Home $name has been deleted") },
                                        false
                                    )
                                    1
                                } else {
                                    context.source.sendError(Text.literal("Home $name not found"))
                                    0
                                }

                            }
                    )
            )
            dispatcher.register(CommandManager.literal("dh").redirect(delhome))

            val buyhome = dispatcher.register(
                CommandManager
                    .literal("buyhome")
                    .requires { source -> source.player !== null }
                    .executes(Homes::executeBuyHome)
            )
            dispatcher.register(
                CommandManager.literal("bh")
                    .requires { source -> source.player !== null }
                    .executes(Homes::executeBuyHome)
                    .redirect(buyhome)
            );
        }
    }

    private fun executeHome(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player!!
        val playerState = HomesState.getPlayerState(player)

        if (playerState.homes.isEmpty()) {
            context.source.sendError(Text.literal("You don't have any home"))
            return 0
        } else {
            val message =
                Text.literal("Homes (${playerState.homes.size} of ${playerState.maxHomes}):\n")
            for (entry in playerState.homes.entries) {
                val dimension = entry.value.first.toTranslationKey()
                val pos = entry.value.second

                val name = Text.literal(entry.key)
                name.setStyle(
                    Style.EMPTY
                        .withClickEvent(ClickEvent.RunCommand("home ${entry.key}"))
                        .withColor(Formatting.GREEN)
                        .withHoverEvent(HoverEvent.ShowText(Text.literal("Click to teleport")))
                )

                val text = Text.literal(" - ")
                text.append(name)
                text.append(" in ")
                when (dimension) {
                    "minecraft.overworld" -> text.append("the Overworld")
                    "minecraft.the_nether" -> text.append("the Nether")
                    "minecraft.the_end" -> text.append("the End")
                    else -> text.append(dimension)
                }
                text.append(" at ${pos.x.roundToLong()} ${pos.y.roundToLong()} ${pos.z.roundToLong()}\n")

                message.append(text)
            }
            context.source.sendFeedback(
                { message },
                false
            )
            return playerState.homes.size
        }
    }

    private fun executeBuyHome(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player!!
        val playerState = HomesState.getPlayerState(player)

        val diamond = Registries.ITEM.get(Identifier.of("minecraft:diamond"))
        val predicate = ItemPredicate.Builder().items(Registries.ITEM, diamond).build()

        if (Inventories.remove(player.inventory, predicate, 32, true) >= 32) {
            Inventories.remove(player.inventory, predicate, 32, false)
            playerState.maxHomes += 1

            context.source.sendFeedback(
                { Text.literal("You can now create ${playerState.maxHomes} homes") },
                false
            )
            return 1
        } else {
            context.source.sendError(Text.literal("You need to have 32 diamond in your inventory"))
            return 0
        }
    }
}