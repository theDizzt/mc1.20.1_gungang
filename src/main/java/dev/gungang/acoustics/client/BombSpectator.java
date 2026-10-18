package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.MatchSnapshot;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class BombSpectator {
    private static MatchSnapshot state;
    private static CameraType previous;
    public static void receive(MatchSnapshot value){state=value;if(!active())restore();}
    public static boolean active(){
        var p=Minecraft.getInstance().player;
        return p!=null&&p.isSpectator()&&state!=null&&state.active()&&state.bomb()!=null
                &&state.bomb().deadPlayers()!=null&&state.bomb().deadPlayers().contains(p.getUUID().toString());
    }
    public static void scroll(InputEvent.MouseScrollingEvent event){
        var mc=Minecraft.getInstance();
        if(!active()||mc.screen!=null||event.getScrollDelta()==0)return;
        event.setCanceled(true);
        LobbyClient.action("bombSpectate",event.getScrollDelta()>0?"previous":"next","");
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(!active()){restore();return;}
        if(previous==null)previous=mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
    }
    private static void restore(){if(previous!=null){Minecraft.getInstance().options.setCameraType(previous);previous=null;}}
    @SubscribeEvent public static void movement(net.minecraftforge.client.event.MovementInputUpdateEvent event){
        if(!active())return;
        var input=event.getInput();input.shiftKeyDown=false;input.jumping=false;input.forwardImpulse=0;input.leftImpulse=0;
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(!active()||mc.options.hideGui||mc.screen!=null||!HudVisibility.enabled())return;
        var camera=mc.getCameraEntity();var g=event.getGuiGraphics();
        String label=camera!=null&&camera!=mc.player?"관전: "+camera.getDisplayName().getString():"생존 팀원 없음 · 다음 라운드 대기";
        String help="휠 위 / 아래: 팀원 변경";int width=Math.max(mc.font.width(label),mc.font.width(help))+16;
        int x=(g.guiWidth()-width)/2,y=g.guiHeight()-63;
        g.fill(x,y,x+width,y+29,0xC0101720);
        g.drawCenteredString(mc.font,label,g.guiWidth()/2,y+5,0xFFFFFFFF);
        g.drawCenteredString(mc.font,help,g.guiWidth()/2,y+17,0xFFC8B060);
    }
    private BombSpectator(){}
}
