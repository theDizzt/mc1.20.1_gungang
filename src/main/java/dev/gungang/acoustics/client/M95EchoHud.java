package dev.gungang.acoustics.client;
import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class M95EchoHud {
    private static List<M95Echo.Target> targets=List.of();
    private static final Set<UUID> highlighted=new HashSet<>();
    private static boolean pending;
    private static long until;
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static final List<Vector2f> screen=new ArrayList<>();
    private static int screenCount;
    private static final Vector2f PROJECTED=new Vector2f();
    private static final Vector4f CLIP=new Vector4f();
    private static final Matrix4f PROJECTION=new Matrix4f();
    public static void receive(List<M95Echo.Target> update){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        world=mc.level;targets=new ArrayList<>(update);pending=true;until=mc.level.getGameTime()+15;
        highlighted.clear();for(var target:targets)highlighted.add(target.id());
    }
    public static Integer color(net.minecraft.world.entity.Entity entity){
        var mc=Minecraft.getInstance();
        if(!highlighted.contains(entity.getUUID())||!visible()||mc.level.getGameTime()>=until)return null;
        return 0x85DBD9;
    }
    private static boolean visible(){
        var mc=Minecraft.getInstance();return mc.level!=null&&mc.level==world&&mc.player!=null&&mc.player.isAlive()&&!mc.player.isSpectator()
                &&mc.screen==null&&mc.options.getCameraType().isFirstPerson()&&M95.is(mc.player.getMainHandItem())&&M95Echo.aiming(mc.player);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){targets=List.of();highlighted.clear();screen.clear();screenCount=0;pending=false;until=0;}
    @SubscribeEvent public static void world(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        screenCount=0;var mc=Minecraft.getInstance();
        if(!visible()||mc.level.getGameTime()>=until){targets=List.of();highlighted.clear();return;}
        if(targets.isEmpty())return;
        var matrix=PROJECTION.set(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose());
        var camera=event.getCamera().getPosition();
        for(var iterator=targets.iterator();iterator.hasNext();){
            var target=iterator.next();
            if(!project(matrix,camera,target.x(),target.y(),target.z())){
                if(pending){iterator.remove();highlighted.remove(target.id());}
                continue;
            }
            if(screenCount==screen.size())screen.add(new Vector2f());
            screen.get(screenCount++).set(PROJECTED);
        }
        pending=false;
    }
    private static boolean project(Matrix4f matrix,net.minecraft.world.phys.Vec3 camera,double x,double y,double z){
        var clip=matrix.transform(CLIP.set((float)(x-camera.x),(float)(y-camera.y),(float)(z-camera.z),1));
        if(clip.w<=0)return false;float nx=clip.x/clip.w,ny=clip.y/clip.w,nz=clip.z/clip.w;
        if(Math.abs(nx)>1||Math.abs(ny)>1||nz< -1||nz>1)return false;
        var window=Minecraft.getInstance().getWindow();
        PROJECTED.set((nx+1)*.5f*window.getGuiScaledWidth(),(1-ny)*.5f*window.getGuiScaledHeight());return true;
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(!visible()||mc.options.hideGui||mc.level.getGameTime()>=until)return;
        var g=event.getGuiGraphics();int color=0xFF85DBD9;
        int labelHalfWidth=mc.font.width("적")/2;
        for(int i=0;i<screenCount;i++){var point=screen.get(i);int x=Math.round(point.x),y=Math.round(point.y);
            g.fill(x-6,y-9,x+7,y-8,color);g.fill(x-6,y+8,x+7,y+9,color);
            g.fill(x-6,y-9,x-5,y+9,color);g.fill(x+6,y-9,x+7,y+9,color);
            // HUD overlay is rendered after the world and remains visible through blocks.
            g.drawString(mc.font,"적",x-labelHalfWidth,y+12,color,true);
        }
    }
    private M95EchoHud(){}
}
