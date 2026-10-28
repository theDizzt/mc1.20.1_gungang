package dev.gungang.acoustics.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
final class PropAbilityHud {
    static boolean ammo(GuiGraphics g,float partial){
        var row=PropHuntClient.ability();if(row==null||row.cooldownTotal()<=0)return false;
        drawAmmo(g,row,Math.max(0,row.cooldown()-PropHuntClient.elapsed(partial)));return true;
    }
    static void drawAmmo(GuiGraphics g,dev.gungang.acoustics.server.PropHuntSnapshot.Row row,float remaining){
        var mc=Minecraft.getInstance();
        int right=mc.getWindow().getWidth()/2-18,bottom=mc.getWindow().getHeight()/2-18,color=remaining>0?0xFFFFC36B:0xFF75D9AD;
        float scale=(float)(2/mc.getWindow().getGuiScale());g.pose().pushPose();g.pose().scale(scale,scale,1);
        String name=dev.gungang.acoustics.server.PropCatalog.name(row.block()).getString();g.drawString(mc.font,name,right-mc.font.width(name),bottom-46,0xFFFFFFFF,false);
        String label=remaining>0?String.format(java.util.Locale.ROOT,"%.1f",remaining/20f):"READY";
        g.pose().pushPose();g.pose().translate(right-mc.font.width(label)*2.5,bottom-30,0);g.pose().scale(2.5f,2.5f,1);g.drawString(mc.font,label,0,0,color,false);g.pose().popPose();
        g.fill(right-90,bottom-4,right,bottom-1,0xAA3A3B32);g.fill(right-90,bottom-4,right-90+Math.round(90*(1-remaining/row.cooldownTotal())),bottom-1,color);g.pose().popPose();
    }
    static int gauges(GuiGraphics g,int center,int y,int width,int height,float partial){
        var row=PropHuntClient.ability();if(row==null)return y;
        float active=Math.max(0,row.active()-PropHuntClient.elapsed(partial));
        if(active>0){UtilityHud.gauge(g,center,y,width,height,active,100,String.format(java.util.Locale.ROOT,"자유 이동 %.1f초",active/20),partial);y-=height+4;}
        if(row.auxiliaryMax()>0){UtilityHud.gauge(g,center,y,width,height,row.auxiliary(),row.auxiliaryMax(),"독거미 "+HudHealth.number(row.auxiliary())+"/"+HudHealth.number(row.auxiliaryMax()),partial);y-=height+4;}
        return y;
    }
    private PropAbilityHud(){}
}
