package dev.gungang.acoustics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Gallery opened by /gungang emblem: one row per shape bracket, the player's own level outlined. */
public final class LevelEmblemScreen extends Screen {
    private static final int LABEL=78;
    public LevelEmblemScreen() {super(Component.literal("레벨 엠블럼"));}
    public static void open() {Minecraft.getInstance().setScreen(new LevelEmblemScreen());}

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
        renderBackground(g);
        int cell=Math.max(12,Math.min(30,Math.min((height-48)/10,(width-LABEL-16)/10)));
        int left=(width-LABEL-cell*10)/2,top=Math.max(28,(height-cell*10)/2+8),own=ProgressionHud.level();
        g.drawCenteredString(font,title,width/2,top-20,0xFFFFFFFF);
        int hovered=0;
        for(int row=0;row<10;row++) {
            int y=top+row*cell;
            g.drawString(font,(row*10+1)+(row==9?"":"~"+(row*10+10))+" "+LevelEmblem.SHAPES[row],left,y+(cell-8)/2,0xFFD8D8D8,true);
            for(int column=0;column<10;column++) {
                int level=row*10+column+1;if(level>91)break;
                int x=left+LABEL+column*cell,pad=Math.max(1,cell/12);
                if(level==own)g.renderOutline(x,y,cell,cell,0xFF70CDBF);
                LevelEmblem.draw(g,level,x+pad,y+pad,cell-pad*2);
                if(mouseX>=x&&mouseX<x+cell&&mouseY>=y&&mouseY<y+cell)hovered=level;
            }
        }
        if(hovered>0)g.renderTooltip(font,Component.literal("Lv. "+hovered+" · "+LevelEmblem.SHAPES[LevelEmblem.shape(hovered)]+" · "
                +(hovered==91?"은하수":LevelEmblem.COLORS[LevelEmblem.digit(hovered)])),mouseX,mouseY);
        super.render(g,mouseX,mouseY,partialTick);
    }
    @Override public boolean isPauseScreen() {return false;}
}
