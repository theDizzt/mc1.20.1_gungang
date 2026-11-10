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
    /**
     * The snapshot's rows by player id, rebuilt on every snapshot. Lookups run per entity per frame (outlines, push and
     * size checks, the team HUD), so they must not scan the list and format UUIDs each time.
     */
    private static Map<UUID,PropHuntSnapshot.Row> rows=Map.of();
    private static PropHuntSnapshot.Row row(UUID id){return id==null?null:rows.get(id);}
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
        PropBullet.clientSeeker=e->{if(!active()||!state.phase().equals("HUNTING"))return false;var r=row(e.getUUID());return r!=null&&r.team().equals("BRAVO");};
    }
    public static int pages(MatchSnapshot value,int height){return PropHuntHud.pages(value,height);}
    public static int table(GuiGraphics g,MatchSnapshot value,int x,int y,int width,int height,int page){return PropHuntHud.table(g,value,x,y,width,height,page);}
    static int page(){return page;}
    static void clampPage(int pages){page=Math.min(page,pages-1);}
    public static boolean active(){return state!=null&&state.active();}
    public static boolean review(){return active()&&state.phase().equals("REVIEW");}
    private static PropHuntSnapshot.Row self(){var p=Minecraft.getInstance().player;return !active()||p==null?null:row(p.getUUID());}
    /** The living hider's own disguise id ("minecraft:minecart"...), or null. */
    static String disguise(){var s=self();return hider()&&s!=null&&!s.block().isEmpty()?dev.gungang.acoustics.server.PropCatalog.id(s.block()).toString():null;}
    public static boolean hider(){var s=self();return s!=null&&s.team().equals("ALPHA")&&!s.found()&&!review();}
    public static boolean outline(PropEntity prop){
        if(prop.marker())return true;
        var viewer=self();if(viewer==null||review()||!viewer.team().equals("ALPHA"))return false;
        var owner=row(prop.owner());return owner!=null&&owner.team().equals("ALPHA");
    }
    /** The local player is on the seeking team while the round is being played. */
    public static boolean seeker(){var s=self();return s!=null&&s.team().equals("BRAVO")&&!review();}
    public static boolean fixed(){var s=self();return hider()&&s.fixed();}
    /** The wall the own disguise clings to (Q), or null. */
    static net.minecraft.core.Direction clinging(){var s=self();return hider()?s.clinging():null;}
    private static boolean liquid(){var p=Minecraft.getInstance().player;return p!=null&&(p.isInWater()||p.isInLava()||p.isInFluidType());}
    /** Jump charge 0..1 (full after 20 ticks, as the server's jump), or -1 when not charging. */
    static float charge(float partial){
        var level=Minecraft.getInstance().level;if(!charging||!hider()||level==null)return -1;
        return Math.min(1,Math.max(0,level.getGameTime()-chargeStart+partial)/20f);
    }
    public static boolean lockLook(){return fixed()&&!free();}
    /** Locked and flying the free spectator camera (Q): the own body is a spectator that is its own camera. */
    static boolean free(){var mc=Minecraft.getInstance();return fixed()&&mc.player!=null&&mc.player.isSpectator()&&mc.getCameraEntity()==mc.player;}
    /** A hider the seekers have found: out for the rest of the round. */
    static boolean found(UUID id){var r=active()?row(id):null;return r!=null&&r.team().equals("ALPHA")&&r.found();}
    public static boolean hidden(UUID id){var r=active()?row(id):null;return r!=null&&r.team().equals("ALPHA")&&!r.found();}
    public static PropEntity prop(net.minecraft.world.entity.player.Player player){
        if(!active()||review()||player.level()==null)return null;
        var row=row(player.getUUID());
        return row!=null&&!row.found()&&row.entity()>=0&&player.level().getEntity(row.entity()) instanceof PropEntity p?p:null;
    }
    public static void receive(String json){
        state=ServerFiles.GSON.fromJson(json,PropHuntSnapshot.class);
        var index=new HashMap<UUID,PropHuntSnapshot.Row>();
        if(state!=null&&state.players()!=null)for(var r:state.players())try{index.putIfAbsent(UUID.fromString(r.id()),r);}catch(IllegalArgumentException ignored){}
        rows=index;
        received=Minecraft.getInstance().level==null?0:Minecraft.getInstance().level.getGameTime();
        var s=self();if(s!=null&&!s.block().equals(lastBlock)){lastBlock=s.block();noticeUntil=System.currentTimeMillis()+4500;}
        if(!hider())restore();
        var mc=Minecraft.getInstance();if(mc.level!=null)for(var p:mc.level.players())p.refreshDimensions();
    }
    /** A hider's current disguise row (not found, block chosen), or null outside the hunt or for seekers. */
    static PropHuntSnapshot.Row disguiseOf(UUID id){
        if(!active()||review())return null;
        var r=row(id);return r!=null&&r.team().equals("ALPHA")&&!r.found()&&!r.block().isEmpty()?r:null;
    }
    /** "참나무 판자 [01]" for a drawn block or colour variant. */
    static String disguise(String block){
        // An assorted type names the look it drew (e.g. 자작나무 판자), from the own row's state.
        var own=self();var drawn=own!=null&&own.block().equals(block)&&own.state()!=0?net.minecraft.world.level.block.Block.stateById(own.state()):null;
        String name=(drawn==null?PropCatalog.name(block):PropCatalog.name(block,drawn)).getString();
        return name+" ["+PropCatalog.number(block)+"]";
    }
    /** Caption above the health bar: the own disguise, or while watching someone their disguise ("술래" for seekers). */
    static String caption(){
        var mc=Minecraft.getInstance();
        UUID watched=watched();
        if(!active()||review()||watched==null||mc.player==null)return null;
        var row=row(watched);
        if(row==null)return null;
        // Only while spectating someone else: the own disguise is already in the control guide.
        if(watched.equals(mc.player.getUUID()))return null;
        if(!row.team().equals("ALPHA"))return "술래";
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
        // 255 drives itself: none of the disguise keys do anything (the horn still sounds); R comes back from watching others.
        if(auto()&&key==GLFW.GLFW_KEY_R){if(action==GLFW.GLFW_PRESS)action("home");return true;}
        if(auto()&&(key==GLFW.GLFW_KEY_T||key==GLFW.GLFW_KEY_E||key==GLFW.GLFW_KEY_Q||key==GLFW.GLFW_KEY_SPACE||key==GLFW.GLFW_KEY_LEFT_SHIFT))return true;
        if(key==GLFW.GLFW_KEY_T){if(action==GLFW.GLFW_PRESS)action("lock");return true;}
        if(key==GLFW.GLFW_KEY_R){if(action==GLFW.GLFW_PRESS){if(fixed())action("home");else action("rotate");}return true;}
        // 178: E on one's own lectern opens the book to write in (the server only stores the text).
        // 95 / 190: E lets go of the wall or ceiling (the blocks have no other E effect).
        if(key==GLFW.GLFW_KEY_E&&sticky()&&stuck){if(action==GLFW.GLFW_PRESS)letGo=true;return true;}
        if(key==GLFW.GLFW_KEY_E){if(action==GLFW.GLFW_PRESS){var own=mc.player==null?null:prop(mc.player);if(own!=null&&own.item().equals("minecraft:lectern")&&!fixed())PropBook.edit(own);
            // 241: E on one's own easel opens the canvas to paint (the server only stores it).
            else if(own!=null&&own.item().equals("gungang:easel")&&!fixed())EaselRenderer.paint(own.text(),value->dev.gungang.acoustics.server.GungangNetwork.CHANNEL.sendToServer(new dev.gungang.acoustics.server.GungangNetwork.PropText(value)));
            else action("interact");}return true;}
        if(key==GLFW.GLFW_KEY_G){if(action==GLFW.GLFW_PRESS)action("horn");return true;}
        // Locked: Q flies a free spectator camera, and Q again leaves it.
        if(TeamPointHud.POINT.matches(key,scan)&&fixed()){if(action==GLFW.GLFW_PRESS)action("free");return true;}
        // Flying free: movement, jump and sneak are the spectator's own flight.
        if(free())return false;
        // Q (the team point key) hangs the disguise on a wall or lets go; hiders do not mark team points.
        if(TeamPointHud.POINT.matches(key,scan)){if(action==GLFW.GLFW_PRESS){if(charging){charging=false;action("cancelCharge");}action("cling");}return true;}
        if(mc.options.keyJump.matches(key,scan)){
            if(action==GLFW.GLFW_PRESS)jumpHeld=true;else if(action==GLFW.GLFW_RELEASE)jumpHeld=false;
            // 95 / 190 cannot jump (they swim in liquids like everyone else).
            if(sticky()&&!liquid()){charging=false;return true;}
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
    /** 95 / 190: stuck to a wall or ceiling this tick; let go with E until the disguise lands. */
    private static boolean stuck,letGo;
    /** 252: the wall ride in progress, the wall last ridden (no second ride on it until landing or a kick), last tick's jump key. */
    private static dev.gungang.acoustics.server.PropSkate.Ride ride;
    private static net.minecraft.core.Direction rode;
    private static boolean jumpBefore;
    private static boolean skater(){var s=self();return s!=null&&dev.gungang.acoustics.server.PropSkate.skater(s.block());}
    /** 255 autonomous combat unit: the server moves the body; the keys are locked. */
    private static boolean auto(){var s=self();return s!=null&&s.block().equals("gungang:combat_unit");}
    private static boolean sticky(){var s=self();return s!=null&&dev.gungang.acoustics.server.PropSticky.sticky(s.block());}
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
        if((clung||stuck||ride!=null)&&mc.player!=null)mc.player.setNoGravity(false);clung=false;stuck=false;letGo=false;ride=null;rode=null;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){restore();state=null;rows=Map.of();lastBlock="";noticeUntil=0;page=0;}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(!hider()){restore();return;}
        if(previous==null)previous=mc.options.getCameraType();
        mc.options.setCameraType(mc.getCameraEntity()==mc.player&&!free()?CameraType.THIRD_PERSON_BACK:CameraType.FIRST_PERSON);
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
        if(System.currentTimeMillis()<noticeUntil)lines.add("변신: "+disguise(self().block())+(PropInteraction.tagText(self().block()).isEmpty()?"":" "+PropInteraction.tagText(self().block()).trim()+"§r"));
        // TNT and gas barrels blast with E (30 s cooldown on the server).
        // Dispensers shoot an arrow with E (1 s cooldown) and turn through six facings with R.
        String block=self().block();boolean blast=block.equals("minecraft:tnt")||block.equals("gungang:gas_barrel"),shoot=block.equals("minecraft:dispenser")||block.equals("minecraft:dropper");
        boolean hologram=block.equals("gungang:hologram_projector");
        // E only where it does something: turning a block is R's job alone.
        var blockState=net.minecraft.world.level.block.Block.stateById(self().state());
        boolean interactive=PropInteraction.interactive(block,blockState.isAir()?PropCatalog.state(block):blockState);
        String use=blast?"폭발":shoot?(block.equals("minecraft:dropper")?"폭죽 발사":"화살 발사"):switch(block){
            case "minecraft:sculk_sensor","minecraft:sculk_shrieker"->"비명 켜기/끄기";
            case "gungang:hologram_projector"->"홀로그램 크기";
            case "gungang:shutter"->"열기/닫기";
            case "minecraft:note_block"->"연주";
            case "minecraft:cartography_table"->"미니맵 보기";
            case "minecraft:farmland"->"작물 바꾸기";
            case "minecraft:piston","minecraft:sticky_piston"->"피스톤 작동";
            case "gungang:desk_clock"->"알람 켜기/끄기";
            case "gungang:money_cart"->"현금 인출기 소리";
            case "gungang:chameleon_block"->"맞닿은 블록으로 위장";
            case "gungang:capsule_machine"->"팀원 블록 뽑기 (1회)";
            case "gungang:garden_fountain"->"물 분사";
            case "gungang:easel"->"그림 그리기";
            case "minecraft:light"->"밝기 조절";
            case "gungang:dice"->"주사위 던지기";
            case "gungang:chandelier"->"천장에 매달리기/떨어지기";
            case "gungang:slot_machine"->"슬롯 돌리기";
            case "gungang:billiard_ball"->"공 바꾸기";
            case "gungang:wishing_well"->"두레박 내리기/올리기";
            case "gungang:embezzlement_statue"->"돈벼락";
            case "gungang:christmas_tree"->"장식등 켜기/끄기";
            case "gungang:sanga_doll"->"포즈 바꾸기";
            case "minecraft:lightning_rod"->"충전 켜기/끄기";
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
            case "gungang:nozzle"->"대량 분사";
            case "minecraft:conduit"->"번개 폭풍";
            case "minecraft:repeater"->"모양 바꾸기";
            case "minecraft:redstone_torch"->"켜기/끄기";
            case "gungang:mannequin","gungang:eunbi_doll","gungang:hanbi_doll"->"포즈 바꾸기";
            case "gungang:small_health_pack","gungang:large_health_pack","gungang:conveyor_belt"->"바닥에 넣기/꺼내기";
            default->block.endsWith("candle_cake")?"촛불 켜기/끄기":PropInteraction.special(block)?"특수 효과":"상호작용";
        };
        String q=TeamPointHud.POINT.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
        if(free()){
            lines.add("자유 관전 중");lines.add("이동 키·점프·Shift  비행");lines.add(q+"  자유 관전 해제");lines.add("휠  다른 시점");lines.add("R  내 시점");lines.add("T  고정 해제");
        }else if(auto()){
            lines.add("자율 전투 중 · 조작 불가");lines.add("찾는 시간에 술래를 찾아가 미니건 사격");lines.add("휠  다른 시점");lines.add("R  내 시점");lines.add("G  경적");
        }else if(fixed()){
            lines.add("T  고정 해제");lines.add("휠  다른 시점");lines.add(q+"  자유 관전");lines.add("R  내 시점");lines.add("G  경적");
            if(blast||shoot)lines.add("E  "+use);
        }else if(clinging()!=null){
            lines.add("이동 키  벽에서 위치 조정");lines.add(q+"  벽에서 떨어지기");if(interactive)lines.add("E  "+use);lines.add("G  경적");lines.add("T  고정");
            lines.add("벽에서는 점프·정렬 불가");
        }else{
            if(sticky()&&!liquid()){lines.add("벽·천장에 닿으면 붙어서 이동");if(stuck)lines.add("E  떨어지기");lines.add("점프 불가");}
            else if(skater()&&!liquid()){lines.add(ride!=null?"벽 타는 중 · 시선으로 방향":"앞으로 달려 벽에 닿으면 벽 타기");lines.add(ride!=null?"점프  벽 차고 뛰기":"점프 길게  높이 충전");}
            else lines.add(liquid()?"점프  헤엄쳐 오르기":"점프 길게  높이 충전");
            lines.add("Shift  정렬");if(interactive)lines.add("E  "+use);lines.add("G  경적");lines.add("T  고정");
            lines.add("R  "+(shoot||block.endsWith("piston")?"방향 전환":hologram?"회전 속도":block.equals("minecraft:note_block")?"곡 바꾸기":"90° 회전"));if(!state.clingOff())lines.add(q+"  벽에 붙기");
            if(block.equals("minecraft:light"))lines.add("휠  밝기 ±1");
            if(block.equals("gungang:eunbi_doll"))lines.add("좌클릭 (꾹)  광선");
            if(block.equals("gungang:hanbi_doll"))lines.add("좌클릭 (꾹)  투명화·무적");
        }
        return lines;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void scroll(InputEvent.MouseScrollingEvent e){
        var mc=Minecraft.getInstance();
        if(active()&&mc.screen==null&&mc.options.keyPlayerList.isDown()){page=Math.max(0,page+(e.getScrollDelta()<0?1:-1));e.setCanceled(true);return;}
        // 242: the wheel steps the light disguise's brightness instead of the hotbar.
        if(hider()&&!fixed()&&mc.screen==null&&e.getScrollDelta()!=0&&self().block().equals("minecraft:light")){e.setCanceled(true);action(e.getScrollDelta()>0?"brightUp":"brightDown");return;}
        if(!fixed()&&!(hider()&&auto())||mc.screen!=null||e.getScrollDelta()==0)return;
        e.setCanceled(true);
        action(e.getScrollDelta()>0?"previous":"next");
    }
    @SubscribeEvent public static void movement(MovementInputUpdateEvent e){
        if(!hider()||free())return;var input=e.getInput();
        // Jump is a charged leap on land, but swims up in water, lava and other liquids like a normal player.
        input.jumping=jumpHeld&&liquid()&&!fixed()&&clinging()==null;
        var player=e.getEntity();var wall=clinging();
        if(wall!=null&&!fixed()&&!self().stunned()){
            // On a wall: no gravity, the movement keys slide over its face (server re-checks the support every tick).
            player.setDeltaMovement(PropCling.motion(player.level(),player.getBoundingBox(),wall,input.forwardImpulse,input.leftImpulse,player.getYRot()));
            player.setNoGravity(true);clung=true;
            input.forwardImpulse=0;input.leftImpulse=0;input.jumping=false;input.shiftKeyDown=false;input.up=false;input.down=false;input.left=false;input.right=false;
        }else if(clung){clung=false;player.setNoGravity(false);}
        // 95 / 190: stuck to whatever wall or ceiling the body touches (a wall from the ground only when walking into it).
        boolean stick=false;
        if(wall==null&&!fixed()&&!self().stunned()&&sticky()&&!liquid()){
            if(player.onGround())letGo=false;
            if(!letGo){
                var box=player.getBoundingBox();var level=player.level();float yaw=player.getYRot();
                var side=dev.gungang.acoustics.server.PropSticky.wall(level,box,yaw);
                net.minecraft.world.phys.Vec3 motion=null;
                if(side!=null&&(!player.onGround()||input.forwardImpulse>0&&net.minecraft.core.Direction.fromYRot(yaw)==side))
                    motion=dev.gungang.acoustics.server.PropSticky.wallMotion(side,input.forwardImpulse,input.leftImpulse,yaw);
                else if(!player.onGround()&&dev.gungang.acoustics.server.PropSticky.ceiling(level,box))
                    motion=dev.gungang.acoustics.server.PropSticky.ceilingMotion(input.forwardImpulse,input.leftImpulse,yaw);
                if(motion!=null){
                    player.setDeltaMovement(motion);player.setNoGravity(true);stick=true;
                    input.forwardImpulse=0;input.leftImpulse=0;input.jumping=false;input.shiftKeyDown=false;input.up=false;input.down=false;input.left=false;input.right=false;
                }
            }
        }
        if(stick)stuck=true;else if(stuck){stuck=false;player.setNoGravity(false);}
        skate(player,input,wall==null&&!fixed()&&!self().stunned()&&skater()&&!liquid());
        if(sticky()&&!liquid())input.jumping=false;
        if(self().active()>elapsed(0)&&self().block().equals("minecraft:command_block")){input.shiftKeyDown=false;e.getEntity().setDeltaMovement(e.getEntity().getDeltaMovement().multiply(1,0,1));}
        if(fixed()||self().stunned()||auto()){input.forwardImpulse=0;input.leftImpulse=0;input.jumping=false;input.shiftKeyDown=false;input.up=false;input.down=false;input.left=false;input.right=false;}
    }
    /**
     * 252: skating into a wall with forward held carves up it; the ride follows the look along the wall and jump kicks
     * off. The entry speed is the skating pace (the collision has already stopped the motion into the wall).
     */
    private static void skate(net.minecraft.world.entity.player.Player player,net.minecraft.client.player.Input input,boolean allowed){
        boolean jump=jumpHeld&&!jumpBefore;jumpBefore=jumpHeld;
        if(!allowed){if(ride!=null){ride=null;player.setNoGravity(false);}rode=null;return;}
        if(player.onGround())rode=null;
        float yaw=player.getYRot();
        if(ride==null){
            if(input.forwardImpulse<=0)return;
            var side=dev.gungang.acoustics.server.PropSticky.wall(player.level(),player.getBoundingBox(),yaw);
            if(side==null||side==rode)return;
            double r=Math.toRadians(yaw),into=-Math.sin(r)*side.getStepX()+Math.cos(r)*side.getStepZ();
            // From the ground only when skating toward the wall; in the air any wall alongside catches the board.
            if(player.onGround()&&into<.2)return;
            var flat=player.getDeltaMovement().horizontalDistance();
            double pace=Math.max(flat,player.getSpeed()*2.16*Math.min(1,input.forwardImpulse));
            ride=dev.gungang.acoustics.server.PropSkate.start(side,pace,yaw);
            if(ride==null)return;
            rode=side;
        }
        if(jump){
            player.setDeltaMovement(dev.gungang.acoustics.server.PropSkate.kick(ride));
            ride=null;rode=null;player.setNoGravity(false);clearInput(input);return;
        }
        var touching=dev.gungang.acoustics.server.PropSticky.wall(player.level(),player.getBoundingBox(),yaw)==ride.wall;
        var motion=touching?dev.gungang.acoustics.server.PropSkate.step(ride,input.forwardImpulse,yaw):null;
        if(motion==null){ride=null;player.setNoGravity(false);return;}
        player.setDeltaMovement(motion);player.setNoGravity(true);clearInput(input);
    }
    private static void clearInput(net.minecraft.client.player.Input input){
        input.forwardImpulse=0;input.leftImpulse=0;input.jumping=false;input.shiftKeyDown=false;input.up=false;input.down=false;input.left=false;input.right=false;
    }
    @SubscribeEvent public static void hide(RenderPlayerEvent.Pre e){if(hidden(e.getEntity().getUUID()))e.setCanceled(true);}
    @SubscribeEvent public static void opening(ScreenEvent.Opening e){if(active()&&(e.getNewScreen() instanceof LoadoutScreen))e.setCanceled(true);}
    public static void draw(GuiGraphics g){if(active()){PropHuntHud.draw(g,state);if(hider())PropChoiceHud.draw(g,state);}}
    private PropHuntClient(){}
}
