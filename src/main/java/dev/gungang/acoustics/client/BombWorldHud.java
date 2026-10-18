package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class BombWorldHud {
    private static final ResourceLocation BEAM=new ResourceLocation("minecraft","textures/entity/beacon_beam.png");
    private static BombMatch.View view;
    private static long received;
    public static void receive(MatchSnapshot state){
        view=state!=null&&state.active()?state.bomb():null;
        var level=Minecraft.getInstance().level;received=level==null?0:level.getGameTime();
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        var mc=Minecraft.getInstance();
        if(view==null||mc.level==null||!mc.level.dimension().location().toString().equals(view.dimension())||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();
        for(int i=0;i<2;i++){
            var point=i==0?view.pointA():view.pointB();String site=i==0?"A":"B";
            var area=i==0?view.areaA():view.areaB();
            double x=area==null?point.x():area.centerX()-.5,z=area==null?point.z():area.centerZ()-.5;
            pose.pushPose();pose.translate(x-camera.x,point.y()-camera.y,z-camera.z);
            BeaconRenderer.renderBeaconBeam(pose,buffers,BEAM,event.getPartialTick(),1,mc.level.getGameTime(),0,256,new float[]{1,.45f,.05f},.12f,.18f);
            // A flat one-block X, slightly above the floor to avoid z-fighting.
            var consumer=buffers.getBuffer(RenderType.lightning());var matrix=pose.last().pose();
            for(int diagonal:new int[]{-1,1}){
                float z0=diagonal==1?.08f:.92f,z1=diagonal==1?.92f:.08f;
                consumer.vertex(matrix,.05f,.015f,z0).color(255,115,13,230).endVertex();
                consumer.vertex(matrix,.11f,.015f,z0).color(255,115,13,230).endVertex();
                consumer.vertex(matrix,.95f,.015f,z1).color(255,115,13,230).endVertex();
                consumer.vertex(matrix,.89f,.015f,z1).color(255,115,13,230).endVertex();
            }
            pose.pushPose();pose.translate(.5,1.6,.5);pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());pose.scale(-.025f,-.025f,.025f);
            String text=site;
            if(site.equals(view.plantedSite())&&view.plantedPosition()==null){
                float ticks=Math.max(0,view.fuseTicks()-(mc.level.getGameTime()-received)-event.getPartialTick());
                text=String.format(java.util.Locale.ROOT,"%.1f초",ticks/20f);
            }
            if(!site.equals(view.plantedSite())||view.plantedPosition()==null)
                mc.font.drawInBatch(text,-mc.font.width(text)/2f,0,0xFFFFFFFF,true,pose.last().pose(),buffers,net.minecraft.client.gui.Font.DisplayMode.NORMAL,0x80000000,15728880);
            pose.popPose();pose.popPose();
        }
        if(view.plantedPosition()!=null){
            var p=view.plantedPosition();pose.pushPose();pose.translate(p.x()+.5-camera.x,p.y()+1.6-camera.y,p.z()+.5-camera.z);
            pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());pose.scale(-.025f,-.025f,.025f);
            float ticks=Math.max(0,view.fuseTicks()-(mc.level.getGameTime()-received)-event.getPartialTick());
            String text=String.format(java.util.Locale.ROOT,"%.1f초",ticks/20f);
            mc.font.drawInBatch(text,-mc.font.width(text)/2f,0,0xFFFFFFFF,true,pose.last().pose(),buffers,net.minecraft.client.gui.Font.DisplayMode.NORMAL,0x80000000,15728880);pose.popPose();
        }
        var check=plantCheck();
        if(check!=null&&check.placement()!=null){
            pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
            boolean ok=check.site()!=null;
            net.minecraft.client.renderer.LevelRenderer.renderLineBox(pose,buffers.getBuffer(RenderType.lines()),new net.minecraft.world.phys.AABB(check.placement()).deflate(.02),ok?.25f:1,ok?1:.2f,.3f,1);
            pose.popPose();
        }
        buffers.endBatch();
    }
    /** Where the bomb would go and the accepting site, or why it is refused. */
    private record PlantCheck(net.minecraft.core.BlockPos placement,String site,String reason){}
    /** Living (not yet dead this round) player on the given side, during the running round. */
    private static boolean on(boolean attack){
        var mc=Minecraft.getInstance();var p=mc.player;var state=MatchHud.state();
        if(view==null||p==null||p.isSpectator()||state==null||!"RUNNING".equals(view.phase()))return false;
        String self=p.getUUID().toString();
        if(view.deadPlayers()!=null&&view.deadPlayers().contains(self))return false;
        String side=attack?view.attacker():view.attacker().equals("ALPHA")?"BRAVO":"ALPHA";
        return state.players().stream().anyMatch(r->r.id().equals(self)&&r.team().equals(side));
    }
    private static boolean planter(){return view!=null&&view.plantedSite().isEmpty()&&on(true);}
    private static final int NEAR=3;
    /** Floor block within NEAR blocks of A or B (footprint and plantable height). */
    private static boolean nearSite(int x,int y,int z){
        for(var a:new BombRules.Site[]{view.areaA(),view.areaB()})
            if(a!=null&&new BombRules.Site(a.minX()-NEAR,a.minY()-NEAR,a.minZ()-NEAR,a.maxX()+NEAR,a.maxY()+NEAR,a.maxZ()+NEAR).plantable(x,y,z))return true;
        for(var point:new BombRules.Point[]{view.areaA()==null?view.pointA():null,view.areaB()==null?view.pointB():null})
            if(point!=null&&Math.abs(point.x()-x)<=NEAR&&Math.abs(point.z()-z)<=NEAR&&Math.abs(point.y()-1-y)<=NEAR)return true;
        return false;
    }
    /** Mirrors BombMatch.plantAt (the floor under the feet) so the preview matches the server; silent away from the sites. */
    private static PlantCheck plantCheck(){
        if(!planter())return null;
        var p=Minecraft.getInstance().player;
        if(!nearSite(p.getBlockX(),p.getBlockY()-1,p.getBlockZ()))return null;
        if(!p.onGround())return new PlantCheck(null,null,"바닥에 서세요");
        var place=BombRules.standing(p.level(),p.getOnPos());var placement=place.cell();
        String site=place.ok()?BombRules.siteAt(view.areaA(),view.areaB(),view.pointA(),view.pointB(),placement.getX(),placement.getY()-1,placement.getZ()):null;
        return new PlantCheck(placement,site,!place.ok()?place.reason():site==null?"설치 영역 밖입니다":"");
    }
    /** A living defender aiming at the planted bomb within reach (mirrors BombMatch.aimed). */
    private static boolean defusable(){
        if(view==null||view.plantedPosition()==null||!on(false))return false;
        var mc=Minecraft.getInstance();var hit=mc.player.pick(4.5,1,false);var b=view.plantedPosition();
        return hit instanceof net.minecraft.world.phys.BlockHitResult block&&hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK
                &&block.getBlockPos().equals(new net.minecraft.core.BlockPos(b.x(),b.y(),b.z()));
    }
    private static boolean holding;
    /**
     * E (the inventory key) plants or defuses only where it applies; elsewhere it still opens the inventory or loadout.
     * Called from KeyboardHandler before vanilla; the server times the hold between "down" and "up".
     */
    public static boolean key(int key,int scan,int action){
        var mc=Minecraft.getInstance();
        if(!mc.options.keyInventory.matches(key,scan))return false;
        if(action==org.lwjgl.glfw.GLFW.GLFW_RELEASE){if(!holding)return false;release();return true;}
        if(holding)return true;
        if(action!=org.lwjgl.glfw.GLFW.GLFW_PRESS||mc.screen!=null||(plantCheck()==null&&!defusable()))return false;
        holding=true;LobbyClient.action("bomb","down","");return true;
    }
    private static void release(){holding=false;LobbyClient.action("bomb","up","");}
    @SubscribeEvent public static void holdCheck(TickEvent.ClientTickEvent event){
        // A screen or losing the role mid-hold would swallow the release.
        if(event.phase==TickEvent.Phase.END&&holding&&(Minecraft.getInstance().screen!=null||!on(true)&&!on(false)))release();
    }
    @SubscribeEvent public static void hint(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.screen!=null||mc.options.hideGui||!HudVisibility.enabled())return;
        String text;int color;
        var check=plantCheck();
        if(check!=null){boolean ok=check.site()!=null;text=ok?"설치 가능 · "+check.site()+" 지점 · E 길게 "+BombEquipment.timing.plantSeconds()+"초":"설치 불가 · "+check.reason();color=ok?0xFF7BE07B:0xFFFF6B6B;}
        else if(defusable()){text="폭탄 해체 · E 길게 "+BombEquipment.timing.defuseSeconds()+"초";color=0xFF8CC8FF;}
        else return;
        var g=event.getGuiGraphics();
        int w=mc.font.width(text)+12,x=(g.guiWidth()-w)/2,y=g.guiHeight()/2+14;
        g.fill(x,y,x+w,y+13,DeathmatchHud.SURFACE);g.fill(x,y+12,x+w,y+13,color);
        g.drawString(mc.font,text,x+6,y+3,color,false);
    }
    private static long nextBeep;
    /** Planted bomb beeps faster as the fuse runs out; plain positional sound, so it fades with distance. */
    @SubscribeEvent public static void beep(TickEvent.ClientTickEvent event){
        var mc=Minecraft.getInstance();
        if(event.phase!=TickEvent.Phase.END||view==null||view.plantedPosition()==null||mc.level==null||mc.isPaused()
                ||!mc.level.dimension().location().toString().equals(view.dimension()))return;
        long now=mc.level.getGameTime();if(now<nextBeep&&nextBeep-now<=20)return;
        long left=Math.max(0,view.fuseTicks()-(now-received));if(left<=0)return;
        var p=view.plantedPosition();
        mc.level.playLocalSound(p.x()+.5,p.y()+.5,p.z()+.5,net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BIT.value(),net.minecraft.sounds.SoundSource.BLOCKS,1.5f,left<=100?2f:1.8f,false);
        nextBeep=now+(left>400?20:left>200?12:left>100?7:4);
    }
    private BombWorldHud(){}
}
