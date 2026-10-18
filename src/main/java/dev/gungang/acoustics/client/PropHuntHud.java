package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.world.item.ItemStack;

/** Prop hunt in the deathmatch/escort style: role plates around the LCD clock, a disguise tile row, and two TAB cards. */
final class PropHuntHud {
    static final int HIDER=0xFF75D9AD,SEEKER=0xFFFFB86C,FOUND=0xFFFF6B6B,DIM=0xFF5F6E80;
    private static final int TILE=18,GAP=3,HEAD=28,COLS=13,ROW=18,FOOT=13,SPLIT=8;
    /** A TAB row from either snapshot; found is null when the source does not know it (match results). */
    private record Entry(String id,String name,boolean hider,Boolean found,int seconds,int finds) { }

    static void draw(GuiGraphics g,PropHuntSnapshot s) {
        var mc=Minecraft.getInstance();int top=Math.max(2,BossBarHud.bottom+1);
        float scale=Math.min(.8f,(g.guiWidth()-12)/232f);
        var hiders=s.players().stream().filter(r->r.team().equals("ALPHA")).toList();
        long alive=hiders.stream().filter(r->!r.found()).count(),seekers=s.players().size()-hiders.size();
        int finds=s.players().stream().filter(r->!r.team().equals("ALPHA")).mapToInt(PropHuntSnapshot.Row::finds).sum();
        boolean review="REVIEW".equals(s.phase()),hiding="HIDING".equals(s.phase());
        g.pose().pushPose();g.pose().translate(g.guiWidth()/2f,top,0);g.pose().scale(scale,scale,1);
        // The seek timer stays separate from the hiding clock: shown on the seeker plate until the hunt starts.
        String change=review?"종료":s.changeSeconds()>0?"변신 "+MatchReport.time(s.changeSeconds()):"변신 없음";
        String seek=hiding?"찾는 시간 "+MatchReport.time(s.huntSeconds()):s.rush()?"이동속도 +35%":seekers+"명 · 발견";
        plate(g,true,"숨는 팀","HIDE",HIDER,String.valueOf(alive),change,hiding);
        plate(g,false,"술래","SEEK",SEEKER,String.valueOf(finds),seek,!hiding&&!review);
        DeathmatchHud.small(g,review?"위치 확인":hiding?"숨는 시간":s.rush()?"마지막 1분":"찾는 시간",0,2,.75f,review?DeathmatchHud.MUTED:hiding?HIDER:s.rush()?0xFFFF6B6B:SEEKER,true);
        MatchTimerHud.drawAt(g,MatchReport.time(s.seconds()),0,11);
        g.pose().popPose();
        tiles(g,hiders,top+Math.round(34*scale)+3);
        if(mc.screen==null&&mc.options.keyPlayerList.isDown()) {
            int rows=(hiders.size()+columns(g)-1)/columns(g),y=top+Math.round(34*scale)+6+rows*(TILE+GAP)+4;
            int w=Math.min(480,g.guiWidth()-8);
            PropHuntClient.clampPage(board(g,s.players().stream().map(r->new Entry(r.id(),r.name(),r.team().equals("ALPHA"),r.found(),r.hiddenSeconds(),r.finds())).toList(),
                    review,Math.max(4,(g.guiWidth()-w)/2),y,w,g.guiHeight()-y-8,PropHuntClient.page(),true));
        }
    }
    /** Deathmatch plate; the big number is a head count instead of a score, the tag names the role. */
    private static void plate(GuiGraphics g,boolean left,String name,String tag,int color,String value,String note,boolean active) {
        int inner=36,outer=116,y1=3,y2=27,x1=left?-outer:inner,x2=left?-inner:outer;
        DeathmatchHud.plate(g,x1,y1,x2,y2,color,left,active);
        var font=Minecraft.getInstance().font;
        float size=Math.min(1.8f,34f/Math.max(1,font.width(value)));int width=Math.round(font.width(value)*size);
        g.pose().pushPose();g.pose().translate(left?x2-8-width:x1+8,y1+12-4*size,0);g.pose().scale(size,size,1);
        g.drawString(font,value,0,0,active?0xFFFFFFFF:0xFFC4CEDA,false);g.pose().popPose();
        int edge=left?x1+7:x2-7;
        DeathmatchHud.small(g,name,edge,y1+5,.75f,0xFFFFFFFF,false,left);
        int w=Math.round(font.width(tag)*.6f)+6,tx=left?edge:edge-w;
        g.fill(tx,y1+14,tx+w,y1+21,(color&0xFFFFFF)|0x40000000);g.fill(tx,y1+14,tx+1,y1+21,color);
        DeathmatchHud.small(g,tag,tx+3,y1+15,.6f,color,false,true);
        DeathmatchHud.small(g,note,left?tx+w+3:tx-3,y1+15,.6f,DeathmatchHud.MUTED,false,left);
    }
    private static int columns(GuiGraphics g){return Math.max(1,Math.min(12,(g.guiWidth()-24)/(TILE+GAP)));}
    /** Centred disguise tiles: own tile outlined, unknown disguises as "?", found hiders dimmed and struck through. */
    private static void tiles(GuiGraphics g,List<PropHuntSnapshot.Row> hiders,int y) {
        if(hiders.isEmpty())return;
        var mc=Minecraft.getInstance();String self=mc.player==null?"":mc.player.getUUID().toString();
        int columns=columns(g);
        for(int i=0;i<hiders.size();i++) {
            var row=hiders.get(i);int inRow=Math.min(columns,hiders.size()-i/columns*columns);
            int x=g.guiWidth()/2-(inRow*(TILE+GAP)-GAP)/2+(i%columns)*(TILE+GAP),ty=y+i/columns*(TILE+GAP);
            boolean mine=row.id().equals(self);
            g.fill(x-1,ty-1,x+TILE+1,ty+TILE+1,mine?HIDER:row.found()?0xB45A2A2A:DeathmatchHud.EDGE);
            g.fill(x,ty,x+TILE,ty+TILE,DeathmatchHud.SURFACE);
            g.fill(x,ty+TILE-2,x+TILE,ty+TILE,row.found()?FOUND:HIDER);
            var item=row.block().isEmpty()?null:net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(PropCatalog.id(row.block()));
            if(item==null)g.drawCenteredString(mc.font,"?",x+TILE/2,ty+5,DeathmatchHud.MUTED);
            else {
                g.renderItem(new ItemStack(item),x+1,ty+1);
                // Catalog number at the bottom right, above the item (z 150) and below the found overlay (z 200).
                g.pose().pushPose();g.pose().translate(0,0,180);
                DeathmatchHud.small(g,PropCatalog.number(row.block()),x+TILE-1,ty+TILE-7,.5f,0xFFFFFFFF,false,false);
                g.pose().popPose();
            }
            if(row.found()) {
                // Items render at z 150; the strike sits above them.
                g.pose().pushPose();g.pose().translate(0,0,200);
                g.fill(x,ty,x+TILE,ty+TILE,0x900B121C);
                for(int d=2;d<TILE-2;d++){g.fill(x+d,ty+d,x+d+1,ty+d+1,FOUND);g.fill(x+TILE-1-d,ty+d,x+TILE-d,ty+d+1,FOUND);}
                g.pose().popPose();
            }
        }
    }

