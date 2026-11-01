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
    private static void botSkins()throws Exception {
        var state=MatchHud.class.getDeclaredField("state");state.setAccessible(true);var previous=state.get(null);
        String file="file:ui-smoke.png",id=BotDatabase.id(0).toString();
        try {
            var image=new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<64;y++)for(int x=0;x<64;x++)image.setRGB(x,y,0xFF37CBA1);
            var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
            BotSkinCache.receive(new GungangNetwork.BotSkin(file,bytes.toByteArray()));
            var texture=BotSkinCache.texture(file);
            if(texture==null)throw new AssertionError("Server skin PNG not registered");
            var row=new MatchSnapshot.Row(id,"Skin bot","ALPHA",0,0,0,0,0,0,0,0,0,file);
            state.set(null,new MatchSnapshot(true,false,"Skin check","RUNNING",60,false,"",0,0,List.of(row)));
            if(!texture.equals(MatchHud.skin(id)))throw new AssertionError("Live prop board lost the bot skin without a loaded entity/PlayerInfo");
            state.set(null,new MatchSnapshot(false,true,"Skin check","FINISHED",60,false,"ALPHA",0,0,List.of(row)));
            if(!texture.equals(MatchHud.skin(id)))throw new AssertionError("Result board lost the removed bot's skin");
            if(!texture.equals(BotRenderer.texture(TeamHud.botSkin(Minecraft.getInstance(),BotDatabase.id(0)))))throw new AssertionError("Team HUD disagrees with scoreboard skin");
            System.out.println("BOT_SKIN_HUD_PASS png_upload live_board removed_bot_result team_hud");
        }finally{state.set(null,previous);BotSkinCache.receive(new GungangNetwork.BotSkin(file,new byte[0]));}
    }
    private static List<String> labels(){return screen.children().stream().filter(e->e instanceof net.minecraft.client.gui.components.Button).map(e->((net.minecraft.client.gui.components.Button)e).getMessage().getString()).toList();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        try {
            if(stage==0){
                if(mc.screen==null||mc.getOverlay()!=null)return;
                botSkins();
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
                }else if(stage==4){
                    var state=new LobbySnapshot(true,true,true,"test","01","LOBBY",0,List.of(),List.of(),TeamOptions.defaults(),"PROP_HUNT");
                    mc.setScreen(new GameOptionsScreen(state));stage=5;ticks=0;
                }else if(stage==5){
                    // Exercise real input dispatch, not just the layout: rebuilding while a focused
                    // EditBox/button handles a click or key must not invalidate the event traversal.
                    for(int i=0;i<12;i++){
                        var toggle=mc.screen.children().stream().filter(e->e instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().startsWith("중복 주무기")).map(e->(net.minecraft.client.gui.components.Button)e).findFirst().orElseThrow();
                        mc.screen.mouseClicked(toggle.getX()+3,toggle.getY()+3,0);
                        mc.screen.mouseReleased(toggle.getX()+3,toggle.getY()+3,0);
                    }
                    var fields=mc.screen.children().stream().filter(e->e instanceof net.minecraft.client.gui.components.EditBox).map(e->(net.minecraft.client.gui.components.EditBox)e).toList();
                    fields.get(0).setValue("1441");
                    var apply=mc.screen.children().stream().filter(e->e instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals("적용")).map(e->(net.minecraft.client.gui.components.Button)e).findFirst().orElseThrow();
                    mc.screen.mouseClicked(apply.getX()+3,apply.getY()+3,0);
                    if(!(mc.screen instanceof GameOptionsScreen))throw new AssertionError("Invalid input closed options");
                    mc.screen.resize(mc,320,240);
                    for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget widget)
                        if(widget.getX()<0||widget.getY()<0||widget.getX()+widget.getWidth()>320||widget.getY()+widget.getHeight()>240)throw new AssertionError("Options widget outside compact viewport");
                    stage=6;ticks=0;
                }else{
                    // A disconnect can arrive between opening options and pressing Apply.
                    var field=mc.screen.children().stream().filter(e->e instanceof net.minecraft.client.gui.components.EditBox).map(e->(net.minecraft.client.gui.components.EditBox)e).findFirst().orElseThrow();
                    field.setValue("10");
                    var apply=mc.screen.children().stream().filter(e->e instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals("적용")).map(e->(net.minecraft.client.gui.components.Button)e).findFirst().orElseThrow();
                    mc.screen.mouseClicked(apply.getX()+3,apply.getY()+3,0);
                    if(!(mc.screen instanceof GameOptionsScreen))throw new AssertionError("Disconnected apply discarded the draft");
                    var previous=mc.screen;
                    LobbyClient.receive("{\"options\":{\"game\":{\"minutes\":1441}}}");
                    LobbyClient.receive("null");
                    if(mc.screen!=previous||!field.getValue().equals("10"))throw new AssertionError("Bad snapshot replaced screen or input");
                    ((GameOptionsScreen)mc.screen).receiveError(null);
                    mc.screen.onClose();
                    // With no world loaded Minecraft substitutes its title screen for null.
                    if(mc.screen instanceof LobbyScreen||mc.screen instanceof GameOptionsScreen)throw new AssertionError("Disconnected close reopened lobby");
                    System.out.println("GUNGANG_LOBBY_UI_PASS missions disabled_modes independent_scroll roster_end compact_layout game_options disconnected_apply");mc.stop();
                }
            }
        }catch(Throwable error){error.printStackTrace();System.out.println("GUNGANG_LOBBY_UI_FAIL");mc.stop();}
    }
}
