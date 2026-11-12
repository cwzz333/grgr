package com.vladik.godbox.mixin;

import com.vladik.godbox.Config;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Жители больше не создают железных големов сами (villagers.golems=true возвращает ванильное поведение). */
@Mixin(VillagerEntity.class)
public abstract class VillagerMixin {
    @Inject(method = "summonGolem", at = @At("HEAD"), cancellable = true)
    private void godbox$noGolems(ServerWorld world, long time, int requiredCount, CallbackInfo ci) {
        if (!Config.b("villagers.golems", false)) ci.cancel();
    }
}
