package dev.gungang.acoustics.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Preserve vanilla editing, selection and narration; replace the field's chrome. */
final class TacticalEditBox extends EditBox {
    TacticalEditBox(Font font,int x,int y,int width,int height,Component title){
        super(font,x,y,width,height,title);setTextColor(TacticalStyle.INK);setTextColorUneditable(TacticalStyle.DIM);
    }
    @Override public void renderWidget(GuiGraphics g,int mx,int my,float partial){
        super.renderWidget(g,mx,my,partial);
        if(!isVisible())return;
        int color=!active?TacticalStyle.DIM:isFocused()?TacticalStyle.GOLD:TacticalStyle.EDGE;
        TacticalStyle.roundFrame(g,getX()-1,getY()-1,getWidth()+2,getHeight()+2,color);
        if(isFocused())g.fill(getX(),getY()+getHeight()-1,getX()+getWidth(),getY()+getHeight(),color);
    }
}
