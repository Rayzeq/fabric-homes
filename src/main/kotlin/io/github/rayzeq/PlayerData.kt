package io.github.rayzeq

import com.mojang.datafixers.util.Pair
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d

class PlayerData() {
    var maxHomes: Int = 1
    var homes: MutableMap<String, Pair<Identifier, Vec3d>> = HashMap()

    constructor(maxHomes: Int, homes: MutableMap<String, Pair<Identifier, Vec3d>>) : this() {
        this.maxHomes = maxHomes
        this.homes = homes
    }
}