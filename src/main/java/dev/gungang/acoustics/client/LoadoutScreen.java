package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.*;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.math.Axis;
import com.mojang.blaze3d.platform.Lighting;

final class LoadoutScreen extends ArenaScreen {
    private EquipmentSnapshot state;
    private boolean primary=true;
    private int page,scroll,maxScroll;
    private String selected;
    private String lastClicked="";
    /** After "랜덤 무기", follow the server's equipped picks until the player clicks something. */
    private boolean followRandom;
    private long lastClickTime;
    private void clickWeapon(String id) {
        long now=net.minecraft.Util.getMillis();
        boolean twice=lastClicked.equals((primary?"primary:":"secondary:")+id)&&now-lastClickTime<250;
        lastClicked=(primary?"primary:":"secondary:")+id;lastClickTime=now;
        followRandom=false;
        select(id);
        if(twice&&state.allowed()) {LobbyClient.action(primary?"primary":"secondary",id,"");lastClicked="";}
    }
    private float yaw=-25,pitch=12,zoom=1;
    /** Third tab: saved primary/secondary combinations stored in the server player database. */
    private boolean presets;
    private int presetSlot=1;
    private String nameDraft="";
    private boolean confirmDelete;
    private net.minecraft.client.gui.components.EditBox nameBox;
    LoadoutScreen(EquipmentSnapshot state){super("GUNGANG / LOADOUT");this.state=state;selected=state.primary();PreviewCache.clear();}
    void update(EquipmentSnapshot next){
        state=next;
        if(followRandom&&!presets){select(primary?next.primary():next.secondary());return;}
        rebuildWidgets();
    }
    private List<Loadouts.Weapon> sortedSource,sorted=List.of();
    /** Groups the catalog by category (first-appearance order), keeping the server's order inside each group. */
    private List<Loadouts.Weapon> weapons(){
        var source=primary?state.catalog().primary():state.catalog().secondary();
        if(source!=sortedSource){sortedSource=source;sorted=byCategory(source);}
        return sorted;
    }
    static List<Loadouts.Weapon> byCategory(List<Loadouts.Weapon> source){
        var groups=new java.util.LinkedHashMap<String,List<Loadouts.Weapon>>();
        for(var weapon:source)groups.computeIfAbsent(weapon.category(),k->new java.util.ArrayList<>()).add(weapon);
        return groups.values().stream().flatMap(List::stream).toList();
    }
    private int capacity(){return Math.max(1,(height-140)/27);}
    private int listWidth(){return Math.max(90,(body()-24)/3);}
    private int detailX(){return left()+22+listWidth();}
    /** Detail card: name header, then preview (left) beside stats (right), then description (full width). */
    private static final int PREVIEW_TOP=107,STAT_ROW=14;
    private int previewWidth(){int dw=left()+body()-12-detailX();return Math.max(60,Math.round((dw-8)*.48f));}
    private int middleHeight(){
        int available=height-57-PREVIEW_TOP-4,stats=statRows(selected()).size()*STAT_ROW+8;
        return Math.max(32,Math.min(available-36,Math.max(stats,Math.min(110,(height-180)/2))));
    }
    private Loadouts.Weapon selected(){return weapons().stream().filter(w->w.id().equals(selected)).findFirst().orElse(weapons().get(0));}
    private void select(String id){selected=id;scroll=0;yaw=-25;pitch=12;zoom=1;rebuildWidgets();}
    @Override protected void init(){
        int x=left()+12,w=body()-24,list=listWidth();
        int tab=(w-16)/3;
        button("주무기 · 핫바 1",x,57,tab,22,true,!presets&&primary?ACCENT:MUTED,()->{presets=false;primary=true;page=0;followRandom=false;select(state.primary());});
        button("보조무기 · 왼손",x+tab+8,57,tab,22,true,!presets&&!primary?ACCENT:MUTED,()->{presets=false;primary=false;page=0;followRandom=false;select(state.secondary());});
        button("프리셋",x+(tab+8)*2,57,w-(tab+8)*2,22,true,presets?ACCENT:MUTED,()->{presets=true;followRandom=false;selectPreset(presetSlot);});
        if(presets){initPresets(x,w,list);return;}
        page=Math.min(page,Math.max(0,weapons().size()-capacity()));
        for(int i=page;i<Math.min(weapons().size(),page+capacity());i++){
            var weapon=weapons().get(i);String equipped=primary?state.primary():state.secondary();
            boolean worn=equipped.equals(weapon.id());
            var label=Component.empty();
            if(worn)label.append(Component.literal("● ").withStyle(net.minecraft.ChatFormatting.GREEN));
            label.append(Component.literal(weapon.category()+"  ").withStyle(s->s.withColor(DIM&0xFFFFFF)))
                    .append(Component.literal(weapon.name()).withStyle(worn?net.minecraft.ChatFormatting.GREEN:net.minecraft.ChatFormatting.WHITE));
            listButton(label,x,85+(i-page)*27,list-6,24,true,
                    weapon.id().equals(selected().id())?ACCENT:MUTED,()->clickWeapon(weapon.id()));
        }
        button("랜덤 무기",x,height-48,list-6,22,state.allowed(),0xFFE0B860,()->{followRandom=true;LobbyClient.action("randomLoadout","","");});
        boolean worn=selected().id().equals(primary?state.primary():state.secondary());
        Runnable equip=()->LobbyClient.action(primary?"primary":"secondary",selected().id(),"");
        if(worn)button("장착 중인 무기",detailX(),height-48,Math.max(40,w-list-82),22,state.allowed(),READY,equip);
        else colorButton("선택한 무기 장착",detailX(),height-48,Math.max(40,w-list-82),22,state.allowed(),ACCENT,equip);
        closeButton(x+w-64,height-48,64,22);
    }
    private PlayerRecord.Preset preset(int slot){return state.presets().stream().filter(p->p.slot()==slot).findFirst().orElse(null);}
    private void selectPreset(int slot){
        presetSlot=slot;confirmDelete=false;
        var preset=preset(slot);nameDraft=preset==null?"":preset.name();
        rebuildWidgets();
    }
    private void initPresets(int x,int w,int list){
        for(int slot=1;slot<=PlayerRecord.PRESETS;slot++){
            int s=slot;var preset=preset(slot);
            var label=Component.empty();
            if(state.defaultPreset()==slot)label.append(Component.literal("★ ").withStyle(net.minecraft.ChatFormatting.GOLD));
            label.append(Component.literal(slot+"  ").withStyle(c->c.withColor(DIM&0xFFFFFF)));
            label.append(preset==null?Component.literal("빈 슬롯").withStyle(c->c.withColor(DIM&0xFFFFFF)):Component.literal(preset.name()).withStyle(net.minecraft.ChatFormatting.WHITE));
            listButton(label,x,85+(slot-1)*27,list-6,24,true,slot==presetSlot?ACCENT:MUTED,()->selectPreset(s));
        }
        var preset=preset(presetSlot);
        if(preset==null)nameDraft="";else if(nameDraft.isEmpty())nameDraft=preset.name();
        int dx=detailX(),right=x+w;
        if(preset!=null){
            int boxW=Math.max(60,Math.min(180,right-dx-90));
            nameBox=new net.minecraft.client.gui.components.EditBox(font,dx+8,PREVIEW_TOP+82,boxW,18,Component.literal("프리셋 이름"));
            nameBox.setMaxLength(20);nameBox.setValue(nameDraft);nameBox.setResponder(text->nameDraft=text);
            addRenderableWidget(nameBox);
            button("이름 변경",dx+12+boxW,PREVIEW_TOP+81,Math.max(40,Math.min(76,right-dx-boxW-18)),20,true,ACCENT,this::renamePreset);
        }else nameBox=null;
        String slot=Integer.toString(presetSlot);
        int bw=(Math.max(160,w-list-82)-12)/4,by=height-48;
        colorButton(preset==null?"현재 장비 저장":"현재 장비로 덮어쓰기",dx,by,bw,22,true,ACCENT,()->{confirmDelete=false;LobbyClient.action("presetSave",slot,"");});
        button("장착",dx+bw+4,by,bw,22,preset!=null&&state.allowed(),READY,()->LobbyClient.action("presetApply",slot,""));
        button(state.defaultPreset()==presetSlot?"기본값 해제":"기본값 설정",dx+(bw+4)*2,by,bw,22,preset!=null,0xFFE0B860,()->LobbyClient.action("presetDefault",slot,""));
        button(confirmDelete?"삭제 확인":"삭제",dx+(bw+4)*3,by,bw,22,preset!=null,0xFFCC3333,()->{
            if(confirmDelete){confirmDelete=false;LobbyClient.action("presetDelete",slot,"");}else{confirmDelete=true;rebuildWidgets();}
        });
        closeButton(x+w-64,by,64,22);
    }
    private void renamePreset(){if(preset(presetSlot)!=null)LobbyClient.action("presetRename",Integer.toString(presetSlot),nameDraft.strip());}
    private String weaponName(List<Loadouts.Weapon> group,String id){
        return group.stream().filter(w->w.id().equals(id)).map(Loadouts.Weapon::name).findFirst().orElse("등록되지 않은 무기");
    }
    private void renderPresets(GuiGraphics g){
        int x=left()+12,w=body()-24,dx=detailX(),right=x+w;
        var preset=preset(presetSlot);boolean fallback=state.defaultPreset()==presetSlot;
        card(g,dx,85,right,height-57);
        g.fillGradient(dx,85,right,103,0xFF1B293C,PANEL);g.fill(dx,85,right,86,fallback?0xFFE0B860:ACCENT);
        int tagX=right-6;
        if(fallback){String tag="경기 시작 기본값";tagX-=font.width(tag)+8;badge(g,tag,tagX,89,0xFFE0B860);}
        String title="프리셋 "+presetSlot+(preset==null?"  ·  빈 슬롯":"  ·  "+preset.name());
        g.drawString(font,font.plainSubstrByWidth(title,Math.max(1,tagX-dx-10)),dx+6,90,INK,false);
        var catalog=state.catalog();int y=PREVIEW_TOP+4;
        if(preset!=null){
            presetLine(g,"주무기",weaponName(catalog.primary(),preset.primary()),preset.primary(),dx,y,right);
            presetLine(g,"보조무기",weaponName(catalog.secondary(),preset.secondary()),preset.secondary(),dx,y+STAT_ROW,right);
            g.drawString(font,"이름 (20자 이하, Enter로 저장)",dx+8,PREVIEW_TOP+70,DIM,false);
        }else g.drawString(font,font.plainSubstrByWidth("아래 버튼으로 현재 장착한 무기 조합을 이 슬롯에 저장합니다.",Math.max(1,right-dx-16)),dx+8,y,MUTED,false);
        int cy=height-57-48;
        if(cy<PREVIEW_TOP+(preset==null?24:110))return;
        g.fill(dx+4,cy-6,right-4,cy-5,0xFF1E2B3B);
        g.drawString(font,"현재 장비",dx+8,cy,DIM,false);
        presetLine(g,"주무기",state.primary().isEmpty()?"미선택":weaponName(catalog.primary(),state.primary()),state.primary(),dx,cy+12,right);
        presetLine(g,"보조무기",state.secondary().isEmpty()?"미선택":weaponName(catalog.secondary(),state.secondary()),state.secondary(),dx,cy+12+STAT_ROW,right);
    }
    private void presetLine(GuiGraphics g,String label,String name,String id,int dx,int y,int right){
        g.drawString(font,label,dx+8,y,INK,false);
        var text=Component.literal(name).withStyle(net.minecraft.ChatFormatting.WHITE);
        if(!id.isEmpty())text.append(Component.literal(" [id: "+id+"]").withStyle(s->s.withColor(0xC8B060)));
        g.drawString(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(text,Math.max(1,right-dx-70))),dx+62,y,ACCENT,false);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(presets&&nameBox!=null&&nameBox.isFocused()&&(key==org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER||key==org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)){renamePreset();return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void tick(){if(nameBox!=null)nameBox.tick();}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        if(presets){
            shell(g,state.team()+"  /  프리셋 "+state.presets().size()+"/"+PlayerRecord.PRESETS+"  /  ★ 표시는 경기 시작 시 자동 장착");
            renderPresets(g);super.render(g,mouseX,mouseY,partial);return;
        }
        shell(g,state.team()+"  /  "+(state.preparationSeconds()>0?"출격까지 "+state.preparationSeconds()+"초":state.spawnHint()?"팀 스폰 구역":"무기 선택")+"  /  "+LobbyClient.LOADOUT.getTranslatedKeyMessage().getString()+"로 다시 열기");
        int x=left()+12,w=body()-24,dx=detailX(),right=x+w,dw=right-dx;
        var weapon=selected();boolean worn=weapon.id().equals(primary?state.primary():state.secondary());
        card(g,dx,85,right,height-57);
        g.fillGradient(dx,85,right,103,0xFF1B293C,PANEL);g.fill(dx,85,right,86,worn?READY:ACCENT);
        int tagX=right-6;
        if(worn){String tag="장착 중";tagX-=font.width(tag)+8;badge(g,tag,tagX,89,READY);tagX-=4;}
        var title=Component.literal(weapon.name()).withStyle(worn?net.minecraft.ChatFormatting.GREEN:net.minecraft.ChatFormatting.WHITE)
                .append(Component.literal(" [id: "+weapon.id()+"]").withStyle(s->s.withColor(0xC8B060)));
        g.drawString(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(title,Math.max(1,tagX-dx-10))),dx+6,90,INK,false);
        int pw=previewWidth(),mh=middleHeight();
        preview(g,weapon,dx+4,PREVIEW_TOP,pw,mh);
        // Overlays sit above the 3D item, which renders at z 150.
        g.pose().pushPose();g.pose().translate(0,0,300);
        badge(g,weapon.category(),dx+8,PREVIEW_TOP+mh-14,ACCENT);
        String hint=font.plainSubstrByWidth("드래그 회전 / 휠 확대",Math.max(1,pw-font.width(weapon.category())-28));
        g.drawString(font,hint,dx+pw-font.width(hint),PREVIEW_TOP+mh-11,DIM,false);
        g.pose().popPose();
        statPanel(g,statRows(weapon),dx+pw+12,PREVIEW_TOP,right-6,mh);
        int top=PREVIEW_TOP+mh+6,bottom=height-57;
        g.fill(dx+4,top-3,right-4,top-2,0xFF1E2B3B);
        var rows=new ArrayList<Row>();
        if(primary)paragraph(rows,"막대: 목록 내 같은 단위의 최댓값 기준",dw-16,true);
        if(weapon.releaseDate()!=null&&!weapon.releaseDate().isEmpty())paragraph(rows,"출시 "+weapon.releaseDate().replace('-','.'),dw-16,true);
        paragraph(rows,weapon.description(),dw-16);heading(rows,"사용방법");paragraph(rows,weapon.usage(),dw-16);
        int total=rows.stream().mapToInt(Row::height).sum();maxScroll=Math.max(0,total-(bottom-top));scroll=Math.min(scroll,maxScroll);
        g.enableScissor(dx,top,right,bottom);int y=top-scroll;
        for(var row:rows){
            if(row.header()){g.fill(dx+8,y+1,dx+10,y+10,ACCENT);g.drawString(font,row.label(),dx+14,y+2,INK,false);}
            else g.drawString(font,row.label(),dx+8,y,row.note()?DIM:MUTED,false);
            y+=row.height();
        }
        g.flush();g.disableScissor();
        if(maxScroll>0){
            int h=Math.max(8,(bottom-top)*(bottom-top)/Math.max(1,total));int sy=top+(bottom-top-h)*scroll/maxScroll;
            g.fill(right-2,top,right,bottom,0xFF1E2B3B);g.fill(right-2,sy,right,sy+h,ACCENT);
            if(scroll>0)g.fillGradient(dx,top,right-2,top+8,0xC0101A28,0x00101A28);
            if(scroll<maxScroll)g.fillGradient(dx,bottom-8,right-2,bottom,0x00101A28,0xC0101A28);
        }
        if(weapons().size()>capacity()){
            int track=capacity()*27-3,thumb=Math.max(12,track*capacity()/weapons().size()),ty=85+(track-thumb)*page/(weapons().size()-capacity());
            g.fill(x+listWidth()-4,85,x+listWidth()-1,85+track,0xFF1E2B3B);g.fill(x+listWidth()-4,ty,x+listWidth()-1,ty+thumb,ACCENT);
        }
        super.render(g,mouseX,mouseY,partial);
    }
    private record Row(String label,String value,float fraction,int height,boolean header,boolean note) {
        Row(String label,String value,float fraction,int height){this(label,value,fraction,height,false,false);}
    }
    private List<Row> statRows(Loadouts.Weapon weapon){
        var rows=new ArrayList<Row>();
        if(primary){
            stat(rows,"최대 체력",number(weapon.maxHealth()),v->number(v.maxHealth()));
            stat(rows,"이동 속도",number(weapon.movementSpeed())+"%",v->number(v.movementSpeed())+"%");
            if(weapon.specs()!=null){
                stat(rows,"피해량",weapon.specs().damage(),v->v.specs()==null?"":v.specs().damage());
                stat(rows,"명중률",weapon.specs().accuracy(),v->v.specs()==null?"":v.specs().accuracy());
                stat(rows,"안정성",weapon.specs().stability(),v->v.specs()==null?"":v.specs().stability());
                stat(rows,"사거리",weapon.specs().range(),v->v.specs()==null?"":v.specs().range());
                stat(rows,"연사력",weapon.specs().fireRate(),v->v.specs()==null?"":v.specs().fireRate());
            }
        }else rows.add(new Row("재사용 대기시간",weapon.cooldownSeconds()==0?"무제한":number(weapon.cooldownSeconds())+"초",-1,STAT_ROW));
        return rows;
    }
    /** One line per stat: label, bar, value. Rows without a comparable number show the value text only. */
    private void statPanel(GuiGraphics g,List<Row> rows,int x1,int y1,int x2,int h){
        int labelW=0,valueW=0;
        for(var row:rows){labelW=Math.max(labelW,font.width(row.label()));valueW=Math.max(valueW,Math.min(64,font.width(row.value())));}
        int barX=x1+labelW+8,barEnd=x2-valueW-6,y=y1+Math.max(0,(h-rows.size()*STAT_ROW)/2)+3;
        g.enableScissor(x1,y1,x2,y1+h);
        for(var row:rows){
            g.drawString(font,row.label(),x1,y,INK,false);
            String value=font.plainSubstrByWidth(row.value(),Math.max(1,row.fraction()>=0?valueW:x2-barX));
            if(row.fraction()<0||barEnd-barX<16){g.drawString(font,value,row.fraction()<0?barX:x2-font.width(value),y,ACCENT,false);y+=STAT_ROW;continue;}
            g.drawString(font,value,x2-font.width(value),y,ACCENT,false);
            g.fill(barX,y,barEnd,y+8,0xFF1E2B3B);g.fill(barX+1,y+1,barEnd-1,y+7,0xFF29394D);
            int fill=Math.round((barEnd-barX-2)*Math.max(0,Math.min(1,row.fraction())));
            if(fill>0)g.fillGradient(barX+1,y+1,barX+1+fill,y+7,blend(ACCENT,0xFFFFFFFF,.25f),blend(ACCENT,0xFF000000,.2f));
            y+=STAT_ROW;
        }
        g.disableScissor();
    }
    private void stat(List<Row> rows,String label,String value,Function<Loadouts.Weapon,String> accessor){rows.add(new Row(label,value,WeaponStatScale.fraction(value,weapons().stream().map(accessor).toList()),STAT_ROW));}
    private void heading(List<Row> rows,String text){rows.add(new Row(text,"",-1,14,true,false));}
    private void paragraph(List<Row> rows,String text,int width){paragraph(rows,text,width,false);}
    private void paragraph(List<Row> rows,String text,int width,boolean note){
        for(String line:WeaponText.lines(text))for(var part:font.split(Component.literal(line),Math.max(1,width))) {
            var out=new StringBuilder();part.accept((index,style,cp)->{out.appendCodePoint(cp);return true;});rows.add(new Row(out.toString(),"",-1,12,false,note));
        }
        rows.add(new Row("","",-1,6));
    }
    private static String number(double value){return String.format(Locale.ROOT,value==Math.rint(value)?"%.0f":"%.1f",value);}
    private void preview(GuiGraphics g,Loadouts.Weapon weapon,int x,int y,int w,int h){
        g.fillGradient(x,y,x+w,y+h,0xFF1D2E44,0xFF132031);
        for(int gx=x+w/2%16;gx<x+w;gx+=16)g.fill(gx,y,gx+1,y+h,0x0CFFFFFF);
        for(int gy=y+h-1;gy>y;gy-=16)g.fill(x,gy,x+w,gy+1,0x0CFFFFFF);
        int c=Math.min(8,Math.min(w,h)/4);
        g.fill(x,y,x+c,y+1,ACCENT);g.fill(x,y,x+1,y+c,ACCENT);g.fill(x+w-c,y,x+w,y+1,ACCENT);g.fill(x+w-1,y,x+w,y+c,ACCENT);
        g.fill(x,y+h-1,x+c,y+h,ACCENT);g.fill(x,y+h-c,x+1,y+h,ACCENT);g.fill(x+w-c,y+h-1,x+w,y+h,ACCENT);g.fill(x+w-1,y+h-c,x+w,y+h,ACCENT);
        var stack=PreviewCache.item(weapon.id(),primary);
        if(stack==null||stack.isEmpty()){
            String message=stack==null?"모델 불러오는 중…":"표시할 아이템 모델이 없습니다";
            g.drawString(font,font.plainSubstrByWidth(message,Math.max(1,w-8)),x+4,y+h/2-4,MUTED,false);return;
        }
        g.flush();g.enableScissor(x,y,x+w,y+h);g.pose().pushPose();
        // Fit the measured model: its diagonal stays inside the panel at any rotation, centred on the model itself.
        float[] box=bounds(stack);
        float dx=box==null?1:box[3]-box[0],dy=box==null?1:box[4]-box[1],dz=box==null?0:box[5]-box[2];
        float diagonal=Math.max(.1f,(float)Math.sqrt(dx*dx+dy*dy+dz*dz));
        g.pose().translate(x+w/2f,y+h/2f,150);float size=Math.min(w,h)*.9f/diagonal*zoom;
        g.pose().scale(size,-size,size);g.pose().mulPose(Axis.XP.rotationDegrees(pitch));g.pose().mulPose(Axis.YP.rotationDegrees(yaw));
        if(box!=null)g.pose().translate(-(box[0]+box[3])/2,-(box[1]+box[4])/2,-(box[2]+box[5])/2);
        Lighting.setupFor3DItems();
        minecraft.getItemRenderer().renderStatic(stack,ItemDisplayContext.FIXED,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,g.pose(),g.bufferSource(),minecraft.level,0);
        g.flush();g.pose().popPose();g.disableScissor();Lighting.setupFor3DItems();
    }
    /** Model-space bounds {minX,minY,minZ,maxX,maxY,maxZ} per preview stack; TaCZ guns are far larger than one block in FIXED. */
    private static final Map<net.minecraft.world.item.ItemStack,float[]> BOUNDS=new WeakHashMap<>();
    private float[] bounds(net.minecraft.world.item.ItemStack stack){
        if(BOUNDS.containsKey(stack))return BOUNDS.get(stack);
        float[] box={Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE};
        var probe=new com.mojang.blaze3d.vertex.VertexConsumer(){
            @Override public com.mojang.blaze3d.vertex.VertexConsumer vertex(double x,double y,double z){
                box[0]=Math.min(box[0],(float)x);box[1]=Math.min(box[1],(float)y);box[2]=Math.min(box[2],(float)z);
                box[3]=Math.max(box[3],(float)x);box[4]=Math.max(box[4],(float)y);box[5]=Math.max(box[5],(float)z);return this;
            }
            @Override public com.mojang.blaze3d.vertex.VertexConsumer color(int r,int g,int b,int a){return this;}
            @Override public com.mojang.blaze3d.vertex.VertexConsumer uv(float u,float v){return this;}
            @Override public com.mojang.blaze3d.vertex.VertexConsumer overlayCoords(int u,int v){return this;}
            @Override public com.mojang.blaze3d.vertex.VertexConsumer uv2(int u,int v){return this;}
            @Override public com.mojang.blaze3d.vertex.VertexConsumer normal(float x,float y,float z){return this;}
            @Override public void endVertex(){}
            @Override public void defaultColor(int r,int g,int b,int a){}
            @Override public void unsetDefaultColor(){}
        };
        float[] result;
        try{
            minecraft.getItemRenderer().renderStatic(stack,ItemDisplayContext.FIXED,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,new com.mojang.blaze3d.vertex.PoseStack(),type->probe,minecraft.level,0);
            result=box[3]>=box[0]?box:null;
        }catch(RuntimeException failure){result=null;}
        BOUNDS.put(stack,result);return result;
    }
    private boolean inPreview(double x,double y){return x>=detailX()+4&&x<detailX()+4+previewWidth()&&y>=PREVIEW_TOP&&y<PREVIEW_TOP+middleHeight();}
    @Override public boolean mouseScrolled(double x,double y,double delta){
        if(presets)return super.mouseScrolled(x,y,delta);
        if(x<left()+12+listWidth()){page=Math.max(0,Math.min(Math.max(0,weapons().size()-capacity()),page-(int)Math.signum(delta)*2));rebuildWidgets();}
        else if(inPreview(x,y))zoom=Math.max(.3f,Math.min(3f,zoom+(float)delta*.1f));
        else scroll=Math.max(0,Math.min(maxScroll,scroll-(int)Math.signum(delta)*24));return true;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(presets)return super.mouseDragged(x,y,button,dx,dy);
        if(button==0&&inPreview(x,y)){yaw+=(float)dx*2;pitch=Math.max(-85,Math.min(85,pitch+(float)dy));return true;}
        int edge=left()+12+listWidth();
        if(button==0&&x>=edge-8&&x<=edge+2&&weapons().size()>capacity()){
            page=Math.max(0,Math.min(weapons().size()-capacity(),(int)((y-85)/(capacity()*27.0)*(weapons().size()-capacity()))));rebuildWidgets();return true;
        }
        return super.mouseDragged(x,y,button,dx,dy);
    }
}
