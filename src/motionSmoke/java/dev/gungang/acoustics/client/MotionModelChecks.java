package dev.gungang.acoustics.client;
import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class MotionModelChecks {
    private static int ticks;
    private static boolean opened;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        // This preview has no world: Minecraft does not tick texture animations
        // on the title screen, so advance the atlas as an unpaused world would.
        if(opened)mc.getTextureManager().tick();
        try{
            if(!opened){
                if(mc.screen==null||mc.getOverlay()!=null)return;
                int states=0;
                for(var block:new net.minecraft.world.level.block.Block[]{MotionBlocks.CONVEYOR.get(),MotionBlocks.BLUE.get(),MotionBlocks.LIME.get()})for(var state:block.getStateDefinition().getPossibleStates()){
                    var model=mc.getBlockRenderer().getBlockModel(state);var quads=model.getQuads(state,null,net.minecraft.util.RandomSource.create(42));
                    if(quads.isEmpty())throw new AssertionError("Empty model: "+state);
                    for(var q:quads)if(q.getSprite().contents().name().getPath().equals("missingno"))throw new AssertionError("Missing texture: "+state);
                    if(block==MotionBlocks.CONVEYOR.get()){
                        var top=quads.stream().filter(q->q.getDirection()==net.minecraft.core.Direction.UP).findFirst().orElseThrow();
                        if(top.getSprite().contents().getUniqueFrames().count()!=16)throw new AssertionError("Missing conveyor animation: "+state);
                    }
                    states++;
                }
                System.out.println("GUNGANG_MOTION_MODELS_PASS states="+states);
                mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new Preview());opened=true;
            }else if(++ticks==31){
                var path=java.nio.file.Path.of("../ui-smoke/conveyor-animation-a.png");java.nio.file.Files.createDirectories(path.getParent());
                try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
            }else if(ticks==35){
                var path=java.nio.file.Path.of("../ui-smoke/movement-blocks.png");java.nio.file.Files.createDirectories(path.getParent());
                try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
                ConveyorScreen.open(new GungangNetwork.ConveyorView(net.minecraft.core.BlockPos.ZERO,10));
            }else if(ticks==65){
                var path=java.nio.file.Path.of("../ui-smoke/conveyor-settings.png");
                try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
                System.out.println("GUNGANG_MOTION_CLIENT_PASS");mc.stop();
            }
        }catch(Throwable t){t.printStackTrace();System.out.println("GUNGANG_MOTION_CLIENT_FAIL");mc.stop();}
    }
    private static final class Preview extends Screen {
        Preview(){super(Component.literal("Movement blocks"));}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xFF182330);g.drawCenteredString(font,"ANIMATED CONVEYOR & HARDLIGHT",width/2,12,0xD9F8FF);
            int cx=width/4,cy=height/3;
            for(int group=0;group<4;group++){
                int x=group%2==0?cx:cx*3,y=group<2?cy:cy*2+40;
                g.drawCenteredString(font,new String[]{"Forward / corner","Reverse / corner","Blue / 8 blocks","Lime / 16 blocks"}[group],x,y-55,0xFFFFFF);
                var pose=g.pose();pose.pushPose();pose.translate(x,y,180);pose.scale(36,-36,36);pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(35));pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(45));pose.translate(-1.5,0,-1.5);
                if(group<2){
                    for(int i=0;i<3;i++)draw(g,MotionBlocks.CONVEYOR.get().defaultBlockState().setValue(ConveyorBlock.SHAPE,i==2?RailShape.NORTH_EAST:RailShape.NORTH_SOUTH).setValue(ConveyorBlock.REVERSED,group==1),0,i);
                    for(int i=1;i<3;i++)draw(g,MotionBlocks.CONVEYOR.get().defaultBlockState().setValue(ConveyorBlock.SHAPE,RailShape.EAST_WEST).setValue(ConveyorBlock.REVERSED,group==1),i,2);
                }else for(int dx=0;dx<3;dx++)for(int dz=0;dz<3;dz++)draw(g,(group==2?MotionBlocks.BLUE:MotionBlocks.LIME).get().defaultBlockState().setValue(JumpPadBlock.NORTH,dz>0).setValue(JumpPadBlock.SOUTH,dz<2).setValue(JumpPadBlock.WEST,dx>0).setValue(JumpPadBlock.EAST,dx<2),dx,dz);
                g.flush();pose.popPose();
            }
            g.renderItem(new net.minecraft.world.item.ItemStack(MotionBlocks.WRENCH.get()),width-28,10);
        }
        private void draw(GuiGraphics g,BlockState state,int x,int z){g.pose().pushPose();g.pose().translate(x,0,z);minecraft.getBlockRenderer().renderSingleBlock(state,g.pose(),g.bufferSource(),0xF000F0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);g.pose().popPose();}
    }
}
