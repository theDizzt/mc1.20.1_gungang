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
    private long lastClickTime;
    private void clickWeapon(String id) {
        long now=net.minecraft.Util.getMillis();
        boolean twice=lastClicked.equals((primary?"primary:":"secondary:")+id)&&now-lastClickTime<250;
        lastClicked=(primary?"primary:":"secondary:")+id;lastClickTime=now;
        select(id);
        if(twice&&state.allowed()) {LobbyClient.action(primary?"primary":"secondary",id,"");lastClicked="";}
    }
    private float yaw=-25,pitch=12,zoom=1;
    LoadoutScreen(EquipmentSnapshot state){super("GUNGANG / LOADOUT");this.state=state;selected=state.primary();PreviewCache.clear();}
    void update(EquipmentSnapshot next){state=next;rebuildWidgets();}
    private List<Loadouts.Weapon> weapons(){return primary?state.catalog().primary():state.catalog().secondary();}
    private int capacity(){return Math.max(1,(height-140)/27);}
    private int listWidth(){return Math.max(90,(body()-24)/3);}
    private int detailX(){return left()+22+listWidth();}
    private int previewHeight(){return Math.max(32,Math.min(105,(height-180)/3));}
    private Loadouts.Weapon selected(){return weapons().stream().filter(w->w.id().equals(selected)).findFirst().orElse(weapons().get(0));}
    private void select(String id){selected=id;scroll=0;yaw=-25;pitch=12;zoom=1;rebuildWidgets();}
    @Override protected void init(){
        int x=left()+12,w=body()-24,list=listWidth();
        button("주무기 · 핫바 1",x,57,(w-8)/2,22,true,primary?ACCENT:MUTED,()->{primary=true;page=0;select(state.primary());});
        button("보조무기 · 왼손",x+(w+8)/2,57,(w-8)/2,22,true,!primary?ACCENT:MUTED,()->{primary=false;page=0;select(state.secondary());});
        page=Math.min(page,Math.max(0,weapons().size()-capacity()));
        for(int i=page;i<Math.min(weapons().size(),page+capacity());i++){
            var weapon=weapons().get(i);String equipped=primary?state.primary():state.secondary();
            String name=font.plainSubstrByWidth("["+weapon.category()+"] "+weapon.name(),Math.max(1,list-20));
            var label=Component.literal(name).withStyle(equipped.equals(weapon.id())?net.minecraft.ChatFormatting.GREEN:net.minecraft.ChatFormatting.WHITE);
            button(label,x,85+(i-page)*27,list-6,24,true,
                    weapon.id().equals(selected().id())?ACCENT:MUTED,()->clickWeapon(weapon.id()));
        }
        button("선택한 무기 장착",detailX(),height-48,Math.max(40,w-list-82),22,state.allowed(),ACCENT,()->LobbyClient.action(primary?"primary":"secondary",selected().id(),""));
        closeButton(x+w-64,height-48,64,22);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        shell(g,state.team()+"  /  "+(state.preparationSeconds()>0?"출격까지 "+state.preparationSeconds()+"초":state.spawnHint()?"팀 스폰 구역":"무기 선택")+"  /  "+LobbyClient.LOADOUT.getTranslatedKeyMessage().getString()+"로 다시 열기");
        int x=left()+12,w=body()-24,dx=detailX(),right=x+w,dw=right-dx;
        var weapon=selected();g.fill(dx,85,right,height-57,0xFF101A28);
        var title=Component.literal(weapon.name()).withStyle(weapon.id().equals(primary?state.primary():state.secondary())?net.minecraft.ChatFormatting.GREEN:net.minecraft.ChatFormatting.WHITE)
                .append(Component.literal(" [id: "+weapon.id()+"]").withStyle(net.minecraft.ChatFormatting.YELLOW));
        g.drawString(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(title,dw-12)),dx+6,91,INK,false);
        g.drawString(font,font.plainSubstrByWidth(weapon.category()+" · 드래그 회전 / 휠 확대",dw-12),dx+6,104,MUTED,false);
        preview(g,weapon,dx+4,117,dw-8,previewHeight());
        int top=125+previewHeight(),bottom=height-57;
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
            paragraph(rows,"막대: 목록 내 같은 단위의 최댓값 기준",dw-16);
        }else rows.add(new Row("재사용 대기시간",weapon.cooldownSeconds()==0?"무제한":number(weapon.cooldownSeconds())+"초",-1,23));
        paragraph(rows,weapon.description(),dw-16);paragraph(rows,"사용방법",dw-16);paragraph(rows,weapon.usage(),dw-16);
        int total=rows.stream().mapToInt(Row::height).sum();maxScroll=Math.max(0,total-(bottom-top));scroll=Math.min(scroll,maxScroll);
        g.enableScissor(dx,top,right,bottom);int y=top-scroll;
        for(var row:rows){
            if(row.height()<=12)g.drawString(font,row.label(),dx+8,y,MUTED,false);
            else {
                g.drawString(font,row.label(),dx+8,y,INK,false);
                String value=font.plainSubstrByWidth(row.value(),Math.max(1,dw-font.width(row.label())-28));
                g.drawString(font,value,right-8-font.width(value),y,ACCENT,false);
                g.fill(dx+8,y+13,right-8,y+17,0xFF29394D);
                if(row.fraction()>=0)g.fill(dx+8,y+13,dx+8+Math.round((dw-16)*row.fraction()),y+17,ACCENT);
            }y+=row.height();
        }
        g.flush();g.disableScissor();
        if(maxScroll>0){int h=Math.max(8,(bottom-top)*(bottom-top)/Math.max(1,total));int sy=top+(bottom-top-h)*scroll/maxScroll;g.fill(right-2,sy,right,sy+h,ACCENT);}
        if(weapons().size()>capacity()){
            int track=capacity()*27-3,thumb=Math.max(12,track*capacity()/weapons().size()),ty=85+(track-thumb)*page/(weapons().size()-capacity());
            g.fill(x+listWidth()-4,85,x+listWidth()-1,85+track,0xFF101A28);g.fill(x+listWidth()-4,ty,x+listWidth()-1,ty+thumb,ACCENT);
        }
        super.render(g,mouseX,mouseY,partial);
    }
    private record Row(String label,String value,float fraction,int height) { }
    private void stat(List<Row> rows,String label,String value,Function<Loadouts.Weapon,String> accessor){rows.add(new Row(label,value,WeaponStatScale.fraction(value,weapons().stream().map(accessor).toList()),23));}
    private void paragraph(List<Row> rows,String text,int width){
        for(String line:WeaponText.lines(text))for(var part:font.split(Component.literal(line),Math.max(1,width))) {
            var out=new StringBuilder();part.accept((index,style,cp)->{out.appendCodePoint(cp);return true;});rows.add(new Row(out.toString(),"",-1,12));
        }
        rows.add(new Row("","",-1,6));
    }
    private static String number(double value){return String.format(Locale.ROOT,value==Math.rint(value)?"%.0f":"%.1f",value);}
    private void preview(GuiGraphics g,Loadouts.Weapon weapon,int x,int y,int w,int h){
        g.fill(x,y,x+w,y+h,0xFF19283A);var stack=PreviewCache.item(weapon.id(),primary);
        if(stack==null||stack.isEmpty()){
            String message=stack==null?"모델 불러오는 중…":"표시할 아이템 모델이 없습니다";
            g.drawString(font,font.plainSubstrByWidth(message,Math.max(1,w-8)),x+4,y+h/2-4,MUTED,false);return;
        }
        g.flush();g.enableScissor(x,y,x+w,y+h);g.pose().pushPose();
        g.pose().translate(x+w/2f,y+h/2f,150);float size=Math.min(w*.55f,h*.9f)*zoom;
        g.pose().scale(size,-size,size);g.pose().mulPose(Axis.XP.rotationDegrees(pitch));g.pose().mulPose(Axis.YP.rotationDegrees(yaw));
        Lighting.setupFor3DItems();
        minecraft.getItemRenderer().renderStatic(stack,ItemDisplayContext.FIXED,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,g.pose(),g.bufferSource(),minecraft.level,0);
        g.flush();g.pose().popPose();g.disableScissor();Lighting.setupFor3DItems();
    }
    private boolean inPreview(double x,double y){return x>=detailX()&&x<left()+body()-12&&y>=117&&y<117+previewHeight();}
    @Override public boolean mouseScrolled(double x,double y,double delta){
        if(x<left()+12+listWidth()){page=Math.max(0,Math.min(Math.max(0,weapons().size()-capacity()),page-(int)Math.signum(delta)*2));rebuildWidgets();}
        else if(inPreview(x,y))zoom=Math.max(.3f,Math.min(3f,zoom+(float)delta*.1f));
        else scroll=Math.max(0,Math.min(maxScroll,scroll-(int)Math.signum(delta)*24));return true;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(button==0&&inPreview(x,y)){yaw+=(float)dx*2;pitch=Math.max(-85,Math.min(85,pitch+(float)dy));return true;}
        int edge=left()+12+listWidth();
        if(button==0&&x>=edge-8&&x<=edge+2&&weapons().size()>capacity()){
            page=Math.max(0,Math.min(weapons().size()-capacity(),(int)((y-85)/(capacity()*27.0)*(weapons().size()-capacity()))));rebuildWidgets();return true;
        }
        return super.mouseDragged(x,y,button,dx,dy);
    }
}
