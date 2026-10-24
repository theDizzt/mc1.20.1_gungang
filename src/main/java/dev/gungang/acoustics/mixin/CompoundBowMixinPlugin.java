package dev.gungang.acoustics.mixin;

import java.util.*;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;

/** Load optional bow mixins only when their target classes exist. */
public final class CompoundBowMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String pkg){}
    @Override public String getRefMapperConfig(){return null;}
    @Override public boolean shouldApplyMixin(String target,String mixin){return true;}
    @Override public void acceptTargets(Set<String> mine,Set<String> others){}
    @Override public List<String> getMixins(){
        try{
            org.spongepowered.asm.service.MixinService.getService().getBytecodeProvider().getClassNode("com.korkuluk.standalonebow.BowArrowEntity");
            return List.of("CompoundBowAimAccessor","CompoundBowHitAccessor","CompoundBowMechanicsMixin","CompoundBowArrowMixin","CompoundBowBlockMixin");
        }catch(ClassNotFoundException absent){return List.of();}
        catch(java.io.IOException error){throw new IllegalStateException("Cannot inspect Tactical Military Bow",error);}
    }
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    @Override public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){}
}
