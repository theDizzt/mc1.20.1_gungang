package dev.gungang.acoustics.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Bullets must hit the disguise, never the invisible human body behind a small prop. */
@Mixin(net.minecraft.world.entity.LivingEntity.class)
public abstract class PropTargetMixin {
    @Inject(method="isPickable",at=@At("HEAD"),cancellable=true)
    private void gungang$propTarget(CallbackInfoReturnable<Boolean> ci){
        if((Object)this instanceof net.minecraft.world.entity.player.Player p&&!p.level().isClientSide){
            var match=dev.gungang.acoustics.server.MatchManager.propHunt();
            if(match!=null&&match.hider(p.getUUID()))ci.setReturnValue(false);
        }
    }
    /**
     * Nor does it shove others: solid props block through their real collision shapes, so a prop without one
     * (rose bush, cobweb, pressure plate) lets players walk straight through instead of pushing them off.
     */
    @Inject(method="pushEntities",at=@At("HEAD"),cancellable=true)
    private void gungang$propPushes(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
        if((Object)this instanceof net.minecraft.world.entity.player.Player p&&p.level()!=null&&dev.gungang.acoustics.server.PropDimensions.prop(p)!=null)ci.cancel();
    }
    /** A disguised hider stands like a block: other players cannot shove it (or its prop) around. */
    @Inject(method="isPushable",at=@At("HEAD"),cancellable=true)
    private void gungang$propPushable(CallbackInfoReturnable<Boolean> ci){
        if((Object)this instanceof net.minecraft.world.entity.player.Player p&&p.level()!=null&&dev.gungang.acoustics.server.PropDimensions.prop(p)!=null)ci.setReturnValue(false);
    }
}
