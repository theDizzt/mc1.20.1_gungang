package dev.gungang.acoustics.mixin;

import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * TaCZ guns never break blocks. TaCZ Tweaks' bullet/melee interactions destroy (or replace) the block they hit, and for
 * a non-player shooter (bots) they skip the Forge break event, so the destroy and the replacement themselves are
 * dropped here. Hit sounds, particles and piercing stay as they are.
 */
@Pseudo
@Mixin(targets={"me.muksc.tacztweaks.data.manager.BulletInteractionManager","me.muksc.tacztweaks.data.manager.MeleeInteractionManager"},remap=false)
public abstract class TaczNoBlockBreakMixin {
    @Redirect(method="handleBlockInteraction",at=@At(value="INVOKE",remap=true,
            target="Lnet/minecraft/server/level/ServerLevel;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"),require=0)
    private boolean gungang$noDestroy(ServerLevel level,BlockPos pos,boolean drop,Entity breaker){return false;}

    @Redirect(method="handleBlockInteraction",at=@At(value="INVOKE",remap=true,
            target="Lnet/minecraft/commands/arguments/blocks/BlockInput;place(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;I)Z"),require=0)
    private boolean gungang$noReplace(BlockInput input,ServerLevel level,BlockPos pos,int flags){return false;}
}
