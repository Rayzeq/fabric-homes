package io.github.rayzeq

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.entity.LivingEntity
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import net.minecraft.world.PersistentState
import net.minecraft.world.PersistentStateType
import net.minecraft.world.World
import java.util.*


class HomesState() : PersistentState() {
    var players: MutableMap<UUID, PlayerData> = HashMap()

    constructor(map: MutableMap<String, PlayerData>) : this() {
        this.players = map.mapKeys { entry -> UUID.fromString(entry.key) }.toMutableMap()
    }

    companion object {
        private val type: PersistentStateType<HomesState> = PersistentStateType(
            Homes.MOD_ID,
            { HomesState() },
            {
                Codec.unboundedMap(
                    Codec.STRING,
                    RecordCodecBuilder.create { instance ->
                        instance.group(
                            Codec.INT.fieldOf("maxHomes").forGetter<PlayerData> { x -> x.maxHomes },
                            Codec.unboundedMap(
                                Codec.STRING,
                                Codec.pair(
                                    Identifier.CODEC.fieldOf("dimension").codec(),
                                    Vec3d.CODEC.fieldOf("pos").codec()
                                )
                            ).fieldOf("homes")
                                .forGetter<PlayerData> { x -> x.homes }
                        ).apply(instance) { maxHomes, homes -> PlayerData(maxHomes, homes.toMutableMap()) }
                    }).xmap(
                    { map -> HomesState(map) },
                    { state -> state.players.mapKeys { entry -> entry.key.toString() } })
            },
            null
        )

        private fun getServerState(server: MinecraftServer?): HomesState {
            // Note: arbitrary choice to use 'World.OVERWORLD' instead of 'World.END' or 'World.NETHER'. Any work
            val persistentStateManager = server!!.getWorld(World.OVERWORLD)!!.persistentStateManager
            val state = persistentStateManager.getOrCreate(type)

            // If state is not marked dirty, when Minecraft closes, 'writeNbt' won't be called and therefore nothing will be saved.
            // Technically it's 'cleaner' if you only mark state as dirty when there was actually a change, but the vast majority
            // of mod writers are just going to be confused when their data isn't being saved, and so it's best just to 'markDirty' for them.
            // Besides, it's literally just setting a bool to true, and the only time there's a 'cost' is when the file is written to disk when
            // there were no actual change to any of the mods state (INCREDIBLY RARE).
            state.markDirty()

            return state
        }

        fun getPlayerState(player: LivingEntity): PlayerData {
            val serverState = getServerState(player.world.server)
            return serverState.players.computeIfAbsent(player.uuid) { _: UUID? -> PlayerData() }
        }
    }
}