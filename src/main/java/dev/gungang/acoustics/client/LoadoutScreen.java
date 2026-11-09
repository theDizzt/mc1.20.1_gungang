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
    /**
     * Grid view (remembered for the session): five smaller tiles a row in a weapon area a little wider than the list's,
     * leaving the detail panel room for its stat bars.
     */
    /** Grid view is the default (u269); the list view is one click away and kept for the rest of the session. */
    private static boolean grid=true;
    private int gridScroll,gridMax;
    private boolean gridView(){return grid&&!presets;}
    private int listWidth(){return gridView()?Math.max(150,Math.round((body()-24)*.42f)):Math.max(90,(body()-24)/3);}
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
        nameBox=null;
        int x=left()+12,w=body()-24,list=listWidth();
        // The two view icons (list / grid) sit at the right end of the tab row.
        int toggles=VIEW_BUTTON*2+4+8,tab=(w-16-toggles)/3;
        tabButton("주무기 · 핫바 1",x,57,tab,22,!presets&&primary,()->{presets=false;primary=true;page=0;gridScroll=0;followRandom=false;select(state.primary());});
        tabButton("보조무기 · 왼손",x+tab+8,57,tab,22,!presets&&!primary,()->{presets=false;primary=false;page=0;gridScroll=0;followRandom=false;select(state.secondary());});
        tabButton("프리셋",x+(tab+8)*2,57,w-toggles-(tab+8)*2,22,presets,()->{presets=true;followRandom=false;selectPreset(presetSlot);});
        if(!presets){
            button(Component.empty(),x+w-VIEW_BUTTON*2-4,57,VIEW_BUTTON,22,true,grid?MUTED:ACCENT,()->{grid=false;rebuildWidgets();});
            button(Component.empty(),x+w-VIEW_BUTTON,57,VIEW_BUTTON,22,true,grid?ACCENT:MUTED,()->{grid=true;rebuildWidgets();});
        }
        if(presets){initPresets(x,w,list);return;}
        if(grid){initActions(x,w,list);return;}
        page=Math.min(page,Math.max(0,weapons().size()-capacity()));
        for(int i=page;i<Math.min(weapons().size(),page+capacity());i++){
            var weapon=weapons().get(i);String equipped=primary?state.primary():state.secondary();
            boolean worn=equipped.equals(weapon.id());
            var label=Component.empty();
            if(worn)label.append(Component.literal("● ").withStyle(net.minecraft.ChatFormatting.GOLD));
            String holder=state.takenBy(primary,weapon.id());
            if(holder!=null)label.append(Component.literal("선점 ").withStyle(net.minecraft.ChatFormatting.RED,net.minecraft.ChatFormatting.BOLD));
            label.append(Component.literal(weapon.name()).withStyle(worn?net.minecraft.ChatFormatting.GOLD:holder!=null?net.minecraft.ChatFormatting.GRAY:net.minecraft.ChatFormatting.WHITE));
            if(holder!=null)label.append(Component.literal(" · "+holder).withStyle(net.minecraft.ChatFormatting.RED));
            if(ReleaseDateText.upcoming(weapon.releaseDate()))label.append(Component.literal(" ["+ReleaseDateText.UPCOMING+"]").withStyle(net.minecraft.ChatFormatting.AQUA));
            // The category is drawn right-aligned over the reserved padding in render().
            listButton(label,x,85+(i-page)*27,list-6,24,true,
                    weapon.id().equals(selected().id())?ACCENT:MUTED,9,categoryWidth(weapon)+6+silhouetteWidth(),()->clickWeapon(weapon.id()));
        }
        initActions(x,w,list);
    }
    private static final int VIEW_BUTTON=22;
    /** Weapons a teammate already holds while duplicates are off. */
    private static final int TAKEN=0xFFE05555;
    private void initActions(int x,int w,int list){
        button("랜덤 무기",x,height-48,list-6,22,state.allowed(),TacticalStyle.GOLD,()->{followRandom=true;LobbyClient.action("randomLoadout","","");});
        boolean worn=selected().id().equals(primary?state.primary():state.secondary());
        Runnable equip=()->LobbyClient.action(primary?"primary":"secondary",selected().id(),"");
        String holder=state.takenBy(primary,selected().id());
        if(holder!=null&&!worn)colorButton("선점됨 · "+holder,detailX(),height-48,Math.max(40,w-list-82),22,false,TAKEN,equip);
        else if(worn)button("장착 중인 무기",detailX(),height-48,Math.max(40,w-list-82),22,state.allowed(),ACCENT,equip);
        else colorButton("선택한 무기 장착",detailX(),height-48,Math.max(40,w-list-82),22,state.allowed(),ACCENT,equip);
        closeButton(x+w-64,height-48,64,22);
    }
    private int categoryWidth(Loadouts.Weapon weapon){return Math.min(font.width(weapon.category()),Math.max(0,(listWidth()-6)/3))+6;}
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
            nameBox=new TacticalEditBox(font,dx+8,PREVIEW_TOP+82,boxW,18,Component.literal("프리셋 이름"));
            nameBox.setMaxLength(20);nameBox.setValue(nameDraft);nameBox.setResponder(text->nameDraft=text);
            addRenderableWidget(nameBox);
            button("이름 변경",dx+12+boxW,PREVIEW_TOP+81,Math.max(40,Math.min(76,right-dx-boxW-18)),20,true,ACCENT,this::renamePreset);
        }else nameBox=null;
        String slot=Integer.toString(presetSlot);
        int bw=(Math.max(160,w-list-82)-12)/4,by=height-48;
        colorButton(preset==null?"현재 장비 저장":"현재 장비로 덮어쓰기",dx,by,bw,22,true,ACCENT,()->{confirmDelete=false;LobbyClient.action("presetSave",slot,"");});
        button("장착",dx+bw+4,by,bw,22,preset!=null&&state.allowed(),READY,()->LobbyClient.action("presetApply",slot,""));
        button(state.defaultPreset()==presetSlot?"기본값 해제":"기본값 설정",dx+(bw+4)*2,by,bw,22,preset!=null,TacticalStyle.GOLD,()->LobbyClient.action("presetDefault",slot,""));
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
        TacticalStyle.band(g,dx,85,right,18,fallback?TacticalStyle.GOLD:ACCENT,PANEL);
        int tagX=right-6;
        if(fallback){String tag="경기 시작 기본값";tagX-=font.width(tag)+8;badge(g,tag,tagX,89,TacticalStyle.GOLD);}
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
        g.fill(dx+4,cy-6,right-4,cy-5,TacticalStyle.HAIRLINE);
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
        TacticalStyle.band(g,dx,85,right,18,ACCENT,PANEL);
        int tagX=right-6;
        if(worn){String tag="장착 중";tagX-=font.width(tag)+8;badge(g,tag,tagX,89,ACCENT);tagX-=4;}
        String holder=worn?null:state.takenBy(primary,weapon.id());
        if(holder!=null){String tag="선점됨 · "+holder;tagX-=font.width(tag)+8;badge(g,tag,tagX,89,TAKEN);tagX-=4;}
        if(ReleaseDateText.upcoming(weapon.releaseDate())){String tag=ReleaseDateText.UPCOMING;tagX-=font.width(tag)+8;badge(g,tag,tagX,89,0xFF5FC8E0);tagX-=4;}
        var title=Component.literal(weapon.name()).withStyle(worn?net.minecraft.ChatFormatting.GOLD:net.minecraft.ChatFormatting.WHITE)
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
        g.fill(dx+4,top-3,right-4,top-2,TacticalStyle.HAIRLINE);
        var rows=new ArrayList<Row>();
        if(primary)paragraph(rows,"막대: 목록 내 같은 단위의 최댓값 기준",dw-16,true);
        if(weapon.releaseDate()!=null&&!weapon.releaseDate().isEmpty())paragraph(rows,"출시 "+ReleaseDateText.format(weapon.releaseDate()),dw-16,true);
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
            g.fill(right-2,top,right,bottom,TacticalStyle.TRACK);TacticalStyle.round(g,right-3,sy,3,h,ACCENT);
            if(scroll>0)g.fillGradient(dx,top,right-2,top+8,0xC0141511,0x00141511);
            if(scroll<maxScroll)g.fillGradient(dx,bottom-8,right-2,bottom,0x00141511,0xC0141511);
        }
        if(grid)renderGrid(g,mouseX,mouseY);
        else if(weapons().size()>capacity()){
            int track=capacity()*27-3,thumb=Math.max(12,track*capacity()/weapons().size()),ty=85+(track-thumb)*page/(weapons().size()-capacity());
            g.fill(x+listWidth()-3,85,x+listWidth()-2,85+track,TacticalStyle.TRACK);TacticalStyle.round(g,x+listWidth()-4,ty,3,thumb,ACCENT);
        }
        super.render(g,mouseX,mouseY,partial);
        viewIcons(g,x+w);
        if(!grid){
            renderSilhouettes(g,x);
            for(int i=page;i<Math.min(weapons().size(),page+capacity());i++){
                var item=weapons().get(i);int cw=categoryWidth(item)-6;String category=font.plainSubstrByWidth(item.category(),Math.max(1,cw));
                g.drawString(font,category,x+listWidth()-12-font.width(category),85+(i-page)*27+8,DIM,false);
            }
        }
    }
    // ---- list view: each row shows the weapon's side profile as a flat silhouette left of its category ----
    private static com.mojang.blaze3d.pipeline.TextureTarget silhouettes;
    private int silhouetteWidth(){int w=listWidth();return w<120?0:Math.min(56,Math.max(26,w/4));}
    /**
     * Draws every visible row's model into an offscreen target with the colour pushed far past 1 (each covered pixel
     * saturates to white, keeping the model's alpha), then lays that mask over the screen once, tinted: a flat shape
     * whatever the textures. The selected row's silhouette is lit in the accent colour.
     */
    private void renderSilhouettes(GuiGraphics g,int x){
        int sw=silhouetteWidth();if(sw==0)return;
        var window=minecraft.getWindow();int fw=window.getWidth(),fh=window.getHeight();
        String chosen=selected().id();
        for(int pass=0;pass<2;pass++){
            boolean any=false;
            for(int i=page;i<Math.min(weapons().size(),page+capacity());i++){
                var weapon=weapons().get(i);if(weapon.id().equals(chosen)!=(pass==1))continue;
                var stack=PreviewCache.item(weapon.id(),primary);if(stack==null||stack.isEmpty())continue;
                if(!any){
                    g.flush();
                    if(silhouettes==null)silhouettes=new com.mojang.blaze3d.pipeline.TextureTarget(fw,fh,true,net.minecraft.client.Minecraft.ON_OSX);
                    else if(silhouettes.width!=fw||silhouettes.height!=fh)silhouettes.resize(fw,fh,net.minecraft.client.Minecraft.ON_OSX);
                    silhouettes.setClearColor(0,0,0,0);silhouettes.clear(net.minecraft.client.Minecraft.ON_OSX);silhouettes.bindWrite(true);
                    com.mojang.blaze3d.systems.RenderSystem.setShaderColor(64,64,64,1);any=true;
                }
                int right=x+listWidth()-12-(categoryWidth(weapon)-6)-6;
                silhouette(g,stack,right-sw,85+(i-page)*27+3,sw,18);
            }
            if(!any)continue;
            g.flush();com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1);
            minecraft.getMainRenderTarget().bindWrite(true);
            int tint=pass==1?ACCENT:0xFFB8B8A8;float alpha=pass==1?.9f:.55f;
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
            com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
            com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0,silhouettes.getColorTextureId());
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor((tint>>16&255)/255f,(tint>>8&255)/255f,(tint&255)/255f,alpha);
            var matrix=g.pose().last().pose();var buffer=com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
            float w=width,h=height,z=350;
            buffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
            buffer.vertex(matrix,0,h,z).uv(0,0).endVertex();buffer.vertex(matrix,w,h,z).uv(1,0).endVertex();
            buffer.vertex(matrix,w,0,z).uv(1,1).endVertex();buffer.vertex(matrix,0,0,z).uv(0,1).endVertex();
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.end());
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1);com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        }
    }
    /** The model seen straight from the side, fitted to {@code w}×{@code h}. */
    private void silhouette(GuiGraphics g,net.minecraft.world.item.ItemStack stack,int x,int y,int w,int h){
        float[] box=bounds(stack);
        float dx=box==null?1:Math.max(.05f,box[3]-box[0]),dy=box==null?1:Math.max(.05f,box[4]-box[1]);
        float size=Math.min(w/dx,h/dy);
        // Clipped to the row button (its 24px height, the reserved width): a model whose measured bounds miss part of it
        // (a renderer that draws outside the probe) must not spill over neighbouring rows.
        g.enableScissor(x,y-2,x+w,y+h+2);
        g.pose().pushPose();g.pose().translate(x+w/2f,y+h/2f,150);g.pose().scale(size,-size,size);
        if(box!=null)g.pose().translate(-(box[0]+box[3])/2,-(box[1]+box[4])/2,-(box[2]+box[5])/2);
        Lighting.setupFor3DItems();
        minecraft.getItemRenderer().renderStatic(stack,ItemDisplayContext.FIXED,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,g.pose(),g.bufferSource(),minecraft.level,0);
        g.pose().popPose();
        g.flush();g.disableScissor();
    }
    /** List icon (three bars) and grid icon (2×2 squares) over the two view buttons. */
    private void viewIcons(GuiGraphics g,int right){
        int listX=right-VIEW_BUTTON*2-4+6,gridX=right-VIEW_BUTTON+6,top=57+6;
        int listColor=grid?MUTED:ACCENT,gridColor=grid?ACCENT:MUTED;
        g.pose().pushPose();g.pose().translate(0,0,400);
        for(int i=0;i<3;i++){g.fill(listX,top+i*4,listX+2,top+i*4+2,listColor);g.fill(listX+3,top+i*4,listX+10,top+i*4+2,listColor);}
        for(int i=0;i<2;i++)for(int j=0;j<2;j++)g.fill(gridX+i*6,top+j*6-1,gridX+i*6+4,top+j*6+3,gridColor);
        g.pose().popPose();
    }
    // ---- grid view: grouped by category, five square tiles a row ----
    private static final int COLUMNS=5,GAP=3,HEADER=14,GRID_TOP=85;
    /** A category header (weapon null) or a weapon tile, in content coordinates (y before scrolling). */
    private record Cell(Loadouts.Weapon weapon,String header,int x,int y){}
    private int gridBottom(){return height-54;}
    private int tileSize(){return Math.max(16,(listWidth()-10-GAP*(COLUMNS-1))/COLUMNS);}
    private List<Cell> gridCells(){
        var cells=new ArrayList<Cell>();int x=left()+12,tile=tileSize(),y=0,column=0;String category=null;
        for(var weapon:weapons()){
            if(!weapon.category().equals(category)){
                if(category!=null&&column>0)y+=tile+GAP;
                category=weapon.category();cells.add(new Cell(null,category,x,y));y+=HEADER;column=0;
            }
            cells.add(new Cell(weapon,null,x+column*(tile+GAP),y));
            if(++column==COLUMNS){column=0;y+=tile+GAP;}
        }
        if(column>0)y+=tile+GAP;
        gridMax=Math.max(0,y-(gridBottom()-GRID_TOP));gridScroll=Math.max(0,Math.min(gridScroll,gridMax));
        return cells;
    }
    private void renderGrid(GuiGraphics g,int mouseX,int mouseY){
        int x=left()+12,tile=tileSize(),areaRight=x+listWidth()-6,bottom=gridBottom();
        var cells=gridCells();String equipped=primary?state.primary():state.secondary(),chosen=selected().id();
        g.enableScissor(x,GRID_TOP,areaRight,bottom);
        for(var cell:cells){
            int y=GRID_TOP+cell.y()-gridScroll;
            if(y>bottom||y+(cell.weapon()==null?HEADER:tile)<GRID_TOP)continue;
            if(cell.weapon()==null){
                g.drawString(font,cell.header(),x,y+3,DIM,false);
                int line=x+font.width(cell.header())+6;if(line<areaRight-6)g.fill(line,y+7,areaRight-6,y+8,TacticalStyle.HAIRLINE);
                continue;
            }
            var weapon=cell.weapon();int cx=cell.x();
            boolean hover=mouseX>=cx&&mouseX<cx+tile&&mouseY>=y&&mouseY<y+tile&&mouseY>=GRID_TOP&&mouseY<bottom;
            boolean worn=weapon.id().equals(equipped),picked=weapon.id().equals(chosen);
            TacticalStyle.round(g,cx,y,tile,tile,0xFF1A1B15);
            g.fillGradient(cx+1,y+1,cx+tile-1,y+tile-1,hover?0xFF2E3024:0xFF24261D,0xFF11120E);
            // Each model stays inside its own tile (a long rifle used to spill over its neighbours); scissors nest,
            // so this also stays inside the grid area.
            g.flush();g.enableScissor(cx+1,y+1,cx+tile-1,y+tile-1);
            tileModel(g,weapon,cx,y,tile);
            g.flush();g.disableScissor();
            // Overlays above the 3D model (z 150).
            g.pose().pushPose();g.pose().translate(0,0,300);
            int edge=picked?ACCENT:worn?TacticalStyle.GOLD:hover?MUTED:0;
            if(edge!=0){g.fill(cx,y,cx+tile,y+1,edge);g.fill(cx,y+tile-1,cx+tile,y+tile,edge);g.fill(cx,y,cx+1,y+tile,edge);g.fill(cx+tile-1,y,cx+tile,y+tile,edge);}
            if(worn)g.fill(cx+3,y+3,cx+6,y+6,TacticalStyle.GOLD);
            if(ReleaseDateText.upcoming(weapon.releaseDate()))small(g,ReleaseDateText.UPCOMING,cx+(worn?8:3),y+3,.55f,0xFF5FC8E0);
            // Id small at the top right; name at the bottom left over a dark strip.
            float idScale=.55f,nameScale=.65f;
            String id=font.plainSubstrByWidth(weapon.id(),Math.max(1,(int)((tile-10)/idScale)));
            small(g,id,cx+tile-3-font.width(id)*idScale,y+3,idScale,0xFFC8B060);
            g.fill(cx+1,y+tile-9,cx+tile-1,y+tile-1,0x99000000);
            String name=font.plainSubstrByWidth(weapon.name(),Math.max(1,(int)((tile-5)/nameScale)));
            small(g,name,cx+3,y+tile-7,nameScale,worn?TacticalStyle.GOLD:INK);
            String holder=state.takenBy(primary,weapon.id());
            if(holder!=null&&!worn){
                // A teammate already holds it (duplicates off): dim the tile, red frame and a "선점됨" tag with the holder.
                g.fill(cx+1,y+1,cx+tile-1,y+tile-9,0xA0101418);
                if(!picked){g.fill(cx,y,cx+tile,y+1,TAKEN);g.fill(cx,y+tile-1,cx+tile,y+tile,TAKEN);g.fill(cx,y,cx+1,y+tile,TAKEN);g.fill(cx+tile-1,y,cx+tile,y+tile,TAKEN);}
                int mid=y+(tile-9)/2;
                g.fill(cx+1,mid-6,cx+tile-1,mid+7,0xCC5A1414);
                g.drawCenteredString(font,"선점됨",cx+tile/2,mid-4,0xFFFF8A8A);
                String who=font.plainSubstrByWidth(holder,Math.max(1,(int)((tile-6)/.6f)));
                small(g,who,cx+tile/2f-font.width(who)*.3f,mid+8,.6f,0xFFFFC4C4);
            }
            g.pose().popPose();
        }
        g.flush();g.disableScissor();
        if(gridMax>0){
            int track=bottom-GRID_TOP,thumb=Math.max(12,track*track/(track+gridMax)),ty=GRID_TOP+(track-thumb)*gridScroll/gridMax;
            g.fill(x+listWidth()-3,GRID_TOP,x+listWidth()-2,bottom,TacticalStyle.TRACK);TacticalStyle.round(g,x+listWidth()-4,ty,3,thumb,ACCENT);
        }
    }
    private void small(GuiGraphics g,String text,float x,float y,float scale,int color){
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);g.drawString(font,text,0,0,color,false);g.pose().popPose();
    }
    /** The weapon's model at the detail preview's resting angle, fitted to the tile. */
    private void tileModel(GuiGraphics g,Loadouts.Weapon weapon,int x,int y,int tile){
        var stack=PreviewCache.item(weapon.id(),primary);
        if(stack==null||stack.isEmpty()){String mark=stack==null?"…":"-";g.drawString(font,mark,x+(tile-font.width(mark))/2,y+tile/2-6,DIM,false);return;}
        float[] box=bounds(stack);
        float dx=box==null?1:box[3]-box[0],dy=box==null?1:box[4]-box[1],dz=box==null?0:box[5]-box[2];
        float diagonal=Math.max(.1f,(float)Math.sqrt(dx*dx+dy*dy+dz*dz)),size=tile*.95f/diagonal;
        g.pose().pushPose();g.pose().translate(x+tile/2f,y+tile/2f-3,150);
        g.pose().scale(size,-size,size);g.pose().mulPose(Axis.XP.rotationDegrees(12));g.pose().mulPose(Axis.YP.rotationDegrees(-25));
        if(box!=null)g.pose().translate(-(box[0]+box[3])/2,-(box[1]+box[4])/2,-(box[2]+box[5])/2);
        Lighting.setupFor3DItems();
        minecraft.getItemRenderer().renderStatic(stack,ItemDisplayContext.FIXED,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,g.pose(),g.bufferSource(),minecraft.level,0);
        g.flush();g.pose().popPose();
    }
    private boolean inGrid(double x,double y){int left=left()+12;return gridView()&&x>=left&&x<left+listWidth()-6&&y>=GRID_TOP&&y<gridBottom();}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0&&inGrid(x,y)){
            int tile=tileSize();
            for(var cell:gridCells()){
                if(cell.weapon()==null)continue;int cy=GRID_TOP+cell.y()-gridScroll;
                if(x>=cell.x()&&x<cell.x()+tile&&y>=cy&&y<cy+tile){
                    minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK,1));
                    clickWeapon(cell.weapon().id());return true;
                }
            }
            return true;
        }
        return super.mouseClicked(x,y,button);
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
        }else{
            rows.add(new Row("재사용 대기시간",weapon.cooldownSeconds()==0?"무제한":number(weapon.cooldownSeconds())+"초",-1,STAT_ROW));
            // Secondary specs come from the weapon JSON "stats"; bars compare against secondaries listing the same stat.
            for(var row:weapon.stats())stat(rows,row.name(),row.value(),v->v.stats().stream().filter(s->s.name().equals(row.name())).map(Loadouts.Stat::value).findFirst().orElse(""));
        }
        return rows;
    }
    /** One line per stat: label, bar, value. Rows without a comparable number show the value text only. */
    private static final List<String> FIXED_LABELS=List.of("최대 체력","이동 속도","피해량","명중률","안정성","사거리","연사력","재사용 대기시간");
    /** Widest label any weapon can show, so bar length does not depend on the selected weapon's text. */
    private int labelColumn(){
        int width=0;
        for(var label:FIXED_LABELS)width=Math.max(width,font.width(label));
        for(var weapon:state.catalog().secondary())for(var row:weapon.stats())width=Math.max(width,font.width(row.name()));
        return width;
    }
    /**
     * The bar runs the whole width after the label and the value sits over its right end, outlined in black, so the
     * bar shows however narrow the panel is.
     */
    /** Each stat bar's displayed fill by label, eased toward the selected weapon's value. */
    private static final java.util.Map<String,Float> STAT_SHOWN=new java.util.HashMap<>();
    private void statPanel(GuiGraphics g,List<Row> rows,int x1,int y1,int x2,int h){
        int labelW=labelColumn();
        int barX=x1+labelW+6,barEnd=x2,y=y1+Math.max(0,(h-rows.size()*STAT_ROW)/2)+3;
        g.enableScissor(x1,y1,x2,y1+h);
        for(var row:rows){
            g.drawString(font,row.label(),x1,y,MUTED,false);
            String value=font.plainSubstrByWidth(row.value(),Math.max(1,x2-barX));
            if(row.fraction()<0||barEnd-barX<8){g.drawString(font,value,row.fraction()<0?barX:x2-font.width(value),y,ACCENT,false);y+=STAT_ROW;continue;}
            // The XP bar's gold gauge; each bar eases to the newly selected weapon's value.
            float target=row.fraction(),shown=STAT_SHOWN.getOrDefault(row.label(),target);
            shown+=(target-shown)*.25f;if(Math.abs(target-shown)<.002f)shown=target;STAT_SHOWN.put(row.label(),shown);
            TacticalStyle.goldGauge(g,barX,y-1,barEnd-barX,10,shown,shown,false);
            outlined(g,value,x2-font.width(value)-1,y,INK);
            y+=STAT_ROW;
        }
        g.disableScissor();
    }
    /** Text with a one-pixel black outline, readable over the bar. */
    private void outlined(GuiGraphics g,String text,int x,int y,int color){
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)if(dx!=0||dy!=0)g.drawString(font,text,x+dx,y+dy,0xFF000000,false);
        g.drawString(font,text,x,y,color,false);
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
        TacticalStyle.round(g,x,y,w,h,0xFF1A1B15);
        g.fillGradient(x+1,y+1,x+w-1,y+h-1,0xFF24261D,0xFF11120E);
        for(int gx=x+(w/2)%16;gx<x+w;gx+=16)g.fill(gx,y+1,gx+1,y+h-1,0x0AFFFFFF);
        for(int gy=y+h-1;gy>y;gy-=16)g.fill(x+1,gy,x+w-1,gy+1,0x0AFFFFFF);
        // A soft floor glow under the model.
        g.fillGradient(x+1,y+h*2/3,x+w-1,y+h-1,0x00D0B75E,0x14D0B75E);
        TacticalStyle.brackets(g,x,y,w,h,8,ACCENT);
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
        if(inGrid(x,y)){gridCells();gridScroll=Math.max(0,Math.min(gridMax,gridScroll-(int)Math.signum(delta)*24));}
        else if(x<left()+12+listWidth()){page=Math.max(0,Math.min(Math.max(0,weapons().size()-capacity()),page-(int)Math.signum(delta)*2));rebuildWidgets();}
        else if(inPreview(x,y))zoom=Math.max(.3f,Math.min(3f,zoom+(float)delta*.1f));
        else scroll=Math.max(0,Math.min(maxScroll,scroll-(int)Math.signum(delta)*24));return true;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(presets)return super.mouseDragged(x,y,button,dx,dy);
        if(button==0&&inPreview(x,y)){yaw+=(float)dx*2;pitch=Math.max(-85,Math.min(85,pitch+(float)dy));return true;}
        int edge=left()+12+listWidth();
        if(button==0&&gridView()&&x>=edge-8&&x<=edge+2&&y>=GRID_TOP&&y<gridBottom()&&gridMax>0){
            gridScroll=Math.max(0,Math.min(gridMax,(int)((y-GRID_TOP)/(double)(gridBottom()-GRID_TOP)*gridMax)));return true;
        }
        if(button==0&&!gridView()&&x>=edge-8&&x<=edge+2&&weapons().size()>capacity()){
            page=Math.max(0,Math.min(weapons().size()-capacity(),(int)((y-85)/(capacity()*27.0)*(weapons().size()-capacity()))));rebuildWidgets();return true;
        }
        return super.mouseDragged(x,y,button,dx,dy);
    }
}
