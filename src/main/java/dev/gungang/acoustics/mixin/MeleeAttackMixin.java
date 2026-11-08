package dev.gungang.acoustics.mixin;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks which lrtactical melee action (LEFT / RIGHT) is dealing damage, for the katana spin settings ({@link dev.gungang.acoustics.server.KatanaSpin}). */
@Pseudo
@Mixin(targets="me.xjqsh.lrtactical.item.MeleeItem",remap=false)
public abstract class MeleeAttackMixin {
    @Inject(method="attack",at=@At("HEAD"),require=0)
    private void gungang$begin(Player player,ItemStack stack,@Coerce Object action,List<Entity> targets,int count,CallbackInfo ci){
        dev.gungang.acoustics.server.KatanaSpin.begin(player,stack,action);
    }
    @Inject(method="attack",at=@At("RETURN"),require=0)
    private void gungang$end(Player player,ItemStack stack,@Coerce Object action,List<Entity> targets,int count,CallbackInfo ci){
        dev.gungang.acoustics.server.KatanaSpin.end();
    }
}
