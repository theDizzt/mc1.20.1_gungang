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
    private static boolean charging,jumpHeld,clung;
    private static long chargeStart;
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
        PropBullet.clientSeeker=e->active()&&state.phase().equals("HUNTING")&&state.players().stream()
                .anyMatch(r->r.id().equals(e.getUUID().toString())&&r.team().equals("BRAVO"));
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
    /** The wall the own disguise clings to (Q), or null. */
    static net.minecraft.core.Direction clinging(){var s=self();return hider()?s.clinging():null;}
    private static boolean liquid(){var p=Minecraft.getInstance().player;return p!=null&&(p.isInWater()||p.isInLava()||p.isInFluidType());}
    /** Jump charge 0..1 (full after 20 ticks, as the server's jump), or -1 when not charging. */
    static float charge(float partial){
        var level=Minecraft.getInstance().level;if(!charging||!hider()||level==null)return -1;
        return Math.min(1,Math.max(0,level.getGameTime()-chargeStart+partial)/20f);
    }
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
    /** A hider's current disguise row (not found, block chosen), or null outside the hunt or for seekers. */
    static PropHuntSnapshot.Row disguiseOf(UUID id){
        if(!active()||review())return null;
        return state.players().stream().filter(r->r.id().equals(id.toString())&&r.team().equals("ALPHA")&&!r.found()&&!r.block().isEmpty()).findFirst().orElse(null);
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
        return camera instanceof net.minecraft.world.entity.player.Player||camera instanceof dev.gungang.acoustics.server.BotEntity?camera.getUUID():camera instanceof PropEntity prop?prop.owner():null;
    }
    private static void action(String value){LobbyClient.action("prop",value,"");}
    /** Called before vanilla consumes E/T/R/space, so inventory, chat and gun reload cannot steal these controls. */
    public static boolean key(int key,int scan,int action){
        var mc=Minecraft.getInstance();if(!hider()||mc.screen!=null)return false;
        // While a block choice is open, 1-3 (or the first emote keys) pick a block instead of emoting.
        var offers=state.offers();
        if(offers!=null&&!offers.isEmpty())for(int i=0;i<offers.size();i++)
            if(key==GLFW.GLFW_KEY_1+i||EmoteClient.KEYS[i].matches(key,scan)){if(action==GLFW.GLFW_PRESS)action("pick"+(i+1));return true;}
        // No disguise yet (the first choice is open): nothing else to control.
        if(self().block().isEmpty())return false;
        if(key==GLFW.GLFW_KEY_T){if(action==GLFW.GLFW_PRESS)action("lock");return true;}
        if(key==GLFW.GLFW_KEY_R){if(action==GLFW.GLFW_PRESS){if(fixed())action("home");else action("rotate");}return true;}
        // 178: E on one's own lectern opens the book to write in (the server only stores the text).
        if(key==GLFW.GLFW_KEY_E){if(action==GLFW.GLFW_PRESS){var own=mc.player==null?null:prop(mc.player);if(own!=null&&own.item().equals("minecraft:lectern")&&!fixed())PropBook.edit(own);else action("interact");}return true;}
        if(key==GLFW.GLFW_KEY_G){if(action==GLFW.GLFW_PRESS)action("horn");return true;}
        // Q (the team point key) hangs the disguise on a wall or lets go; hiders do not mark team points.
        if(TeamPointHud.POINT.matches(key,scan)){if(action==GLFW.GLFW_PRESS){if(charging){charging=false;action("cancelCharge");}action("cling");}return true;}
        if(mc.options.keyJump.matches(key,scan)){
            if(action==GLFW.GLFW_PRESS)jumpHeld=true;else if(action==GLFW.GLFW_RELEASE)jumpHeld=false;
            if(self().active()>elapsed(0)&&self().block().equals("minecraft:command_block"))return true;
            // In water, lava or another liquid the jump key swims (see movement); on a wall it does nothing.
            if(action==GLFW.GLFW_PRESS&&!fixed()&&clinging()==null&&!liquid()){
                // A jump pad launches on the press (server side), so no charge gauge there.
                charging=mc.player==null||dev.gungang.acoustics.server.PropSurfacePhysics.pad(mc.player)==null;chargeStart=mc.level==null?0:mc.level.getGameTime();action("charge");}
            if(action==GLFW.GLFW_RELEASE&&charging){charging=false;action("jump");}
            return true;
        }
        if(mc.options.keyShift.matches(key,scan)&&action==GLFW.GLFW_PRESS&&!fixed()&&clinging()==null&&self().active()<=elapsed(0))action("snap");
        return false;
    }
    /** Attack key held for a doll's beam or cloak (184, 191), sent on press and release. */
    private static boolean primary;
    private static boolean doll(){var s=self();return hider()&&(s.block().equals("gungang:eunbi_doll")||s.block().equals("gungang:hanbi_doll"));}
    private static void primary(boolean down){if(primary!=down){primary=down;action(down?"primaryDown":"primaryUp");}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void mouse(InputEvent.MouseButton.Pre e){
        var mc=Minecraft.getInstance();
        if(e.getButton()!=GLFW.GLFW_MOUSE_BUTTON_LEFT||mc.screen!=null||!doll())return;
        if(e.getAction()==GLFW.GLFW_PRESS)primary(true);else if(e.getAction()==GLFW.GLFW_RELEASE)primary(false);
        e.setCanceled(true);
    }
    private static void restore(){
        if(primary&&Minecraft.getInstance().getConnection()!=null)primary(false);
        var mc=Minecraft.getInstance();if(previous!=null){mc.options.setCameraType(previous);previous=null;}charging=false;jumpHeld=false;
        if(clung&&mc.player!=null)mc.player.setNoGravity(false);clung=false;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){restore();state=null;lastBlock="";noticeUntil=0;page=0;}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(!hider()){restore();return;}
        if(previous==null)previous=mc.options.getCameraType();
        mc.options.setCameraType(mc.getCameraEntity()==mc.player?CameraType.THIRD_PERSON_BACK:CameraType.FIRST_PERSON);
        if(mc.screen!=null&&charging){charging=false;action("cancelCharge");}
        if(primary&&(mc.screen!=null||!doll()))primary(false);
        if(mc.screen!=null)jumpHeld=false;
    }
    /**
     * The hider's control guide, drawn by PropHuntHud in the right-side scoreboard area (not the action bar). The
     * first line is the title; a fresh disguise shows as "변신: …" for a few seconds. Empty when not hiding.
     */
    static List<String> guide(){
        var mc=Minecraft.getInstance();if(!hider()||mc.player==null||mc.screen!=null)return List.of();
        var lines=new ArrayList<String>();lines.add("숨는 팀 조작");
        // A block with its own E effect is tagged "특수" in yellow (§e) after its name.
        if(System.currentTimeMillis()<noticeUntil)lines.add("변신: "+disguise(self().block())+(PropInteraction.special(self().block())?" §e특수§r":"")+(PropCatalog.colored(self().block())?" §d색상§r":""));
        // TNT and gas barrels blast with E (30 s cooldown on the server).
        // Dispensers shoot an arrow with E (1 s cooldown) and turn through six facings with R.
        String block=self().block();boolean blast=block.equals("minecraft:tnt")||block.equals("gungang:gas_barrel"),shoot=block.equals("minecraft:dispenser");
        boolean hologram=block.equals("gungang:hologram_projector");
        // E only where it does something: turning a block is R's job alone.
        var blockState=net.minecraft.world.level.block.Block.stateById(self().state());
        boolean interactive=PropInteraction.interactive(block,blockState.isAir()?PropCatalog.state(block):blockState);
        String use=blast?"폭발":shoot?"화살 발사":switch(block){
            case "minecraft:sculk_sensor","minecraft:sculk_shrieker"->"비명 켜기/끄기";
            case "gungang:hologram_projector"->"홀로그램 크기";
            case "gungang:shutter"->"열기/닫기";
            case "minecraft:note_block"->"연주";
            case "minecraft:jukebox"->"CD 발사";
            case "minecraft:brewing_stand"->"팀 물약 효과";
            case "minecraft:bell"->"타종";
            case "minecraft:bubble_column"->"물살 방향";
            case "minecraft:respawn_anchor"->"팀원 불러오기";
            case "minecraft:armor_stand"->"갑옷·포즈 바꾸기";
            case "minecraft:daylight_detector"->"낮/밤 바꾸기";
            case "minecraft:lectern"->"책 쓰기";
            case "minecraft:end_portal_frame"->"엔더의 눈 순간이동";
            case "minecraft:crying_obsidian"->"울기";
            case "gungang:prettiest_block"->"사랑 뿜뿜";
            case "minecraft:tnt_minecart"->"TNT 설치";
            case "gungang:mannequin","gungang:eunbi_doll","gungang:hanbi_doll"->"포즈 바꾸기";
            case "gungang:small_health_pack","gungang:large_health_pack","gungang:conveyor_belt"->"바닥에 넣기/꺼내기";
            default->PropInteraction.special(block)?"특수 효과":"상호작용";
        };
        String q=TeamPointHud.POINT.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
        if(fixed()){
            lines.add("T  고정 해제");lines.add("휠  다른 시점");lines.add("R  내 시점");lines.add("G  경적");
            if(blast||shoot)lines.add("E  "+use);
        }else if(clinging()!=null){
            lines.add("이동 키  벽에서 위치 조정");lines.add(q+"  벽에서 떨어지기");if(interactive)lines.add("E  "+use);lines.add("G  경적");lines.add("T  고정");
            lines.add("벽에서는 점프·정렬 불가");
        }else{
            lines.add(liquid()?"점프  헤엄쳐 오르기":"점프 길게  높이 충전");lines.add("Shift  정렬");if(interactive)lines.add("E  "+use);lines.add("G  경적");lines.add("T  고정");
            lines.add("R  "+(shoot?"방향 전환":hologram?"회전 속도":"90° 회전"));lines.add(q+"  벽에 붙기");
            if(block.equals("gungang:eunbi_doll"))lines.add("좌클릭 (꾹)  광선");
            if(block.equals("gungang:hanbi_doll"))lines.add("좌클릭 (꾹)  투명화·무적");
        }
        return lines;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void scroll(InputEvent.MouseScrollingEvent e){
        var mc=Minecraft.getInstance();
        if(active()&&mc.screen==null&&mc.options.keyPlayerList.isDown()){page=Math.max(0,page+(e.getScrollDelta()<0?1:-1));e.setCanceled(true);return;}
        if(!fixed()||mc.screen!=null||e.getScrollDelta()==0)return;
        e.setCanceled(true);
        action(e.getScrollDelta()>0?"previous":"next");
    }
    @SubscribeEvent public static void movement(MovementInputUpdateEvent e){
        if(!hider())return;var input=e.getInput();
        // Jump is a charged leap on land, but swims up in water, lava and other liquids like a normal player.
        input.jumping=jumpHeld&&liquid()&&!fixed()&&clinging()==null;
        var player=e.getEntity();var wall=clinging();
        if(wall!=null&&!fixed()&&!self().stunned()){
            // On a wall: no gravity, the movement keys slide over its face (server re-checks the support every tick).
            player.setDeltaMovement(PropCling.motion(player.level(),player.getBoundingBox(),wall,input.forwardImpulse,input.leftImpulse,player.getYRot()));
            player.setNoGravity(true);clung=true;
            input.forwardImpulse=0;input.leftImpulse=0;input.jumping=false;input.shiftKeyDown=false;input.up=false;input.down=false;input.left=false;input.right=false;
        }else if(clung){clung=false;player.setNoGravity(false);}
        if(self().active()>elapsed(0)&&self().block().equals("minecraft:command_block")){input.shiftKeyDown=false;e.getEntity().setDeltaMovement(e.getEntity().getDeltaMovement().multiply(1,0,1));}
        if(fixed()||self().stunned()){input.forwardImpulse=0;input.leftImpulse=0;input.jumping=false;input.shiftKeyDown=false;input.up=false;input.down=false;input.left=false;input.right=false;}
    }
    @SubscribeEvent public static void hide(RenderPlayerEvent.Pre e){if(hidden(e.getEntity().getUUID()))e.setCanceled(true);}
    @SubscribeEvent public static void opening(ScreenEvent.Opening e){if(active()&&(e.getNewScreen() instanceof LoadoutScreen))e.setCanceled(true);}
    public static void draw(GuiGraphics g){if(active()){PropHuntHud.draw(g,state);if(hider())PropChoiceHud.draw(g,state);}}
    private PropHuntClient(){}
}