    static int capacity(int height){return Math.max(1,(height-HEAD-COLS-FOOT)/ROW);}
    private static int pages(long hiders,long seekers,int height){int c=capacity(height);return Math.max(1,(int)((Math.max(hiders,seekers)+c-1)/c));}
    static int pages(MatchSnapshot s,int height){long h=s.players().stream().filter(r->r.team().equals("ALPHA")).count();return pages(h,s.players().size()-h,height);}
    /** Result screen: MatchSnapshot rows carry finds in kills and hidden time in playSeconds. */
    static int table(GuiGraphics g,MatchSnapshot s,int x,int y,int width,int height,int page) {
        return board(g,s.players().stream().map(r->new Entry(r.id(),r.name(),r.team().equals("ALPHA"),null,r.playSeconds(),r.kills())).toList(),true,x,y,width,height,page,false);
    }
    /** Hider and seeker cards side by side, sharing one page index like the team TAB board. */
    private static int board(GuiGraphics g,List<Entry> entries,boolean finished,int x,int y,int width,int height,int requested,boolean live) {
        var hiders=entries.stream().filter(Entry::hider).sorted(Comparator.comparing((Entry e)->Boolean.TRUE.equals(e.found())).thenComparing(Comparator.comparingInt(Entry::seconds).reversed())).toList();
        var seekers=entries.stream().filter(e->!e.hider()).sorted(Comparator.comparingInt(Entry::finds).reversed().thenComparing(Entry::name)).toList();
        int capacity=capacity(height),pages=pages(hiders.size(),seekers.size(),height),page=Math.min(requested,pages-1),half=(width-SPLIT)/2;
        int shown=Math.max(1,Math.max(Math.min(capacity,hiders.size()-page*capacity),Math.min(capacity,seekers.size()-page*capacity)));
        int bottom=y+HEAD+COLS+shown*ROW+(pages>1?FOOT:4);
        long alive=hiders.stream().filter(e->!Boolean.TRUE.equals(e.found())).count();
        card(g,true,"숨는 팀",HIDER,live?"생존 "+alive+" / "+hiders.size():hiders.size()+"명",live?String.valueOf(alive):String.valueOf(hiders.size()),hiders,x,y,half,bottom,page,capacity,pages);
        int finds=seekers.stream().mapToInt(Entry::finds).sum();
        card(g,false,"술래",SEEKER,seekers.size()+"명 · "+finds+"명 발견",String.valueOf(finds),seekers,x+half+SPLIT,y,half,bottom,page,capacity,pages);
        return pages;
    }
    private static void card(GuiGraphics g,boolean hider,String title,int color,String subtitle,String total,List<Entry> rows,
                             int left,int top,int width,int bottom,int page,int capacity,int pages) {
        var mc=Minecraft.getInstance();var font=mc.font;int right=left+width,rgb=color&0xFFFFFF;
        g.fill(right,top+2,right+2,bottom+2,0x40000000);g.fill(left+2,bottom,right,bottom+2,0x40000000);
        g.fillGradient(left,top,right,bottom,0xA6131D2B,0xA60B121C);
        g.fillGradient(left,top,right,top+HEAD,rgb|0x50000000,rgb|0x08000000);
        g.fill(left,top,right,top+2,color);g.fill(left+8,top+9,left+10,top+19,color);
        g.drawString(font,title,left+14,top+7,0xFFFFFFFF,false);
        g.drawString(font,subtitle,left+14,top+17,0xFF9AAABD,false);
        g.pose().pushPose();g.pose().translate(right-8-font.width(total)*2,top+6,0);g.pose().scale(2,2,1);
        g.drawString(font,total,0,0,0xFFFFFFFF,false);g.pose().popPose();
        // Hiders: status + hidden time; seekers: find count. Right-anchored so names keep the rest.
        int value=right-8,status=value-52,face=left+26,nameX=face+16,cy=top+HEAD,nameEnd=hider?status-34:value-30;
        g.fill(left,cy,right,cy+COLS,0x30000000);g.fill(left,cy+COLS-1,right,cy+COLS,0xFF22324A);
        g.drawString(font,"#",left+8,cy+3,DIM,false);g.drawString(font,"플레이어",face,cy+3,DIM,false);
        if(hider){MatchHud.column(g,"상태",status,cy+3,DIM);MatchHud.column(g,"숨은 시간",value,cy+3,DIM);}
        else MatchHud.column(g,"발견",value,cy+3,DIM);
        String self=mc.player==null?"":mc.player.getUUID().toString();
        int start=page*capacity,count=Math.max(0,Math.min(capacity,rows.size()-start));
        if(count==0)g.drawString(font,"플레이어 없음",left+(width-font.width("플레이어 없음"))/2,cy+COLS+5,DIM,false);
        for(int i=0;i<count;i++) {
            var row=rows.get(start+i);int ry=cy+COLS+i*ROW,rank=start+i+1;boolean mine=row.id().equals(self),found=Boolean.TRUE.equals(row.found());
            if(mine){g.fill(left,ry,right,ry+ROW,rgb|0x38000000);g.fill(left,ry,left+2,ry+ROW,color);}
            else if(i%2==1)g.fill(left,ry,right,ry+ROW,0x10FFFFFF);
            String number=String.valueOf(rank);
            g.drawString(font,number,left+8+(10-font.width(number))/2,ry+5,rank==1&&(hider?row.seconds()>0:row.finds()>0)?0xFFFFD166:DIM,false);
            g.fill(face-1,ry+2,face+13,ry+16,mine?color:found?0xFF5A2A2A:0xFF2A3A50);
            PlayerFaceRenderer.draw(g,MatchHud.skin(row.id()),face,ry+3,12);
            if(found)g.fill(face,ry+3,face+12,ry+15,0x900B121C);
            g.drawString(font,font.plainSubstrByWidth(row.name(),Math.max(1,nameEnd-nameX)),nameX,ry+5,mine?0xFFFFFFFF:found?0xFF8A96A6:0xFFE2E8F0,false);
            if(hider) {
                if(row.found()!=null)MatchHud.column(g,found?"발견됨":"생존",status,ry+5,found?FOUND:HIDER);
                MatchHud.column(g,MatchReport.time(row.seconds()),value,ry+5,0xFFFFFFFF);
            } else MatchHud.column(g,row.finds()+"명",value,ry+5,0xFFFFFFFF);
        }
        if(pages>1) {
            String label=(page+1)+" / "+pages+" · 휠";
            g.fill(left+8,bottom-FOOT,right-8,bottom-FOOT+1,0xFF22324A);
            g.drawString(font,label,right-8-font.width(label),bottom-FOOT+3,DIM,false);
        }
    }
    private PropHuntHud() { }
}
