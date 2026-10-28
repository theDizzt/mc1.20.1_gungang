package dev.gungang.acoustics.client;

import java.util.*;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.*;
import org.lwjgl.glfw.GLFW;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID,value=Dist.CLIENT)
public final class PropHuntClient {
    private static PropHuntSnapshot state;
    private static CameraType previous;
    private static boolean charging;
    private static int page;
    private static long noticeUntil;
    private static String lastBlock="";
    private static long received;
    static PropHuntSnapshot.Row ability(){return hider()?self():null;}
    static float elapsed(float partial){var l=Minecraft.getInstance().level;return l==null?0:Math.max(0,l.getGameTime()-received+partial);}
    static{
        // Seekers may fire inside safe zones during the match; nobody in the match can click custom NPCs.
        SafeZones.clientArmed=e->{var s=self();var p=Minecraft.getInstance().player;return s!=null&&e==p&&!review()&&!s.team().equals("ALPHA");};
        SafeZones.clientInMatch=()->self()!=null&&!review();
    }
    public static int pages(MatchSnapshot value,int height){return PropHuntHud.pages(value,height);}
    public static int table(GuiGraphics g,MatchSnapshot value,int x,int y,int width,int height,int page){return PropHuntHud.table(g,value,x,y,width,height,page);}
    static int page(){return page;}
    static void clampPage(int pages){page=Math.min(page,pages-1);}
    public static boolean active(){return state!=null&&state.active();}
    public static boolean review(){return active()&&state.phase().equals("REVIEW");}
    private static PropHuntSnapshot.Row self(){var p=Minecraft.getInstance().player;return !active()||p==null?null:state.players().stream().filter(r->r.id().equals(p.getUUID().toString())).findFirst().orElse(null);}
    public static boolean hider(){var s=self();return s!=null&&s.team().equals("ALPHA")&&!s.found()&&!review();}
    public static boolean outline(PropEntity prop){
        if(prop.marker())return true;
        var viewer=self();
        return viewer!=null&&!review()&&viewer.team().equals("ALPHA")&&prop.owner()!=null
                &&state.players().stream().anyMatch(r->r.id().equals(prop.owner().toString())&&r.team().equals("ALPHA"));
    }
    public static boolean fixed(){var s=self();return hider()&&s.fixed();}
    public static boolean lockLook(){return fixed();}
    public static boolean hidden(UUID id){return active()&&state.players().stream().anyMatch(r->r.id().equals(id.toString())&&r.team().equals("ALPHA")&&!r.found());}
    public static PropEntity prop(net.minecraft.world.entity.player.Player player){
        if(!active()||review()||player.level()==null)return null;
        var row=state.players().stream().filter(r->r.id().equals(player.getUUID().toString())&&!r.found()).findFirst().orElse(null);
        return row!=null&&player.level().getEntity(row.entity()) instanceof PropEntity p?p:null;
    }
    public static void receive(String json){
        state=ServerFiles.GSON.fromJson(json,PropHuntSnapshot.class);
        received=Minecraft.getInstance().level==null?0:Minecraft.getInstance().level.getGameTime();
        var s=self();if(s!=null&&!s.block().equals(lastBlock)){lastBlock=s.block();noticeUntil=System.currentTimeMillis()+4500;}
        if(!hider())restore();
        var mc=Minecraft.getInstance();if(mc.level!=null)for(var p:mc.level.players())p.refreshDimensions();
    }
    /** "참나무 판자 [01]" for a drawn block or colour variant. */
    static String disguise(String block){
        String name=PropCatalog.name(block).getString();
        return name+" ["+PropCatalog.number(block)+"]";
    }
    /** Caption above the health bar: the own disguise, or while watching someone their disguise ("술래" for seekers). */
    static String caption(){
        var mc=Minecraft.getInstance();
        UUID watched=watched();
        if(!active()||review()||watched==null||mc.player==null)return null;
        var row=state.players().stream().filter(r->r.id().equals(watched.toString())).findFirst().orElse(null);
        if(row==null)return null;
        if(!row.team().equals("ALPHA"))return watched.equals(mc.player.getUUID())?null:"술래";
        return row.found()||row.block().isEmpty()?null:PropCatalog.name(row.block(),net.minecraft.world.level.block.Block.stateById(row.state())).getString()+" ["+PropCatalog.number(row.block())+"]";
    }
    /** Player behind the camera: a watched player, or the owner when the camera follows a disguise. */
    public static UUID watched(){
        var camera=Minecraft.getInstance().getCameraEntity();
        return camera instanceof net.minecraft.world.entity.player.Player p?p.getUUID():camera instanceof PropEntity prop?prop.owner():null;
    }
    private static void action(String value){LobbyClient.action("prop",value,"");}
    /** Called before vanilla consumes E/T/R/space, so inventory, chat and gun reload cannot steal these controls. */
    public static boolean key(int key,int scan,int action){
        var mc=Minecraft.getInstance();if(!hider()||mc.screen!=null)return false;
        if(key==GLFW.GLFW_KEY_T){if(action==GLFW.GLFW_PRESS)action("lock");return true;}
        if(key==GLFW.GLFW_KEY_R){if(action==GLFW.GLFW_PRESS){if(fixed())action("home");else action("rotate");}return true;}
        if(key==GLFW.GLFW_KEY_E){if(action==GLFW.GLFW_PRESS)action("interact");return true;}
        if(key==GLFW.GLFW_KEY_G){if(action==GLFW.GLFW_PRESS)action("horn");return true;}
        if(mc.options.keyJump.matches(key,scan)){
            if(self().active()>elapsed(0)&&self().block().equals("minecraft:command_block"))return false;
            if(action==GLFW.GLFW_PRESS&&!fixed()){charging=true;action("charge");}
            if(action==GLFW.GLFW_RELEASE&&charging){charging=false;action("jump");}
            return true;
        }
        if(mc.options.keyShift.matches(key,scan)&&action==GLFW.GLFW_PRESS&&!fixed()&&self().active()<=elapsed(0))action("snap");
        return false;
    }
    private static void restore(){var mc=Minecraft.getInstance();if(previous!=null){mc.options.setCameraType(previous);previous=null;}charging=false;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){restore();state=null;lastBlock="";noticeUntil=0;page=0;}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(!hider()){restore();return;}
        if(previous==null)previous=mc.options.getCameraType();
        mc.options.setCameraType(mc.getCameraEntity()==mc.player?CameraType.THIRD_PERSON_BACK:CameraType.FIRST_PERSON);
        if(mc.screen!=null&&charging){charging=false;action("cancelCharge");}
        if(mc.player!=null&&mc.screen==null){
            String label;
            if(System.currentTimeMillis()<noticeUntil)label="변신: "+disguise(self().block());
            else {
                // TNT and gas barrels blast with E (30 s cooldown on the server).
                // Dispensers shoot an arrow with E (1 s cooldown) and turn through six facings with R.
                String block=self().block();boolean blast=block.equals("minecraft:tnt")||block.equals("gungang:gas_barrel"),shoot=block.equals("minecraft:dispenser");
                String use=blast?"폭발":shoot?"화살 발사":"상호작용";
                label=fixed()?"T 고정 해제 · 휠 다른 시점 · R 내 시점 · G 경적"+(blast||shoot?" · E "+use:""):"점프 길게: 높이 충전 · Shift 정렬 · E "+use+" · G 경적 · T 고정 · R "+(shoot?"방향 전환":"90° 회전");
            }
            mc.gui.setOverlayMessage(Component.literal(label),false);
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void scroll(InputEvent.MouseScrollingEvent e){
        var mc=Minecraft.getInstance();
        if(active()&&mc.screen==null&&mc.options.keyPlayerList.isDown()){page=Math.max(0,page+(e.getScrollDelta()<0?1:-1));e.setCanceled(true);return;}
        if(!fixed()||mc.screen!=null||e.getScrollDelta()==0)return;
        e.setCanceled(true);
        action(e.getScrollDelta()>0?"previous":"next");
    }
    @SubscribeEvent public static void movement(MovementInputUpdateEvent e){
        if(!hider())return;var input=e.getInput();if(self().active()<=elapsed(0))input.jumping=false;
        if(fixed()||self().stunned()){input.forwardImpulse=0;input.leftImpulse=0;input.jumping=false;input.shiftKeyDown=false;input.up=false;input.down=false;input.left=false;input.right=false;}
    }
    @SubscribeEvent public static void hide(RenderPlayerEvent.Pre e){if(hidden(e.getEntity().getUUID()))e.setCanceled(true);}
    @SubscribeEvent public static void opening(ScreenEvent.Opening e){if(active()&&(e.getNewScreen() instanceof LoadoutScreen))e.setCanceled(true);}
    public static void draw(GuiGraphics g){if(active())PropHuntHud.draw(g,state);}
    private PropHuntClient(){}
}
