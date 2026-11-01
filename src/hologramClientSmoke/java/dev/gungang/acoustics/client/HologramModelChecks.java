package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.HologramBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class HologramModelChecks {
    private static int ticks;
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.screen==null||mc.getOverlay()!=null)return;
        try{
            if(ticks++==0){
                var model=mc.getBlockRenderer().getBlockModel(HologramBlocks.BLOCK.get().defaultBlockState());
                var quads=model.getQuads(null,null,net.minecraft.util.RandomSource.create());
                if(quads.isEmpty()||quads.stream().anyMatch(q->q.getSprite().contents().name().getPath().equals("missingno")))throw new AssertionError("Missing projector model/texture");
                mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new Preview());
            }
            if(ticks==45){
                var path=java.nio.file.Path.of("../ui-smoke/hologram-projector.png");java.nio.file.Files.createDirectories(path.getParent());
                try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
                var inventory=new net.minecraft.world.entity.player.Inventory(null);
                var menu=new dev.gungang.acoustics.server.HologramMenu(1,inventory);menu.setData(0,150);menu.setData(1,2);menu.getSlot(0).set(new ItemStack(Items.DIAMOND_BLOCK));menu.getSlot(1).set(new ItemStack(Items.DIAMOND_SWORD));
                var settings=new HologramScreen(menu,inventory,Component.literal("홀로그램 영사기"));
                // Preview the real screen without ContainerScreen's world/player lifecycle tick.
                mc.setScreen(new Screen(Component.literal("Hologram menu preview")){
                    @Override protected void init(){settings.init(minecraft,width,height);}
                    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){settings.render(graphics,mouseX,mouseY,partial);}
                });
            }
            if(ticks==75){
                var path=java.nio.file.Path.of("../ui-smoke/hologram-menu.png");
                try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
                System.out.println("GUNGANG_HOLOGRAM_CLIENT_PASS projector block_item flat_item special_renderer tint scan_ring menu");mc.stop();
            }
        }catch(Throwable failure){failure.printStackTrace();System.out.println("GUNGANG_HOLOGRAM_CLIENT_FAIL");mc.stop();}
    }
    private static final class Preview extends Screen {
        private final ItemStack[] items={new ItemStack(Items.DIAMOND_BLOCK),new ItemStack(Items.DIAMOND_SWORD),new ItemStack(Items.CHEST)};
        Preview(){super(Component.literal("Hologram projector"));}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xFF142C3A);g.drawCenteredString(font,"HOLOGRAM PROJECTOR",width/2,16,0x93EFFF);
            for(int i=0;i<3;i++){
                int x=width*(i*2+1)/6,y=height-55;
                g.pose().pushPose();g.pose().translate(x,y,150);g.pose().scale(68,-68,68);g.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(18));g.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(30));g.pose().translate(-.5,0,-.5);
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(HologramBlocks.BLOCK.get().defaultBlockState(),g.pose(),g.bufferSource(),0xF000F0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                HologramRenderer.draw(items[i],ticks+partial-40,g.pose(),g.bufferSource(),null);g.flush();g.pose().popPose();
                g.drawCenteredString(font,new String[]{"3D BLOCK","ITEM MODEL","CHEST MODEL"}[i],x,y+24,0xD0EEFF);
            }
        }
    }
}
