package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.ArrowFragmentEntity;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

public final class ArrowFragmentRenderer extends ArrowRenderer<ArrowFragmentEntity> {
    private static final ResourceLocation TEXTURE=new ResourceLocation("minecraft","textures/entity/projectiles/arrow.png");
    public ArrowFragmentRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(ArrowFragmentEntity arrow){return TEXTURE;}
}
