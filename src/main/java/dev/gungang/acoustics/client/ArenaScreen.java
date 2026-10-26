package dev.gungang.acoustics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

abstract class ArenaScreen extends Screen {
    static final int INK = TacticalStyle.INK, MUTED = TacticalStyle.MUTED, ACCENT = TacticalStyle.GOLD;
    static final int DIM = TacticalStyle.DIM, PANEL = TacticalStyle.PANEL, BORDER = TacticalStyle.EDGE, READY = TacticalStyle.GREEN;
    ArenaScreen(String title) { super(Component.literal(title)); }
    ArenaScreen(Component title) { super(title); }
    int left() { return Math.max(8, (width - 620) / 2); }
    int body() { return width - left() * 2; }
    void button(String text, int x, int y, int w, int h, boolean enabled, int accent, Runnable action) {
        addButton(Component.literal(text),x,y,w,h,enabled,accent,Look.PLAIN,8,5,action);
    }
    void button(Component text,int x,int y,int w,int h,boolean enabled,int accent,Runnable action) {
        addButton(text,x,y,w,h,enabled,accent,Look.PLAIN,8,5,action);
    }
    /** Left-aligned row used by scrolling lists (players, maps, weapons). */
    void listButton(Component text,int x,int y,int w,int h,boolean enabled,int accent,Runnable action) {
        listButton(text,x,y,w,h,enabled,accent,9,5,action);
    }
    /** List row whose label is inset so the caller can draw icons or tags over the padding. */
    void listButton(Component text,int x,int y,int w,int h,boolean enabled,int accent,int padLeft,int padRight,Runnable action) {
        addButton(text,x,y,w,h,enabled,accent,Look.ROW,padLeft,padRight,action);
    }
    void colorButton(String text,int x,int y,int w,int h,boolean enabled,int color,Runnable action) {
        addButton(Component.literal(text),x,y,w,h,enabled,color,Look.FILLED,8,5,action);
    }
    /** Underlined tab; {@code selected} lights the underline in the accent colour. */
    void tabButton(String text,int x,int y,int w,int h,boolean selected,Runnable action) {
        addButton(Component.literal(text),x,y,w,h,true,selected?ACCENT:MUTED,Look.TAB,8,5,action);
    }
    void closeButton(int x,int y,int w,int h) {
        colorButton("닫기",x,y,w,h,true,0xFFCC3333,this::onClose);
    }
    private enum Look { PLAIN, ROW, FILLED, TAB }
    private void addButton(Component text,int x,int y,int w,int h,boolean enabled,int accent,Look look,int padLeft,int padRight,Runnable action) {
        var button = new FlatButton(x, y, w, h, text, b -> action.run(), accent,look,padLeft,padRight);
        button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(text));
        button.active = enabled; addRenderableWidget(button);
    }
    void shell(GuiGraphics g, String subtitle) {
        shell(g, Component.literal(subtitle));
    }
    /** Lets the world show faintly through the backdrop and panel (match results). */
    boolean seeThrough() { return false; }
    void shell(GuiGraphics g, Component subtitle) {
        int l=left(),r=l+body(),bottom=height-10;boolean clear=seeThrough();
        g.fillGradient(0, 0, width, height, clear?0x600B0C09:0xEA0B0C09, clear?0x800F100D:0xF20F100D);
        if(!clear)TacticalStyle.grid(g,0,0,width,height,24,0x07FFFFFF);
        if(clear){g.fill(r, 17, r + 3, bottom + 3, 0x40000000);g.fill(l + 3, bottom + 1, r, bottom + 3, 0x40000000);}
        else TacticalStyle.shadow(g,l-1,13,r-l+2,bottom-12);
        // Draw the border as a frame so a translucent panel is not tinted by it.
        TacticalStyle.roundFrame(g,l-1,13,r-l+2,bottom-12,BORDER);
        g.fill(l, 14, r, bottom, clear?0x8C181914:0xF0181914);
        g.fillGradient(l, 16, r, 54, clear?0xB422231B:0xFF22231B, clear?0x8C181914:0xF0181914);
        g.fill(l, 14, r, 15, ACCENT);g.fill(l, 15, l + 96, 16, ACCENT);
        g.fill(l + 12, 25, l + 14, 35, ACCENT);
        g.drawString(font, title, l + 19, 26, INK, false);
        g.enableScissor(l+12,36,r-12,55);
        g.drawString(font, subtitle, l + 19, 40, MUTED, false);
        g.flush();g.disableScissor();
        g.fill(l + 12, 52, r - 12, 53, TacticalStyle.HAIRLINE);
    }
    /** Bordered inset panel. */
    void card(GuiGraphics g, int x1, int y1, int x2, int y2) {
        TacticalStyle.surface(g, x1 - 1, y1 - 1, x2 - x1 + 2, y2 - y1 + 2, 0xFF191A15, PANEL, BORDER);
    }
    /** Card header: coloured top edge, label and an optional right-aligned note. */
    void cardHeader(GuiGraphics g, int x1, int y1, int x2, String label, int color, String note) {
        g.fill(x1, y1, x2, y1 + 2, color);
        g.fillGradient(x1, y1 + 2, x2, y1 + 16, blend(PANEL, color, .18f), PANEL);
        g.drawString(font, label, x1 + 6, y1 + 5, color, false);
        if (note != null && !note.isEmpty()) {
            String text = font.plainSubstrByWidth(note, Math.max(1, x2 - x1 - font.width(label) - 18));
            g.drawString(font, text, x2 - 6 - font.width(text), y1 + 5, MUTED, false);
        }
    }
    /** Compact rounded status chip; returns its width. */
    int badge(GuiGraphics g, String text, int x, int y, int color) {
        int w = font.width(text) + 8;
        TacticalStyle.round(g, x, y, w, 11, blend(PANEL, color, .55f));
        g.fill(x + 1, y + 1, x + w - 1, y + 10, blend(PANEL, color, .2f));
        g.drawString(font, text, x + 4, y + 2, color, false);
        return w;
    }
    static int blend(int base, int color, float amount) {
        int r = Math.round((base >> 16 & 255) + ((color >> 16 & 255) - (base >> 16 & 255)) * amount);
        int gc = Math.round((base >> 8 & 255) + ((color >> 8 & 255) - (base >> 8 & 255)) * amount);
        int b = Math.round((base & 255) + ((color & 255) - (base & 255)) * amount);
        return 0xFF000000 | r << 16 | gc << 8 | b;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(null); }
    private static final class FlatButton extends Button {
        final int accent, padLeft, padRight;
        final Look look;
        FlatButton(int x, int y, int w, int h, Component label, OnPress press, int accent,Look look,int padLeft,int padRight) {
            super(x, y, w, h, label, press, DEFAULT_NARRATION); this.accent = accent;this.look=look;this.padLeft=padLeft;this.padRight=padRight;
        }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            boolean hover = active && isHoveredOrFocused(), highlighted = accent != MUTED, filled = look == Look.FILLED;
            int x1=getX(),y1=getY(),x2=x1+width,y2=y1+height,tint=highlighted?accent:INK;
            int textColor;
            if(look==Look.TAB){
                // Tabs float on the shell: a tint rising toward the underline, no frame.
                if(active&&(hover||highlighted))g.fillGradient(x1,y1,x2,y2,tint&0xFFFFFF,(tint&0xFFFFFF)|(hover?0x26000000:0x18000000));
                g.fill(x1,y2-1,x2,y2,TacticalStyle.HAIRLINE);
                if(active&&highlighted)g.fill(x1,y2-2,x2,y2,accent);
                textColor=!active?DIM:highlighted?accent:hover?INK:MUTED;
            }else{
                float strength=(filled?.28f:highlighted?.11f:.05f)+(hover?.1f:0);
                int edge=!active?0xFF2C2D28:hover?(highlighted?accent:blend(BORDER,INK,.45f)):highlighted?blend(BORDER,accent,filled?.85f:.6f):BORDER;
                TacticalStyle.round(g,x1,y1,width,height,edge);
                g.fillGradient(x1+1,y1+1,x2-1,y2-1,!active?0xFF131410:blend(PANEL,tint,strength+.04f),!active?0xFF10110E:blend(PANEL,tint,strength*.55f));
                if(active)g.fill(x1+2,y1+1,x2-2,y1+2,hover?0x22FFFFFF:0x10FFFFFF);
                // Rows mark the selection with an inset bar; hover shows a thinner neutral one.
                if(active&&look==Look.ROW&&(highlighted||hover))g.fill(x1+2,y1+3,x1+(highlighted?4:3),y2-3,highlighted?accent:blend(BORDER,INK,.6f));
                textColor=!active?DIM:filled?blend(accent,0xFFFFFFFF,.45f):highlighted?accent:INK;
            }
            var font = Minecraft.getInstance().font;
            var label = net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(getMessage(), Math.max(1,width - padLeft - padRight)));
            int textX = look == Look.ROW ? x1 + padLeft : x1 + Math.max(padLeft, (width - font.width(label)) / 2);
            g.drawString(font, label, textX, y1 + (height - 8) / 2, textColor, false);
        }
    }
}
