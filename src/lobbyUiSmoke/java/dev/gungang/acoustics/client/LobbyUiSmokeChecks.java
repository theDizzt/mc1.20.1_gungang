package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import java.util.*;

/** Real renderer checks with overflowing rosters and server-provided mission support. */
@Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID,value=Dist.CLIENT)
public final class LobbyUiSmokeChecks {
    private static int stage,ticks;
    private static LobbyScreen screen;
    private static List<String> labels(){return screen.children().stream().filter(e->e instanceof net.minecraft.client.gui.components.Button).map(e->((net.minecraft.client.gui.components.Button)e).getMessage().getString()).toList();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        try {
            if(stage==0){
                if(mc.screen==null||mc.getOverlay()!=null)return;
                mc.options.guiScale().set(2);mc.resizeDisplay();
                var members=new ArrayList<LobbySnapshot.Member>();
                for(String team:List.of("ALPHA","BRAVO","SPECTATOR"))for(int i=0;i<32;i++)members.add(new LobbySnapshot.Member(UUID.nameUUIDFromBytes((team+i).getBytes()).toString(),team+" player "+String.format("%02d",i),team,i%3==0));
                var map=new LobbySnapshot.MapView("01","금광 호위","임무 아이콘과 긴 명단 스크롤 확인",600,"호위",true,"",false,false,"clear","day","",List.of("ESCORT","DEATHMATCH","PROP_HUNT"));
                screen=new LobbyScreen(new LobbySnapshot(true,true,true,members.get(0).id(),"01","LOBBY",0,List.of(map),members,TeamOptions.defaults(),"ESCORT"));
                mc.setScreen(screen);
                var missions=screen.children().stream().filter(e->e instanceof MissionButton).map(e->(MissionButton)e).toList();
                if(missions.size()!=7||missions.stream().filter(b->b.active).count()!=3||missions.stream().anyMatch(b->b.getWidth()!=b.getHeight()))throw new AssertionError("Mission button support/geometry");
                stage=1;ticks=0;
            }else if(++ticks==20){
                var path=java.nio.file.Path.of("../ui-smoke/lobby-icons-"+stage+".png");java.nio.file.Files.createDirectories(path.getParent());
                try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
                if(stage==1){
                    screen.mouseScrolled(20,75,-1);
                    if(labels().stream().anyMatch(s->s.startsWith("ALPHA player 00"))||labels().stream().noneMatch(s->s.startsWith("ALPHA player 01"))||labels().stream().noneMatch(s->s.startsWith("BRAVO player 00")))throw new AssertionError("Independent roster scrolling");
                    stage=2;ticks=0;
                }else if(stage==2){
                    for(int i=0;i<100;i++)screen.mouseScrolled(20,75,-1);
                    if(labels().stream().noneMatch(s->s.startsWith("ALPHA player 31")))throw new AssertionError("Cannot reach roster end");
                    stage=3;ticks=0;
                }else if(stage==3){
                    screen.resize(mc,320,240);
                    for(var child:screen.children())if(child instanceof net.minecraft.client.gui.components.Button b)
                        if(b.getX()<0||b.getY()<0||b.getX()+b.getWidth()>320||b.getY()+b.getHeight()>240)throw new AssertionError("Widget outside compact viewport");
                    stage=4;ticks=0;
                }else{System.out.println("GUNGANG_LOBBY_UI_PASS missions disabled_modes independent_scroll roster_end compact_layout");mc.stop();}
            }
        }catch(Throwable error){error.printStackTrace();System.out.println("GUNGANG_LOBBY_UI_FAIL");mc.stop();}
    }
}
