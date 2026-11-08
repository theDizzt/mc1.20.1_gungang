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
    private record Entry(String type,String block,BlockState state,String number,Component name,boolean special,boolean attack,boolean colored,String description,List<PropInteraction.Tag> tags){}
    private final List<Entry> entries=new ArrayList<>();
    private int scroll;
    private Entry selected;
    private net.minecraft.client.gui.components.Button morph,unmorph;
    public PropListScreen(){
        super(Component.literal("변신 블록 목록"));
        for(String type:PropCatalog.available()){
            String block=PropCatalog.display(type);
            var state=PropCatalog.state(block);
            entries.add(new Entry(type,block,state,PropCatalog.number(block),PropCatalog.name(block),PropInteraction.tagged(block,state),PropInteraction.attack(block),type.startsWith("@")||type.startsWith("#"),describe(type,block,state),PropInteraction.tags(block,state)));
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
        g.drawCenteredString(font,Component.literal("§c■ 공격  §5■ 디버프  §a■ 견고  §6■ 접착  §9■ 신속  §b■ 액체  §6■ 광원  §e■ 특수  §d■ 색상  §8· 클릭해 선택 후 변신"+(maxScroll()>0?" · 휠로 스크롤":"")),width/2,top-15,0xFFB0B0B0);
        Entry hovered=null;
        int first=scroll*columns,last=Math.min(entries.size(),first+visibleRows()*columns);
        for(int i=first;i<last;i++){
            var e=entries.get(i);int index=i-first;
            int x=left+(index%columns)*cell,y=top+(index/columns)*cell;
            boolean over=mouseX>=x&&mouseX<x+cell&&mouseY>=y&&mouseY<y+cell;
            g.fill(x+1,y+1,x+cell-1,y+cell-1,over?0x60FFFFFF:0x30000000);
            if(e==selected)g.fill(x+1,y+1,x+cell-1,y+cell-1,0x7070CDBF);
            if(e==selected)g.renderOutline(x,y,cell,cell,0xFF70CDBF);
            else{var tag=e.tags.stream().filter(t->(t.color()&0xFFFFFF)!=0).findFirst();if(tag.isPresent())g.renderOutline(x+1,y+1,cell-2,cell-2,tag.get().color());}
            int size=cell-6;
            PropAbilityHud.icon(g,e.block,e.state,x+3,y+2,size);
            // Above the item (z ~150-170) but below the tooltip (z 400), which it used to cover.
            g.pose().pushPose();g.pose().translate(0,0,250);
            float s=.65f;g.pose().scale(s,s,1);
            String n=e.number;g.drawString(font,n,Math.round((x+cell-2)/s)-font.width(n),Math.round((y+cell-1)/s)-9,0xFFFFFFFF,true);
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
        for(var t:e.tags)head.append(Component.literal(" "+t.label()).withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(t.color()&0xFFFFFF)));
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
        d("gungang:nozzle","물 분사 기둥. 술래의 불만 끔. 물·웅덩이 칸에 겹쳐 숨을 수 있음\nE: 3초 대량 분사(10초 쿨타임). 물줄기에 닿은 술래·변신 블록을 위로 날려버림(공중에서 자유 이동)");
        d("gungang:acid_nozzle","산성 분사 기둥 안의 술래에게 1초마다 피해 50과 독. 산성액·웅덩이 칸에 겹쳐 숨을 수 있음");
        d("gungang:uxo","술래가 밟으면 폭발(반경 3블록, 피해 120). 변신은 유지, 5초 뒤 다시 터질 수 있음");
        d("barrier","본체가 투명. 숨는 팀에게는 흰 테두리만 보임");d("cobweb","닿은 술래를 느리게 함. 다른 플레이어가 지나갈 수 있음");
        d("obsidian","체력 255");d("bedrock","체력 999");d("minecraft:painting","몸이 액자 두께만큼 얇아 벽에 바로 붙을 수 있음");
        d("chest","E: 27칸 보관함(닫으면 뚜껑이 닫힘)");d("barrel","E: 27칸 보관함");
        d("gungang:war_table","체력 255. E: 로비 화면(경기 규칙에 따라 제한). R: 회전");d("gungang:armory","체력 255. E: 무기 화면(경기 규칙에 따라 제한). R: 회전");
        d("rose_bush","충돌 판정이 없어 다른 플레이어가 지나갈 수 있음");
        d("spawner","E: 술래 근처에 독거미 소환(체력 100, 피해 16, 독 5초, 84초 쿨타임). 독거미 체력은 보조 체력바");
        d("soul_sand","위를 걷는 개체가 느려짐(진짜 영혼 모래와 같음). 영혼 입자가 피어오름");d("anvil","E: 모루·금 간 모루·손상된 모루 전환. R: 회전");d("hay_block","R: 축 회전");
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
        d("minecraft:end_crystal","체력 255. E: 24블록 안의 모든 술래를 벽 너머까지 감지해 10초간 엔드 수정 광선으로 공격(초당 16 피해, 30초 쿨타임)");
        d("sculk_sensor","E: 비명 효과 켜기/끄기. 켜진 동안 2초마다 딸깍 소리·입자(상태 효과 없음)");
        d("gungang:hologram_projector","주무기 중 무작위 무기를 홀로그램으로 표시\nE: 크기 50~300%, R: 회전 속도");
        d("sculk_shrieker","E: 비명 효과 켜기/끄기. 켜진 동안 2초마다 비명 소리와 입자(어둠 효과 없음)");
        d("gungang:geyser","체력 255. 3×3 바닥 모습. 찾는 시간에 술래가 3블록 안에 오면 높이 8블록 물기둥 3초 분출(화상 10초)");
        d("gungang:hot_spring_nozzle","바라보는 방향으로 약 3블록 물줄기. 찾는 시간에 닿은 숨는 팀 치유. R: 방향 전환");
        d("gungang:bath_toad","치유 물줄기(온천수 노즐과 같음). R: 방향 전환");
        d("gungang:shutter","E: 말려 올라가며 열리고 내려오며 닫힘. R: 축 회전");
        d("sea_pickle","E: 1~4개 전환, 개수에 따라 밝기 증가");d("lightning_rod","E: 충전(빛남) 켜기/끄기. 찾는 시간에 60초 동안 켜 두면 벼락이 떨어져 반경 8블록 안 술래 전원 사망");
        d("crimson_hyphae","R: 축 회전");
        d("note_block","E: 고른 곡을 한 음씩 연주(6옥타브). R: 곡 바꾸기(모차르트 소나타·엘리제를 위하여·카논·송어·나비·월광·흑건·작은 별)");
        d("jukebox","E: 무작위 음반을 위로 발사(3초 뒤 사라짐, 1초 쿨타임)");d("turtle_egg","E: 알 1~4개 전환. 밟아도 깨지지 않음");
        d("brewing_stand","E: 점프 강화·신속·투명화 중 하나를 숨는 팀 전원에게 15초(50초 쿨타임). 투명화가 걸린 동안 변신 블록이 누구에게도 보이지 않음(자신·팀원 포함)");
        d("gungang:big_grass_block","체력 400. 2×2×2 크기의 잔디 블록");
        d("bell","E: 타종. 3초 뒤 48블록 안 술래가 3초 동안 발광(15초 쿨타임)");
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
        d("minecraft:tnt_minecart","이동속도 2.5배(들이받기 없음). E: 불붙은 TNT 설치(5초 쿨타임), 5초 뒤 반경 4블록 술래에게 피해 120");
        d("gungang:hanbi_doll","체력 255, 전용 스킨. E: 6가지 포즈\n좌클릭(꾹): 투명화·이동속도 2.4배·무적. 최대 8초, 16초에 걸쳐 회복");
        d("slime_block","벽·천장에 닿으면 붙어서 이동(벽은 앞으로 걸어 들어가면 붙음). E: 떨어지기. 점프 불가");
        d("honey_block","벽·천장에 닿으면 붙어서 이동(벽은 앞으로 걸어 들어가면 붙음). E: 떨어지기. 점프 불가");
        d("repeater","E: 모양 바꾸기(지연 1~4, 켜짐/꺼짐). R: 회전");
        d("ladder","R: 회전");
        d("@candle_cake","초 16색 중 무작위. E: 촛불 켜기/끄기. 켜져 있는 동안 생일 축하 노래 재생");
        d("redstone_torch","E: 켜기/끄기. R: 바닥 → 벽(4방향) 순서로 회전");
        d("gungang:death_zone","술래에게는 보이지 않는 데스존 블록(같은 팀에게만 해골 표시). 닿은 술래는 즉시 사망");
        d("torch","R: 바닥 → 벽(4방향) 순서로 회전");
        d("soul_torch","R: 바닥 → 벽(4방향) 순서로 회전");
        d("oak_planks","나무 11종 판자 중 무작위");d("oak_log","나무 11종 원목·자루·대나무 블록 중 무작위. R: 축 회전");
        d("oak_stairs","나무 11종 계단 중 무작위");d("oak_slab","나무 11종 반 블록 중 무작위");d("oak_fence","모든 울타리 중 무작위");
        d("oak_pressure_plate","모든 압력판 중 무작위");d("stripped_oak_log","껍질 벗긴 나무 11종 중 무작위. R: 축 회전");
        d("sandstone","사암·매끄러운 사암·조각된 사암 중 무작위");d("red_sandstone","붉은 사암·매끄러운 붉은 사암·조각된 붉은 사암 중 무작위");
        d("stone_bricks","석재 벽돌·이끼 낀·금 간·조각된 석재 벽돌 중 무작위");
        d("oak_door","나무 11종 문 중 무작위. E: 열기/닫기");d("oak_trapdoor","나무 11종 다락문 중 무작위. E: 열기/닫기");
        d("polished_blackstone","윤나는 흑암·조각된 윤나는 흑암 중 무작위");d("oak_leaves","나뭇잎 9종 중 무작위");
        d("coal_ore","석탄·철·구리·금·레드스톤·에메랄드·청금석·다이아몬드 광석 중 무작위");d("deepslate_coal_ore","심층암 광석 8종 중 무작위");
        d("polished_blackstone_bricks","윤나는 흑암 벽돌·금 간 윤나는 흑암 벽돌 중 무작위");
        d("sponge","이동속도 1.25배. 물·웅덩이·액체에 닿으면 젖은 스펀지가 되어 1.0배");
        d("cobblestone_wall","돌 담장 22종 중 무작위");d("nether_bricks","네더 벽돌·금 간 네더 벽돌·조각된 네더 벽돌 중 무작위");
        d("cartography_table","E: 8초 동안 맵 미니맵에 모든 술래와 팀원 위치 표시(30초 쿨타임)");
        d("farmland","밀·당근·감자·비트 중 무작위 작물이 심김. E: 작물 바꾸기");d("oak_sapling","묘목 10종 중 무작위");
        d("piston","E: 피스톤 작동(다시 E로 수축). 앞 두 칸의 술래·팀원을 밀쳐냄. 맵 블록은 밀지 못함. R: 여섯 방향 회전");
        d("sticky_piston","피스톤과 같음. 앞의 팀원은 밀치지 않고 머리에 붙여 함께 움직이고, 수축하면 끌어당김");
        d("gungang:desk_clock","현재 시각(24시간 시·분·초)을 보여주는 LCD 탁상 시계. E: 알람 켜기/끄기(켜진 동안 계속 울림). R: 회전");
        d("gungang:money_cart","이동속도 2.4배. 이동하는 동안 돈(에메랄드·금 조각)을 흘림(3초 뒤 사라짐). E: 현금 인출기 소리(3초 쿨타임)");
        d("stone","돌·화강암·섬록암·안산암 중 무작위");d("smooth_stone","매끄러운 돌·윤나는 화강암·섬록암·안산암·심층암·매끄러운 현무암 중 무작위");
        d("oak_hanging_sign","나무 11종 매달린 표지판 중 무작위. E: 닉네임·이모티콘·자신 또는 팀원 좌표 중 임의 문구");d("cocoa","다 익은 코코아");
        d("gungang:aquarium","물·모래·해초·암석이 든 어항. 대구·연어·열대어·복어·작은 오징어 중 무작위 물고기");
        d("gungang:chameleon_block","E: 맞닿은 블록(위·아래·옆) 중 하나의 모양으로 위장. 누를 때마다 다른 블록(쿨타임 없음)");
        d("gungang:capsule_machine","체력 100. 높이 2블록(대기실 캡슐 뽑기 기계와 같은 블록). E: 변신 중 한 번만, 자신을 뺀 숨는 팀원 전원의 블록을 무작위로 바꿈");
        d("gungang:error_block","텍스처가 없는 블록");
        d("gungang:garden_fountain","체력 255. 2×2 크기, 높이 2블록의 3단 정원 분수. E: 3초 동안 물줄기를 뿜어 닿은 술래·변신 블록을 위로 날려버림(10초 쿨타임)");
        d("gungang:nuke_warhead","체력 100. 2×3×2 크기의 핵탄두. 처음 총에 맞으면 거대한 폭발과 함께 반경 64블록 안 술래 전원 사망(변신당 한 번)");
        d("gungang:easel","체력 255. 폭 2블록·높이 2블록. E: 캔버스에 16×16 그림 그리기. 누구나 볼 수 있음");
        d("light","보이지 않는 빛 블록(같은 팀에게만 표시). 이동속도 3배, 해로운 상태 이상·화상을 받지 않음. E: 밝기 15→12→9→6→3→0 순환. 마우스 휠: 밝기 1씩 조절");
        d("gungang:wall_clock","체력 255. 3×3 크기의 둥근 아날로그 벽시계(두께는 표지판 정도). 현재 시각을 시·분·초침으로 보여 줌");
        d("gungang:mirror","체력 255. 전신 거울. 앞쪽 풍경과 사람이 실제로 비침");
        d("gungang:dice","E: 주사위 던지기. 굴러가다 멈춘 눈(1~6)을 문구로 띄움");
        d("gungang:chandelier","체력 255. 3×2×3 크기. E: 가까운 천장(12블록 안)에 매달리기 / 다시 E로 떨어지기");
        d("gungang:slot_machine","E: 슬롯 돌리기(쿨타임 없음, 릴이 멈춘 뒤 다시 돌리기). 777 잭팟이면 당첨 버저와 함께 모든 술래에게 돈벼락, 술래는 5초 동안 맞다가 사망(찾는 시간에만)");
        d("gungang:billiard_ball","이동속도 1.8배로 굴러다님. E: 1~15번·흰 공·노란 공·빨간 공 중 다른 공으로 바꾸기");
        d("gungang:wishing_well","체력 255. 3×3 크기의 지붕 달린 우물. E: 두레박 내리기/올리기");
        d("gungang:embezzlement_statue","체력 999. 높이 8블록의 거대한 동상(ArchBear, 횃불과 돈자루). E: 20초 동안 반경 8블록에 돈벼락, 맞은 술래는 피해 80(30초 쿨타임)");
        d("gungang:christmas_tree","체력 255. 장식등이 깜빡이는 크리스마스 트리. E: 장식등 켜기/끄기");
        d("gungang:sanga_doll","체력 255, 전용 스킨. 스케이트보드와 보호구. 이동속도 2.4배. 앞으로 달려 벽에 닿으면 스케이트보드로 벽을 타고 오르며, 보는 방향으로 벽을 따라 달림(점프: 벽 차고 뛰기). E: 6가지 포즈");
        d("dropper","94번 발사기와 같음(R: 여섯 방향). E: 보는 쪽으로 무작위 모양의 폭죽 발사(1초 쿨타임, 찾는 시간에만)");
        d("gungang:unpleasant_photo","광산 수레처럼 보는 방향으로 회전. 사진을 바라본 술래·팀원은 10초 동안 멀미");
        d("gungang:combat_unit","체력 255. 마네킹과 같은 모습에 미니건. 직접 조작 불가: 찾는 시간에 가장 가까운 술래를 찾아가 탄창 무제한 미니건으로 사격(초당 10발, 발당 3.5). 휠: 다른 시점 관전(계속 자율 전투) / R: 내 시점");
        d("gungang:shadow","검은 그림자만 바닥에 깔림. 아무도 부딪히지 않음");
        d("red_mushroom","빨간·갈색 버섯, 진홍·뒤틀린 균 중 무작위");
        d("conduit","E: 4초 동안 24블록 안 술래에게 번개 폭풍(번개 1회 피해 10, 90초 쿨타임). 찾는 시간에만");
    }
}
