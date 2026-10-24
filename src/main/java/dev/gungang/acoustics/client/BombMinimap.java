package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Bomb mode TAB minimap: the map's generated preview image (when it has a previewArea), A/B site areas,
 * the planted bomb and living teammates. The server sends only the viewer's own team, so enemies never show.
 */
final class BombMinimap {
    private static final int GOLD=0xFFD29B14,SITE_FILL=0x40D29B14,BOMB=0xFFFF4B3E;
    /** World bounds shown, as [minX,minZ,maxX,maxZ] (max exclusive). */
    private static double[] bounds(BombMatch.View v){
        var a=v.previewArea();
        if(a!=null)return new double[]{a.minX(),a.minZ(),a.maxX()+1,a.maxZ()+1};
        // No previewArea: frame the sites and the players with a margin.
        double minX=Double.MAX_VALUE,minZ=Double.MAX_VALUE,maxX=-Double.MAX_VALUE,maxZ=-Double.MAX_VALUE;
        for(var s:new BombRules.Site[]{v.areaA(),v.areaB()})if(s!=null){minX=Math.min(minX,s.minX());minZ=Math.min(minZ,s.minZ());maxX=Math.max(maxX,s.maxX()+1);maxZ=Math.max(maxZ,s.maxZ()+1);}
        for(var p:new BombRules.Point[]{v.pointA(),v.pointB()})if(p!=null){minX=Math.min(minX,p.x());minZ=Math.min(minZ,p.z());maxX=Math.max(maxX,p.x()+1);maxZ=Math.max(maxZ,p.z()+1);}
        for(var m:v.mates()==null?java.util.List.<BombMatch.Mate>of():v.mates()){minX=Math.min(minX,m.x());minZ=Math.min(minZ,m.z());maxX=Math.max(maxX,m.x());maxZ=Math.max(maxZ,m.z());}
        if(minX>maxX)return null;
        double pad=8+Math.max(maxX-minX,maxZ-minZ)*.08;
        return new double[]{minX-pad,minZ-pad,maxX+pad,maxZ+pad};
    }
    /** Draws inside the size x size box at (x,y), keeping the map's aspect ratio. */
    static void draw(GuiGraphics g,MatchSnapshot state,int x,int y,int size){
        var v=state.bomb();var b=v==null?null:bounds(v);if(b==null||size<40)return;
        var mc=Minecraft.getInstance();var font=mc.font;
        double spanX=b[2]-b[0],spanZ=b[3]-b[1],scale=size/Math.max(spanX,spanZ);
        int w=(int)Math.round(spanX*scale),h=(int)Math.round(spanZ*scale),left=x+(size-w)/2,top=y+(size-h)/2;
        g.fill(left-2,top-13,left+w+2,top+h+2,0xC00B121C);g.fill(left-2,top+h+1,left+w+2,top+h+2,0xFF70CDBF);
        g.drawString(font,"미니맵",left,top-11,0xFF9AAABD,false);
        background(g,v,left,top,w,h);
        // Sites: tinted area with a gold outline (legacy single points as one block), labelled at the centre.
        for(int i=0;i<2;i++){
            var site=i==0?v.areaA():v.areaB();var point=i==0?v.pointA():v.pointB();String name=i==0?"A":"B";
            double x0,z0,x1,z1;
            if(site!=null){x0=site.minX();z0=site.minZ();x1=site.maxX()+1;z1=site.maxZ()+1;}
            else if(point!=null){x0=point.x()-1;z0=point.z()-1;x1=point.x()+2;z1=point.z()+2;}
            else continue;
            int sx0=sx(x0,b,left,scale),sz0=sz(z0,b,top,scale),sx1=Math.max(sx0+2,sx(x1,b,left,scale)),sz1=Math.max(sz0+2,sz(z1,b,top,scale));
            g.fill(sx0,sz0,sx1,sz1,SITE_FILL);
            g.fill(sx0,sz0,sx1,sz0+1,GOLD);g.fill(sx0,sz1-1,sx1,sz1,GOLD);g.fill(sx0,sz0,sx0+1,sz1,GOLD);g.fill(sx1-1,sz0,sx1,sz1,GOLD);
            g.drawCenteredString(font,name,(sx0+sx1)/2,(sz0+sz1)/2-4,GOLD);
        }
        if(v.plantedPosition()!=null){
            var p=v.plantedPosition();int px=sx(p.x()+.5,b,left,scale),pz=sz(p.z()+.5,b,top,scale);
            g.fill(px-3,pz-3,px+3,pz+3,0xFF000000);g.fill(px-2,pz-2,px+2,pz+2,BOMB);
        }
        var options=state.options()==null?TeamOptions.defaults():state.options();
        String self=mc.player==null?"":mc.player.getUUID().toString();
        for(var m:v.mates()==null?java.util.List.<BombMatch.Mate>of():v.mates()){
            int px=sx(m.x(),b,left,scale),pz=sz(m.z(),b,top,scale);
            if(px<left||px>=left+w||pz<top||pz>=top+h)continue;
            boolean me=m.id().equals(self);int color=me?0xFFFFFFFF:options.color(m.team())|0xFF000000,r=me?3:2;
            g.fill(px-r-1,pz-r-1,px+r+1,pz+r+1,0xFF000000);g.fill(px-r,pz-r,px+r,pz+r,color);
            // Facing tick: Minecraft yaw 0 looks toward +Z (down on the map).
            double yaw=Math.toRadians(m.yaw());int dx=(int)Math.round(-Math.sin(yaw)*(r+3)),dz=(int)Math.round(Math.cos(yaw)*(r+3));
            g.fill(px+dx-1,pz+dz-1,px+dx+1,pz+dz+1,color);
        }
        TeamPointHud.minimap(g,b,left,top,w,h,scale);
    }
    /** The generated preview image's map region (it has a legend strip below), or a plain surface. */
    private static void background(GuiGraphics g,BombMatch.View v,int left,int top,int w,int h){
        var a=v.previewArea();var image=a==null||v.mapId()==null||v.mapId().isEmpty()?null:PreviewCache.image(v.mapId());
        if(image==null||image.texture()==null){g.fill(left,top,left+w,top+h,0xFF1A222C);return;}
        // Same layout as MapPreviewGenerator.Job: map pixels are width*unit by height*unit at the top-left.
        int dx=a.maxX()-a.minX()+1,dz=a.maxZ()-a.minZ()+1;double scale=Math.min(1,256.0/Math.max(dx,dz));
        int width=Math.max(1,(int)Math.ceil(dx*scale)),height=Math.max(1,(int)Math.ceil(dz*scale)),unit=Math.max(2,(int)Math.ceil(256.0/Math.max(width,height)));
        g.blit(image.texture(),left,top,w,h,0,0,width*unit,height*unit,image.width(),image.height());
    }
    private static int sx(double world,double[] b,int left,double scale){return left+(int)Math.round((world-b[0])*scale);}
    private static int sz(double world,double[] b,int top,double scale){return top+(int)Math.round((world-b[1])*scale);}
    private BombMinimap(){}
}
