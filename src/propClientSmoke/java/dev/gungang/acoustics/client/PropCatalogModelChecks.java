package dev.gungang.acoustics.client;
import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.RenderShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class PropCatalogModelChecks {
    private static boolean opened;private static int ticks;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        try{
            if(!opened){
                if(mc.screen==null||mc.getOverlay()!=null)return;
                for(int i=100;i<=139;i++){
                    String item=PropCatalog.variant(PropCatalog.type(String.valueOf(i)),new java.util.Random(1));var state=PropCatalog.state(item);
                    if(state.getRenderShape()!=RenderShape.MODEL)continue;
                    var model=mc.getBlockRenderer().getBlockModel(state);var quads=new java.util.ArrayList<net.minecraft.client.renderer.block.model.BakedQuad>();
                    for(var side:net.minecraft.core.Direction.values())quads.addAll(model.getQuads(state,side,net.minecraft.util.RandomSource.create(42)));
                    quads.addAll(model.getQuads(state,null,net.minecraft.util.RandomSource.create(42)));
                    if(quads.isEmpty())throw new AssertionError("Empty model "+item);
                    for(var quad:quads)if(quad.getSprite().contents().name().getPath().equals("missingno"))throw new AssertionError("Missing texture "+item);
                }
                mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new Catalog());opened=true;
            }else if(++ticks==35){capture("prop139-catalog");mc.setScreen(new Custom());}
            else if(ticks==65){capture("prop139-custom-hud");System.out.println("GUNGANG_PROP_139_CLIENT_PASS models=40 hud=pass");mc.stop();}
        }catch(Throwable error){error.printStackTrace();System.out.println("GUNGANG_PROP_139_CLIENT_FAIL");mc.stop();}
    }
    private static void capture(String name)throws java.io.IOException {var mc=Minecraft.getInstance();var path=java.nio.file.Path.of("../ui-smoke/"+name+".png");java.nio.file.Files.createDirectories(path.getParent());try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}}
    private static final class Catalog extends Screen {
        Catalog(){super(Component.literal("Prop catalog 100-139"));}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xFF18232F);g.drawCenteredString(font,"u139 / PROP CATALOG 100-139",width/2,8,0xFFFFFF);
            int cw=width/8,ch=(height-28)/5;
            for(int i=0;i<40;i++){int x=i%8*cw,y=28+i/8*ch;String id=PropCatalog.variant(PropCatalog.type(String.valueOf(i+100)),new java.util.Random(1));var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(PropCatalog.id(id));
                g.drawString(font,""+(i+100),x+4,y,0x75D9AD,false);
                if(item!=null&&item!=Items.AIR){g.pose().pushPose();g.pose().translate(x+cw/2-12,y+10,0);g.pose().scale(1.5f,1.5f,1);g.renderItem(new ItemStack(item),0,0);g.pose().popPose();}
                else draw(g,PropCatalog.state(id),x+cw/2,y+27,18);
            }
        }
    }
    private static final class Custom extends Screen {
        Custom(){super(Component.literal("Backported blocks and ability HUD"));}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xFF18232F);g.drawCenteredString(font,"u139 / CUSTOM PROPS & ABILITY HUD",width/2,10,0xFFFFFF);
            var blocks=new net.minecraft.world.level.block.Block[]{PropExtraBlocks.MANNEQUIN.get(),PropExtraBlocks.DRIED_GHAST.get(),PropExtraBlocks.WILDFLOWERS.get()};
            for(int i=0;i<3;i++){int x=width*(i+1)/4;g.drawCenteredString(font,blocks[i].getName(),x,36,0xFFFFFF);draw(g,blocks[i].defaultBlockState(),x,122,40);}
            UtilityHud.gauge(g,width/3,height-54,120,12,74,100,"Cave spider 74/100",0);
            UtilityHud.gauge(g,width/3,height-34,120,12,62,100,"Free flight 3.1s",0);
            var row=new PropHuntSnapshot.Row("","","ALPHA","minecraft:spawner",0,0,false,false,0,0,1240,1680,0,0,0,false);PropAbilityHud.drawAmmo(g,row,1240);
        }
    }
    private static void draw(GuiGraphics g,net.minecraft.world.level.block.state.BlockState state,int x,int y,int size){
        var mc=Minecraft.getInstance();g.pose().pushPose();g.pose().translate(x,y,150);g.pose().scale(size,-size,size);g.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(25));g.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(35));g.pose().translate(-.5,0,-.5);mc.getBlockRenderer().renderSingleBlock(state,g.pose(),g.bufferSource(),0xF000F0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);g.flush();g.pose().popPose();
    }
}
