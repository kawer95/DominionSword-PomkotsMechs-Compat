package com.arxyt.dominionsword.pomkotscompat.mixin;

import com.arxyt.dominionsword.config.ServerConfig;
import grcmcs.minecraft.mods.pomkotsmechs.util.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.StackWalker;
import java.util.List;

/** Blocks direct weapon terrain edits while preserving PMVC engineering equipment. */
@Mixin(Utils.class)
public abstract class PomkotsBlockDestructionMixin {
    private static final StackWalker DOMINION$STACK_WALKER = StackWalker.getInstance();

    @Inject(method = "destroyBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Z)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void dominion$protectTerrainFromWeapons(Level level, BlockPos blockPos, boolean dropItem,
                                                           CallbackInfo ci) {
        if (!ServerConfig.POMKOTS_DISABLE_WEAPON_BLOCK_DESTRUCTION.get()) return;
        List<String> callers = DOMINION$STACK_WALKER.walk(stream ->
                stream.limit(20).map(StackWalker.StackFrame::getClassName).toList());
        boolean engineering = callers.stream().anyMatch(name ->
                name.endsWith(".AmagiItem") || name.endsWith(".DaigomaruItem")
                        || name.endsWith(".ShoutouItem") || name.endsWith(".WadaItem"));
        if (engineering) return;
        // Alpha.8 routes weapon, projectile, fixed-mech and boss terrain damage through this
        // helper.  Cancelling the shared helper is deliberately more complete than maintaining
        // a fragile caller allow-list; the four engineering tools above are the only exception.
        ci.cancel();
    }
}
