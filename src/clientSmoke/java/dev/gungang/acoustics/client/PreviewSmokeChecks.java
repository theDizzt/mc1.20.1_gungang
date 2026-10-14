package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.item.*;
import java.nio.file.*;
import java.util.List;

/** Opt-in real-renderer screenshots; this class is always excluded from the installable JAR. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class PreviewSmokeChecks {
    private static int stage,ticks;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        try {
            if(stage==0){
                if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;
                if(!OffhandMacro.USE.getCategory().equals(LobbyClient.LOBBY.getCategory())||!OffhandMacro.ATTACK.getCategory().equals(LobbyClient.LOBBY.getCategory()))throw new AssertionError("Offhand keys must share the GunGang category");
                mc.options.guiScale().set(2);mc.resizeDisplay();
                var bow=new Loadouts.Weapon("bow","{id:'minecraft:bow',Count:1b}","훈련용 활","드래그로 실제 아이템 모델을 확인합니다.",new Loadouts.Specs("30","85%","높음","60m","수동"),"사용 키를 누른 뒤 놓아 발사합니다.","활",100d,100d,0d);
                var crossbow=new Loadouts.Weapon("crossbow","{id:'minecraft:crossbow',Count:1b}","석궁","",new Loadouts.Specs("60","70%","보통","80m","수동"),"","석궁",150d,80d,0d);
                var loadouts=new Loadouts(List.of(bow,crossbow),List.of(bow),null);
                mc.setScreen(new LoadoutScreen(new EquipmentSnapshot(true,true,"ALPHA","bow","bow",0,loadouts)));
                PreviewCache.weapon(new GungangNetwork.WeaponPreview("primary:bow",new ItemStack(Items.BOW)));
                stage=1;ticks=0;
            }else if(++ticks==30){
                Path directory=Path.of("../build/ui-smoke");Files.createDirectories(directory);
                try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(directory.resolve(stage==1?"loadout.png":stage==2?"maps.png":stage==3?"teams.png":stage==4?"furniture.png":stage==5?"gadgets.png":stage==6?"shield-pose.png":stage==7?"shield-first-person.png":stage==8?"gimmicks.png":"bomb.png"));}
                if(stage==1){
                    var view=new LobbySnapshot.MapView("01","일리오스","전장 미리보기 테스트",600,"쟁탈전",true,"test.png");
                    var screen=new LobbyScreen(new LobbySnapshot(true,true,false,"tester","01","LOBBY",0,List.of(view),List.of(),TeamOptions.defaults(),"CONTROL"));
                    mc.setScreen(screen);
                    try(var image=new com.mojang.blaze3d.platform.NativeImage(320,180,false)){
                        for(int y=0;y<180;y++)for(int x=0;x<320;x++)image.setPixelRGBA(x,y,0xFF000000|((x*255/320)<<16)|((y*255/180)<<8)|0x30);
                        PreviewCache.map(new GungangNetwork.MapPreview("01",image.asByteArray(),""));
                    }
                    screen.mouseClicked(screen.left()+screen.body()-30,65,0);
                    var generated=directory.resolve("generated-map.png");
                    if(Files.exists(generated))PreviewCache.map(new GungangNetwork.MapPreview("01",Files.readAllBytes(generated),""));
                    stage=2;ticks=0;
                }else if(stage==2){
                    var view=new LobbySnapshot.MapView("preview","사찰 거점","전장 미리보기 테스트",600,"쟁탈전",true,"test.png");
                    mc.setScreen(new LobbyScreen(new LobbySnapshot(true,true,false,"tester","preview","LOBBY",0,List.of(view),List.of(),TeamOptions.defaults(),"CONTROL")));
                    stage=3;ticks=0;
                }else if(stage==3){mc.setScreen(new FurnitureScreen());stage=4;ticks=0;}
                else if(stage==4){mc.setScreen(new GadgetScreen());stage=5;ticks=0;}
                else if(stage==5){mc.setScreen(new ShieldPoseScreen());stage=6;ticks=0;}
                else if(stage==6){mc.setScreen(new ShieldFirstPersonScreen());stage=7;ticks=0;}
                else if(stage==7){mc.setScreen(new GimmickScreen());stage=8;ticks=0;}
                else if(stage==8){mc.setScreen(new BombScreen());stage=9;ticks=0;}
                else {System.out.println("GUNGANG_PREVIEW_SMOKE_PASS");mc.stop();stage=9;}
            }
        }catch(Throwable failure){failure.printStackTrace();System.out.println("GUNGANG_PREVIEW_SMOKE_FAIL");mc.stop();stage=3;}
    }
    private static final class BombScreen extends net.minecraft.client.gui.screens.Screen {
        BombScreen(){super(net.minecraft.network.chat.Component.literal("Bomb mode preview"));}
        @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xFF283440);
            var rules=BombRules.defaults();var view=new BombMatch.View(1,5,"ALPHA","RUNNING",rules.pointA(),rules.pointB(),"A",900,"minecraft:overworld");
            var snapshot=new MatchSnapshot(true,false,"폭탄전","RUNNING",210,true,"",2,1,List.of(),new MatchSnapshot.Capture("",0,false,0,3,"BOMB",1),TeamOptions.defaults(),List.of(),0,"clear","minecraft:overworld",view);
            DeathmatchHud.draw(g,snapshot);
            var items=List.of(GadgetItems.BOMB.get(),GadgetItems.DEFUSER.get());
            for(int i=0;i<items.size();i++){
                int x=width*(i*2+1)/4;
                g.pose().pushPose();g.pose().translate(x-48,height/2-48,0);g.pose().scale(6,6,1);g.renderItem(new ItemStack(items.get(i)),0,0);g.pose().popPose();
                String text=i==0?"98 · 폭탄":"99 · 폭탄 해체기";g.drawString(font,text,x-font.width(text)/2,height/2+65,0xFFFFFFFF);
            }
        }
    }
    private static final class GimmickScreen extends net.minecraft.client.gui.screens.Screen {
        GimmickScreen(){super(net.minecraft.network.chat.Component.literal("Gimmicks"));}
        @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xFF506478);
            var items=new java.util.ArrayList<Item>();GimmickBlocks.GIMMICKS.values().forEach(b->items.add(b.get().asItem()));items.add(GimmickBlocks.PUDDLE_ITEM.get());items.add(GimmickBlocks.ACID_BUCKET.get());
            for(int i=0;i<items.size();i++){int x=15+(i%8)*(width-30)/8,y=15+(i/8)*(height-30)/3;g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(3,3,1);g.renderItem(new ItemStack(items.get(i)),0,0);g.pose().popPose();g.drawString(font,""+i,x,y+53,0xFFFFFFFF);}
        }
    }
    private static final class GadgetScreen extends net.minecraft.client.gui.screens.Screen {
        GadgetScreen(){super(net.minecraft.network.chat.Component.literal("Gadget models"));}
        @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mouseX,int mouseY,float partial) {
            g.fill(0,0,width,height,0xFF172333);
            var items=List.of(GadgetItems.MOLOTOV.get(),GadgetItems.SHIELD.get(),GadgetItems.ORBITAL_STRIKE.get());
            for(int i=0;i<items.size();i++) {
                float x=width*(i+.5f)/3;
                g.drawString(font,new ItemStack(items.get(i)).getHoverName(),(int)x-30,20,0xFFFFFFFF);
                for(int row=0;row<12;row++)for(int col=0;col<8;col++)
                    g.fill((int)x-64+col*16,40+row*16,(int)x-48+col*16,56+row*16,(row+col)%2==0?0xFF405060:0xFF708090);
                g.flush();g.pose().pushPose();g.pose().translate(x,height*.5,150);g.pose().scale(70,-70,70);
                g.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(i==1?165:20));
                com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
                minecraft.getItemRenderer().renderStatic(new ItemStack(items.get(i)),ItemDisplayContext.FIXED,net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,g.pose(),g.bufferSource(),minecraft.level,0);
                g.flush();g.pose().popPose();
                g.pose().pushPose();g.pose().translate(x-24,height-65,0);g.pose().scale(3,3,1);g.renderItem(new ItemStack(items.get(i)),0,0);g.pose().popPose();
            }
            g.flush();
        }
    }
    private static final class ShieldPoseScreen extends net.minecraft.client.gui.screens.Screen {
        ShieldPoseScreen(){super(net.minecraft.network.chat.Component.literal("Shield hand poses"));}
        @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float partial) {
            g.fill(0,0,width,height,0xFF243344);
            g.drawString(font,"Eye-anchored shield: front / rear view",20,15,0xFFFFFFFF);
            for(int i=0;i<2;i++) {
                var pose=g.pose();g.flush();pose.pushPose();pose.translate(width*(i+.5)/2,height*.28,180);pose.scale(100,100,100);
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(8));pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(i==0?150:30));
                var model=new net.minecraft.client.model.PlayerModel<net.minecraft.client.player.AbstractClientPlayer>(minecraft.getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER),false);
                model.young=false;ShieldRendering.poseArms(model);model.leftSleeve.copyFrom(model.leftArm);model.rightSleeve.copyFrom(model.rightArm);
                var texture=new net.minecraft.resources.ResourceLocation("minecraft","textures/entity/player/wide/steve.png");
                var buffers=g.bufferSource();com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
                model.renderToBuffer(pose,buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(texture)),15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,1,1,1,1);
                model.head.translateAndRotate(pose);pose.translate(0,-.25,0);pose.scale(1,-1,-1);
                ShieldRendering.atEye(pose,buffers,new ItemStack(GadgetItems.SHIELD.get()),15728880);
                g.flush();pose.popPose();
            }
        }
    }
    private static final class ShieldFirstPersonScreen extends net.minecraft.client.gui.screens.Screen {
        ShieldFirstPersonScreen(){super(net.minecraft.network.chat.Component.literal("Shield first person"));}
        @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float partial) {
            for(int y=0;y<height;y+=24)for(int x=0;x<width;x+=24)g.fill(x,y,x+24,y+24,((x+y)/24)%2==0?0xFF56758C:0xFFABC4D4);
            g.flush();
            var old=new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
            com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,net.minecraft.client.Minecraft.ON_OSX);
            var view=com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();view.pushPose();view.setIdentity();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().perspective((float)Math.toRadians(70),(float)width/height,.05f,100),com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
            var pose=new com.mojang.blaze3d.vertex.PoseStack();pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
            ShieldRendering.atEye(pose,g.bufferSource(),new ItemStack(GadgetItems.SHIELD.get()),15728880);g.flush();
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(old,com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
            view.popPose();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            g.drawString(font,"First-person shield at 70 FOV: sight line through glass",15,15,0xFFFFFFFF);
            g.fill(width/2-5,height/2,width/2+6,height/2+1,0xFFFF4444);g.fill(width/2,height/2-5,width/2+1,height/2+6,0xFFFF4444);
        }
    }
    private static final class FurnitureScreen extends net.minecraft.client.gui.screens.Screen {
        FurnitureScreen(){super(net.minecraft.network.chat.Component.literal("Furniture models"));}
        @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mouseX,int mouseY,float partial){
            g.fill(0,0,width,height,0xFF172333);
            g.drawString(font,"War Table · 3 x 1 x 3",25,20,0xFFFFFFFF);
            g.drawString(font,"Armory · 1 x 3 x 2",width/2+30,20,0xFFFFFFFF);
            draw(g,(FurnitureBlock)StationBlocks.WAR_TABLE.get(),width*.26f,height*.56f,55);
            draw(g,(FurnitureBlock)StationBlocks.ARMORY.get(),width*.75f,height*.75f,55);
            int i=0;for(var block:List.of(StationBlocks.WAR_TABLE.get(),StationBlocks.ARMORY.get())){
                g.pose().pushPose();g.pose().translate(50+i*width/2,height-80,0);g.pose().scale(4,4,1);g.renderItem(new ItemStack(block),0,0);g.pose().popPose();i++;
            }
            g.flush();
        }
        private void draw(net.minecraft.client.gui.GuiGraphics g,FurnitureBlock block,float x,float y,float size){
            g.flush();g.pose().pushPose();g.pose().translate(x,y,150);g.pose().scale(size,-size,size);
            g.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(25));g.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(135));
            com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
            var facing=net.minecraft.core.Direction.NORTH;
            for(int part=0;part<block.partCount();part++){
                var offset=block.offset(part,facing);g.pose().pushPose();g.pose().translate(offset.getX()-.5,offset.getY(),offset.getZ()-.5);
                var state=block.defaultBlockState().setValue(FurnitureBlock.PART,part).setValue(FurnitureBlock.FACING,facing);
                minecraft.getBlockRenderer().renderSingleBlock(state,g.pose(),g.bufferSource(),net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                g.pose().popPose();
            }
            g.flush();g.pose().popPose();
        }
    }
}
