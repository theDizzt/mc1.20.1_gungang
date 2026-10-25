package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.client.ScannerVision;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remote scan masks still need the outline post-process when no tracked entity glows. */
@Mixin(LevelRenderer.class)
public abstract class ScannerOutlinePassMixin {
    @Shadow private PostChain entityEffect;
    @Unique private boolean gungang$processed;
    @Inject(method="renderLevel",at=@At("HEAD"))
    private void gungang$begin(CallbackInfo ci){gungang$processed=false;ScannerVision.beginFrame();}
    @Redirect(method="renderLevel",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/PostChain;process(F)V"))
    private void gungang$process(PostChain chain,float partial){chain.process(partial);if(chain==entityEffect)gungang$processed=true;}
    @Inject(method="renderLevel",at=@At("RETURN"))
    private void gungang$remoteOutline(CallbackInfo ci){
        if(!gungang$processed&&ScannerVision.remoteDrawn()&&entityEffect!=null){
            var mc=Minecraft.getInstance();entityEffect.process(mc.getFrameTime());mc.getMainRenderTarget().bindWrite(false);
        }
    }
}
