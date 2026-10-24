package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.BotEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

public final class BotRenderer extends HumanoidMobRenderer<BotEntity,HumanoidModel<BotEntity>> {
    private static final ResourceLocation SKIN=new ResourceLocation("minecraft","textures/entity/player/wide/steve.png");
    public BotRenderer(EntityRendererProvider.Context context){super(context,new net.minecraft.client.model.PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER),false),.5f);}
    @Override public ResourceLocation getTextureLocation(BotEntity entity){return texture(entity.skin());}
    static ResourceLocation texture(String value){
        if(value!=null&&value.startsWith(dev.gungang.acoustics.server.BotSkinFiles.PREFIX)){var file=BotSkinCache.texture(value);return file!=null?file:SKIN;}
        var id=value==null?null:ResourceLocation.tryParse(value);
        return id!=null&&net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(id).isPresent()?id:SKIN;
    }
}
