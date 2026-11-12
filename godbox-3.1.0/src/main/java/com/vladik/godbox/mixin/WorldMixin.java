package com.vladik.godbox.mixin;

import com.vladik.godbox.Undo;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Запоминаем старые блоки для отмены (Ctrl+Z). */
@Mixin(World.class)
public abstract class WorldMixin {
    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z", at = @At("HEAD"))
    private void godbox$record(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        World self = (World) (Object) this;
        if (!self.isClient && Undo.active(self) && !self.isOutOfHeightLimit(pos)) {
            Undo.record(pos, self.getBlockState(pos));
        }
    }
}
