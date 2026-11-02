package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.PropCatalog;
import dev.gungang.acoustics.server.PropInteraction;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Gallery opened by /gungang prop list (a client command, so it works without a server round trip): every disguise as an icon grid like the emblem gallery. Hovering a cell shows
 * its number, name, tags and what the block does; the wheel scrolls when the grid is taller than the screen.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class PropListScreen extends Screen {
    /** Opened on the next tick: the command runs while the chat is still open, and the chat closes right after. */
    private static boolean pending;
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void commands(net.minecraftforge.client.event.RegisterClientCommandsEvent event){
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("gungang").then(net.minecraft.commands.Commands.literal("prop")
                .then(net.minecraft.commands.Commands.literal("list").executes(c->{
                    if(MatchHud.active()){c.getSource().sendFailure(Component.literal("경기 준비·진행 중에는 변신 블록 목록을 사용할 수 없습니다."));return 0;}
                    pending=true;return 1;
                }))));
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||!pending)return;
        var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)return;
        pending=false;if(mc.player!=null&&!MatchHud.active())open();
    }
    private record Entry(String type,String block,BlockState state,String number,Component name,boolean special,boolean colored,String description){}
    private final List<Entry> entries=new ArrayList<>();
    private int scroll;
    private Entry selected;
    private net.minecraft.client.gui.components.Button morph,unmorph;
    public PropListScreen(){
        super(Component.literal("변신 블록 목록"));
        for(String type:PropCatalog.available()){
            String block=PropCatalog.display(type);
            var state=PropCatalog.state(block);
            entries.add(new Entry(type,block,state,PropCatalog.number(block),PropCatalog.name(block),PropInteraction.special(block),type.startsWith("@")||type.startsWith("#"),describe(type,block,state)));
        }
    }
    public static void open(){
        if(MatchHud.active()){var p=Minecraft.getInstance().player;if(p!=null)p.displayClientMessage(Component.literal("경기 준비·진행 중에는 변신 블록 목록을 사용할 수 없습니다."),true);return;}
        Minecraft.getInstance().setScreen(new PropListScreen());
    }
    private static final int BAR=30;
    @Override protected void init(){
        int w=90,gap=6,y=height-BAR+5;
        morph=addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("변신"),b->{
            if(selected==null)return;LobbyClient.action("propMorph",selected.number,"");onClose();
        }).bounds(width/2-w*3/2-gap,y,w,20).build());
        unmorph=addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("변신 해제"),b->{
            LobbyClient.action("propMorph","off","");onClose();
        }).bounds(width/2-w/2,y,w,20).build());
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("닫기"),b->onClose()).bounds(width/2+w/2+gap,y,w,20).build());
        refreshButtons();
    }
    private void refreshButtons(){
        if(morph==null)return;
        boolean connected=LobbyClient.connected();
        morph.active=selected!=null&&connected;
        morph.setMessage(Component.literal(selected==null?"블록을 선택하세요":"변신: ["+selected.number+"]"));
        unmorph.active=connected&&PropHuntClient.active();
    }
    @Override public void tick(){refreshButtons();}
    /** The cell under the mouse, or null. */
    private Entry at(double mouseX,double mouseY){
        int cell=cell(),columns=columns(),left=(width-cell*columns)/2,top=top();
        if(mouseX<left||mouseX>=left+cell*columns||mouseY<top)return null;
        int row=(int)((mouseY-top)/cell);if(row>=visibleRows())return null;
        int index=(scroll+row)*columns+(int)((mouseX-left)/cell);
        return index<entries.size()?entries.get(index):null;
    }
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button){
        if(super.mouseClicked(mouseX,mouseY,button))return true;
        var e=at(mouseX,mouseY);if(e==null)return false;
        selected=e;refreshButtons();
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK,1));
        return true;
    }

    private int cell(){return Math.max(18,Math.min(26,(width-32)/12));}
    private int columns(){return Math.max(1,Math.min(16,(width-32)/cell()));}
    private int top(){return 40;}
    private int visibleRows(){return Math.max(1,(height-top()-BAR-4)/cell());}
    private int maxScroll(){return Math.max(0,(entries.size()+columns()-1)/columns()-visibleRows());}

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        renderBackground(g);
        scroll=Math.min(scroll,maxScroll());
        int cell=cell(),columns=columns(),left=(width-cell*columns)/2,top=top();
        g.drawCenteredString(font,Component.literal("변신 블록 목록 ("+entries.size()+"종)"),width/2,top-28,0xFFFFFFFF);
        g.drawCenteredString(font,Component.literal("§e■ 특수§7 E 고유 능력  §d■ 색상§7 무작위 색  §8· 클릭해 선택 후 변신"+(maxScroll()>0?" · 휠로 스크롤":"")),width/2,top-15,0xFFB0B0B0);
        Entry hovered=null;
        int first=scroll*columns,last=Math.min(entries.size(),first+visibleRows()*columns);
        for(int i=first;i<last;i++){
            var e=entries.get(i);int index=i-first;
            int x=left+(index%columns)*cell,y=top+(index/columns)*cell;
            boolean over=mouseX>=x&&mouseX<x+cell&&mouseY>=y&&mouseY<y+cell;
            g.fill(x+1,y+1,x+cell-1,y+cell-1,over?0x60FFFFFF:0x30000000);
            if(e==selected)g.fill(x+1,y+1,x+cell-1,y+cell-1,0x7070CDBF);
            if(e==selected)g.renderOutline(x,y,cell,cell,0xFF70CDBF);
            else if(e.special)g.renderOutline(x+1,y+1,cell-2,cell-2,0xFFFFD84A);
            else if(e.colored)g.renderOutline(x+1,y+1,cell-2,cell-2,0xFFFF8AD0);
            int size=cell-6;
            PropAbilityHud.icon(g,e.block,e.state,x+3,y+2,size);
            g.pose().pushPose();g.pose().translate(0,0,400);
            float s=.5f;g.pose().scale(s,s,1);
            String n=e.number;g.drawString(font,n,(int)((x+cell-2)/s)-font.width(n),(int)((y+cell-2)/s)-8,0xFFFFFFFF,true);
            g.pose().popPose();
            if(over)hovered=e;
        }
        if(maxScroll()>0){
            int trackTop=top,trackH=visibleRows()*cell,x=left+columns*cell+4;
            g.fill(x,trackTop,x+2,trackTop+trackH,0x40FFFFFF);
            int barH=Math.max(8,trackH*visibleRows()/(maxScroll()+visibleRows()));
            int barY=trackTop+(trackH-barH)*scroll/Math.max(1,maxScroll());
            g.fill(x,barY,x+2,barY+barH,0xFFD0D0D0);
        }
        super.render(g,mouseX,mouseY,partialTick);
        if(hovered!=null)g.renderTooltip(font,tooltip(hovered),mouseX,mouseY);
    }
    private List<FormattedCharSequence> tooltip(Entry e){
        var lines=new ArrayList<FormattedCharSequence>();
        var head=Component.literal("["+e.number+"] ").withStyle(net.minecraft.ChatFormatting.AQUA).append(e.name.copy().withStyle(net.minecraft.ChatFormatting.WHITE));
        if(e.special)head.append(Component.literal(" 특수").withStyle(net.minecraft.ChatFormatting.YELLOW));
        if(e.colored)head.append(Component.literal(" 색상").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
        lines.add(head.getVisualOrderText());
        int wrap=Math.max(120,Math.min(260,width/2));
        for(String part:e.description.split("\n"))lines.addAll(font.split(Component.literal(part).withStyle(net.minecraft.ChatFormatting.GRAY),wrap));
        return lines;
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double delta){
        scroll=Math.max(0,Math.min(maxScroll(),scroll+(delta<0?1:-1)));return true;
    }
    @Override public boolean isPauseScreen(){return false;}

    /** What a disguise does: its own entry, or else what E does generically (state switch, menu) or "일반 블록". */
    private static String describe(String type,String block,BlockState state){
        String text=DESCRIPTIONS.get(type.startsWith("@")||type.startsWith("#")?type:PropCatalog.id(type).toString());
        if(text!=null)return text;
        if(PropInteraction.interactive(block,state))return "E: 블록 상태 전환(열기·켜기·모양 등) 또는 제작·보관 화면";
        return "일반 블록";
    }
    private static final Map<String,String> DESCRIPTIONS=new HashMap<>();
    private static void d(String id,String text){DESCRIPTIONS.put(id.startsWith("@")||id.startsWith("#")||id.contains(":")?id:"minecraft:"+id,text);}
    static{
        d("@banner","16색 중 무작위");d("@bed","16색 중 무작위. E: 눕기 상태 전환");d("@carpet","16색 중 무작위. 낮은 블록");d("@concrete","16색 중 무작위");
        d("@stained_glass","16색 중 무작위");d("@wool","16색 중 무작위");d("@terracotta","16색 중 무작위");d("@glazed_terracotta","16색 중 무작위. R: 회전");
        d("@candle","16색 중 무작위. E: 불 켜기/끄기");d("@shulker_box","16색 중 무작위. E: 뚜껑이 열리며 27칸 보관함, 닫으면 뚜껑이 닫힘");
        d("#coral","관·뇌·거품·불·뿔 산호 중 무작위. 물 없이 마른 상태로 서 있음");d("#coral_block","다섯 산호 블록 중 무작위");
        d("tnt","E: 직접 폭발(반경 4블록 술래에게 피해 120, 30초 쿨타임). 터져도 변신 유지\n발견되면 그 자리에서 폭발");
        d("gungang:gas_barrel","E: 직접 폭발(술래에게 피해 120, 30초 쿨타임). 터져도 변신 유지\n총에 맞거나 발견되면 폭발");
        d("dispenser","E: 바라보는 면에서 화살 발사(피해 25, 1초 쿨타임)\nR: 여섯 방향 전환");
        d("gungang:small_health_pack","E: 바닥 아래로 1블록 넣기/꺼내기. 유리병 홀로그램은 바닥 위에 남음");
        d("gungang:large_health_pack","E: 바닥 아래로 1블록 넣기/꺼내기. 유리병 홀로그램은 바닥 위에 남음");
        d("cactus","닿은 술래에게 피해 50(1초 간격)");d("campfire","켜져 있으면 닿은 술래에게 피해 50(1초 간격). E: 불 켜기/끄기");
        d("soul_campfire","켜져 있으면 닿은 술래에게 피해 50(1초 간격). E: 불 켜기/끄기");d("magma_block","닿은 술래에게 피해 50(1초 간격)");
        d("pointed_dripstone","닿은 술래에게 피해 50(1초 간격)");d("stonecutter","닿은 술래에게 피해 50(1초 간격). E: 제작 화면");
        d("gungang:acid_puddle","닿은 술래에게 피해 50과 독(1초 간격)");d("gungang:puddle","낮은 웅덩이 블록");
        d("gungang:nozzle","물 분사 기둥. 술래의 불만 끔. 물·웅덩이 칸에 겹쳐 숨을 수 있음");
        d("gungang:acid_nozzle","산성 분사 기둥 안의 술래에게 1초마다 피해 50과 독. 산성액·웅덩이 칸에 겹쳐 숨을 수 있음");
        d("gungang:uxo","술래가 밟으면 폭발(반경 3블록, 피해 120). 변신은 유지, 5초 뒤 다시 터질 수 있음");
        d("barrier","본체가 투명. 숨는 팀에게는 흰 테두리만 보임");d("cobweb","닿은 술래를 느리게 함. 다른 플레이어가 지나갈 수 있음");
        d("obsidian","체력 255");d("bedrock","체력 999");d("minecraft:painting","몸이 액자 두께만큼 얇아 벽에 바로 붙을 수 있음");
        d("chest","E: 27칸 보관함(닫으면 뚜껑이 닫힘)");d("barrel","E: 27칸 보관함");
        d("gungang:war_table","E: 로비 화면(경기 규칙에 따라 제한). R: 회전");d("gungang:armory","E: 무기 화면(경기 규칙에 따라 제한). R: 회전");
        d("rose_bush","충돌 판정이 없어 다른 플레이어가 지나갈 수 있음");
        d("spawner","E: 술래 근처에 독거미 소환(체력 100, 피해 16, 독 5초, 84초 쿨타임). 독거미 체력은 보조 체력바");
        d("anvil","E: 모루·금 간 모루·손상된 모루 전환. R: 회전");d("hay_block","R: 축 회전");
        d("flower_pot","처음 식물 무작위(빈 화분 포함). E: 다시 선택. 심긴 식물까지 피격 판정");
        d("fire","닿은 술래에게 피해 50, 화상 10초(1초 간격)");d("soul_fire","닿은 술래에게 피해 50, 화상 10초(1초 간격). 영혼 불 외관");
        d("nether_portal","E: 닿은 참가자를 팀 구분 없이 맵 안 임의 위치로 이동(안전 위치 보장 없음)");
        d("ice","위에 선 개체가 미끄러짐");d("packed_ice","위에 선 개체가 미끄러짐");
        d("bee_nest","무적 꿀벌 3마리가 주변을 날고, 찾는 시간에 반경 16블록 안 술래를 자동 공격(피해 36)");
        d("oak_sign","시선에 따라 회전. E: 닉네임·이모티콘·자신 또는 팀원 좌표 중 임의 문구");
        d("infested_stone","벌레 먹은 블록 중 외관 무작위. 발견되면 좀벌레 3마리(체력 50, 피해 25)");
        d("lodestone","E: 반경 24블록 안 살아 있는 변신 플레이어를 자신 쪽으로 끌어당김(고정 해제)");
        d("gungang:blue_jump_pad","E: 바닥에 묻기/꺼내기. 표면에서 점프하면 8블록");d("gungang:lime_jump_pad","E: 바닥에 묻기/꺼내기. 표면에서 점프하면 16블록");
        d("rail","R: 방향 회전");d("hopper","R: 방향 회전");
        d("minecraft:minecart","시선 방향으로 회전, 이동속도 2.5배. 이동 중 술래와 부딪히면 피해 50·넉백, 자신은 1초 기절");
        d("gungang:conveyor_belt","E: 바닥에 넣기/꺼내기(반 칸). R: 경로 회전");
        d("command_block","E: 사용한 높이에서 5초 수평 비행(30초 쿨타임). 남은 시간은 보조 체력바");
        d("dragon_egg","피해를 받고 살아 있으면 반경 8블록 안 빈 공간으로 이동");
        d("gungang:mannequin","체력 255, 술래 한 명의 스킨. E: 6가지 포즈");d("redstone_lamp","E: 켜기/끄기");
        d("gungang:dried_ghast","부화 직전의 젖은 모습(부화하지 않음)");d("gungang:wildflowers","네 군집이 놓인 모습");d("pink_petals","꽃잎 4개 모습");
        d("torchflower","닿은 술래에게 화상 10초");d("pitcher_plant","다 자란 두 블록 형태");
        d("sugar_cane","60초마다 한 블록 성장(최대 3블록). 몸 크기도 함께 커짐");
        d("beacon","E: 빔 켜기/끄기. 하나 이상 켜져 있으면 살아 있는 숨는 팀 전체 이동속도 1.2배(중첩 없음)");
        d("minecraft:end_crystal","E: 24블록 안 가장 가까운 술래를 추적하는 입자 발사(명중 피해 50, 30초 쿨타임). 벽에 막힘");
        d("sculk_sensor","E: 비명 효과 켜기/끄기. 켜진 동안 2초마다 딸깍 소리·입자(상태 효과 없음)");
        d("gungang:hologram_projector","주무기 중 무작위 무기를 홀로그램으로 표시\nE: 크기 50~300%, R: 회전 속도");
        d("sculk_shrieker","E: 비명 효과 켜기/끄기. 켜진 동안 2초마다 비명 소리와 입자(어둠 효과 없음)");
        d("gungang:geyser","3×3 바닥 모습. 찾는 시간에 술래가 3블록 안에 오면 높이 8블록 물기둥 3초 분출(화상 10초)");
        d("gungang:hot_spring_nozzle","바라보는 방향으로 약 3블록 물줄기. 찾는 시간에 닿은 숨는 팀 치유. R: 방향 전환");
        d("gungang:bath_toad","치유 물줄기(온천수 노즐과 같음). R: 방향 전환");
        d("gungang:shutter","E: 말려 올라가며 열리고 내려오며 닫힘. R: 축 회전");
        d("sea_pickle","E: 1~4개 전환, 개수에 따라 밝기 증가");d("lightning_rod","E: 충전(빛남) 켜기/끄기, 켜진 동안 전기 불꽃 입자");
        d("crimson_hyphae","R: 축 회전");
        d("note_block","E: 모차르트 피아노 소나타 16번 도입부를 한 음씩 연주");
        d("jukebox","E: 무작위 음반을 위로 발사(3초 뒤 사라짐, 1초 쿨타임)");d("turtle_egg","E: 알 1~4개 전환. 밟아도 깨지지 않음");
        d("brewing_stand","E: 점프 강화·신속·투명화 중 하나를 숨는 팀 전원에게 15초(50초 쿨타임). 투명화 중 블록도 보이지 않음");
        d("gungang:big_grass_block","2×2×2 크기의 잔디 블록");
        d("bell","E: 타종. 3초 뒤 48블록 안 술래가 3초 동안 발광(30초 쿨타임)");
        d("bubble_column","물 없이 방울만 보임. 닿은 술래를 위로 던지거나 아래로 끌어내림. E: 물살 방향 전환");
        d("water","닿은 술래의 불을 끔");d("gungang:hot_spring_water","찾는 시간에 잠긴 숨는 사람을 치유");
        d("lava","닿은 술래에게 피해 50과 화상");d("gungang:acid","산성액 접촉 효과");
        d("respawn_anchor","E: 살아 있는 다른 숨는 사람 전원을 주변 빈자리로 순간이동(30초 쿨타임)");
        d("minecraft:armor_stand","갑옷·포즈 무작위, 시선에 따라 회전. E: 갑옷 또는 포즈를 무작위로 바꿈");
        d("daylight_detector","E: 맵의 낮/밤 전환(2초 쿨타임). 블록이 바뀌거나 발견되면 원래 시간으로 복귀");
        d("lectern","E: 책 쓰기(최대 1024자). 누구든 우클릭해 읽을 수 있음");
        d("end_portal_frame","E: 보는 방향으로 엔더의 눈을 최대 48블록 날리고 닿은 곳으로 순간이동(5초 쿨타임)");
        d("gungang:eunbi_doll","체력 255, 전용 스킨. E: 6가지 포즈\n좌클릭(꾹): 거리 제한 없는 광선, 처음 닿은 술래에게 초당 50. 최대 5초, 10초에 걸쳐 회복");
        d("gungang:prettiest_block","파스텔 무지개 색으로 계속 변함. E: 하트 입자와 문구 팝업, 효과음(3초 쿨타임)");
        d("crying_obsidian","체력 255. E: 약 2.5초 동안 눈물이 흐르고 낑낑 소리(3초 쿨타임)");
        d("minecraft:tnt_minecart","이동속도 2.5배(들이받기 없음). E: 불붙은 TNT 설치(5초 쿨타임), 5초 뒤 반경 4블록 술래에게 피해 80");
        d("gungang:hanbi_doll","체력 255, 전용 스킨. E: 6가지 포즈\n좌클릭(꾹): 투명화·이동속도 2.4배·무적. 최대 8초, 16초에 걸쳐 회복");
    }
}
