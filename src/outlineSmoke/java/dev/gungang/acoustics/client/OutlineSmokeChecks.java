package dev.gungang.acoustics.client;
import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class OutlineSmokeChecks {
    private static int ticks,stage;
    private static PropEntity hidden,found,barrier;
    private static final UUID H=UUID.randomUUID(),F=UUID.randomUUID(),B=UUID.randomUUID(),S=UUID.randomUUID();
    private static PropEntity prop(UUID owner,String item,double x){
        var mc=Minecraft.getInstance();var p=PropEntity.TYPE.get().create(mc.level);p.setup(owner,item,0);p.setPos(x,100,0);mc.level.putNonPlayerEntity(p.getId(),p);return p;
    }
    private static void snapshot(String team){
        var mc=Minecraft.getInstance();
        var rows=List.of(new PropHuntSnapshot.Row(mc.player.getUUID().toString(),"viewer",team,"",0,-1,false,false,0,0),
            new PropHuntSnapshot.Row(H.toString(),"hidden","ALPHA","minecraft:oak_planks",0,hidden.getId(),false,false,0,0),
            new PropHuntSnapshot.Row(F.toString(),"found","ALPHA","minecraft:oak_planks",0,found.getId(),true,false,0,0),
            new PropHuntSnapshot.Row(B.toString(),"barrier","ALPHA","minecraft:barrier",0,barrier.getId(),false,false,0,0));
        PropHuntClient.receive(ServerFiles.GSON.toJson(new PropHuntSnapshot(true,"HUNT",500,100,rows)));
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void capture(String name)throws Exception{
        try(var image=net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(name+".png"));}
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        mc.options.pauseOnLostFocus=false;
        org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        try{
            if(stage==0){if(mc.screen==null||mc.getOverlay()!=null)return;
                ConnectScreen.startConnecting(mc.screen,mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("127.0.0.1:25568"),new net.minecraft.client.multiplayer.ServerData("outline","127.0.0.1:25568",false),false);stage=1;return;}
            if(mc.player==null||mc.level==null)return;
            if(stage>=2)mc.setScreen(null);
            if(stage==4)ScannerVision.receive(ServerFiles.GSON.toJson(new CombatGadgets.ScanView(List.of(new CombatGadgets.Target(S,0,100,0,0,false,0x3388FF)))));
            if(stage==1){if(++ticks<100)return;hidden=prop(H,"minecraft:oak_planks",-4);found=prop(F,"minecraft:oak_planks",-1);
                var flag=net.minecraft.world.entity.Entity.class.getDeclaredMethod("setSharedFlag",int.class,boolean.class);flag.setAccessible(true);flag.invoke(found,6,true);
                check(found.marker(),"Found fixture flag missing");barrier=prop(B,"minecraft:barrier",2);
                snapshot("ALPHA");mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);mc.setCameraEntity(mc.player);mc.options.hideGui=true;mc.setScreen(null);stage=2;ticks=0;return;}
            if(++ticks<35)return;ticks=0;
            if(stage==2){check(mc.shouldEntityAppearGlowing(hidden)&&mc.shouldEntityAppearGlowing(barrier),"Hider teammate outline missing");check(hidden.getTeamColor()==0xFFFFFF,"Hidden color not white");check(found.getTeamColor()==0xFF3030,"Found color not red");capture("hider");snapshot("BRAVO");stage=3;}
            else if(stage==3){check(!mc.shouldEntityAppearGlowing(hidden)&&!mc.shouldEntityAppearGlowing(barrier),"Hidden hider leaked to seeker");check(mc.shouldEntityAppearGlowing(found),"Found marker hidden");capture("seeker");hidden.discard();found.discard();barrier.discard();
                ScannerVision.receive(ServerFiles.GSON.toJson(new CombatGadgets.ScanView(List.of(new CombatGadgets.Target(S,0,100,0,0,false,0x3388FF)))));stage=4;}
            else if(stage==4){check(ScannerVision.remoteDrawn(),"Untracked scan mask missing");capture("scanner");ScannerVision.receive(ServerFiles.GSON.toJson(new CombatGadgets.ScanView(List.of())));stage=5;}
            else {capture("cleared");System.out.println("GUNGANG_OUTLINE_PASS hider_privacy found_red barrier_invisible remote_scan clear screenshots");mc.stop();stage=6;}
        }catch(Throwable e){e.printStackTrace();System.out.println("GUNGANG_OUTLINE_FAIL");mc.stop();stage=6;}
    }
}
