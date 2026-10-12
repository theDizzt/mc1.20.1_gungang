package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.*;
import java.util.Locale;

/** Camera-facing displays with normal depth testing: illuminated, never visible through walls. */
public final class StationRenderer implements BlockEntityRenderer<StationEntity> {
    private final ItemStack small=potion(0x3399FF),large=potion(0x33EE66);
    public StationRenderer(BlockEntityRendererProvider.Context context) { }
    private static ItemStack potion(int color){var stack=new ItemStack(Items.POTION);stack.getOrCreateTag().putInt("CustomPotionColor",color);return stack;}
    @Override public void render(StationEntity entity,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        if(!(entity.getBlockState().getBlock() instanceof StationBlock block))return;
        if(block instanceof FurnitureBlock furniture&&!furniture.isAnchor(entity.getBlockState()))return;
        var mc=Minecraft.getInstance();
        pose.pushPose();
        double x=.5,z=.5,y=block.healthPack()?1.65:1.35;
        if(block instanceof FurnitureBlock furniture&&block.kind==StationBlock.Kind.ARMORY){
            var offset=furniture.offset(1,entity.getBlockState().getValue(FurnitureBlock.FACING));x+=offset.getX()*.5;z+=offset.getZ()*.5;y=3.35;
        }
        pose.translate(x,y,z);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        if(block.healthPack()&&entity.remaining()==0){
            float size=block.kind==StationBlock.Kind.SMALL?.55f:.85f;
            pose.scale(size,size,size);
            mc.getItemRenderer().renderStatic(block.kind==StationBlock.Kind.SMALL?small:large,ItemDisplayContext.FIXED,
                    LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,pose,buffers,entity.getLevel(),0);
        }else{
            String label=block.healthPack()?String.format(Locale.ROOT,"%.1f",Math.max(0,entity.remaining()-partial)/20.0):switch(block.kind){
                case WAR_TABLE->"War Table";case ARMORY->"무기고";case ALPHA->"ALPHA";case BRAVO->"BRAVO";default->"";
            };
            int color=block.kind==StationBlock.Kind.ALPHA?LobbyClient.options.color("ALPHA"):
                    block.kind==StationBlock.Kind.BRAVO?LobbyClient.options.color("BRAVO"):0xFFFFFFFF;
            pose.scale(-.025f,-.025f,.025f);
            mc.font.drawInBatch(label,-mc.font.width(label)/2f,0,color,true,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,LightTexture.FULL_BRIGHT);
        }
        pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(StationEntity entity){return true;}
    @Override public int getViewDistance(){return 48;}
}
