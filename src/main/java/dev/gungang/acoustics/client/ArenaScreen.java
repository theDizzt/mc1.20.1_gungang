package dev.gungang.acoustics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

abstract class ArenaScreen extends Screen {
    static final int INK = 0xFFF0F4FA, MUTED = 0xFF9AAABD, ACCENT = 0xFF70CDBF;
    static final int DIM = 0xFF5F6E80, PANEL = 0xFF101A28, BORDER = 0xFF2A3A50, READY = 0xFF7BE07B;
    ArenaScreen(String title) { super(Component.literal(title)); }
    ArenaScreen(Component title) { super(title); }
    int left() { return Math.max(8, (width - 620) / 2); }
    int body() { return width - left() * 2; }
    void button(String text, int x, int y, int w, int h, boolean enabled, int accent, Runnable action) {
        addButton(Component.literal(text),x,y,w,h,enabled,accent,false,true,action);
    }
    void button(Component text,int x,int y,int w,int h,boolean enabled,int accent,Runnable action) {
        addButton(text,x,y,w,h,enabled,accent,false,true,action);
    }
    /** Left-aligned row used by scrolling lists (players, maps, weapons). */
    void listButton(Component text,int x,int y,int w,int h,boolean enabled,int accent,Runnable action) {
        addButton(text,x,y,w,h,enabled,accent,false,false,action);
    }
    void colorButton(String text,int x,int y,int w,int h,boolean enabled,int color,Runnable action) {
        addButton(Component.literal(text),x,y,w,h,enabled,color,true,true,action);
    }
    void closeButton(int x,int y,int w,int h) {
        colorButton("닫기",x,y,w,h,true,0xFFCC3333,this::onClose);
    }
    private void addButton(Component text,int x,int y,int w,int h,boolean enabled,int accent,boolean filled,boolean center,Runnable action) {
        var button = new FlatButton(x, y, w, h, text, b -> action.run(), accent,filled,center);
        button.active = enabled; addRenderableWidget(button);
    }
    void shell(GuiGraphics g, String subtitle) {
        shell(g, Component.literal(subtitle));
    }
    /** Lets the world show faintly through the backdrop and panel (match results). */
    boolean seeThrough() { return false; }
    void shell(GuiGraphics g, Component subtitle) {
        int l=left(),r=l+body(),bottom=height-10;boolean clear=seeThrough();
        g.fillGradient(0, 0, width, height, clear?0x60070C16:0xEA070C16, clear?0x800C1422:0xF20C1422);
        if(clear){g.fill(r, 17, r + 3, bottom + 3, 0x40000000);g.fill(l + 3, bottom + 1, r, bottom + 3, 0x40000000);}
        else g.fill(l + 3, 17, r + 3, bottom + 3, 0x66000000);
        // Draw the border as a frame so a translucent panel is not tinted by it.
        g.fill(l - 1, 13, r + 1, 14, BORDER);g.fill(l - 1, bottom, r + 1, bottom + 1, BORDER);
        g.fill(l - 1, 14, l, bottom, BORDER);g.fill(r, 14, r + 1, bottom, BORDER);
        g.fill(l, 14, r, bottom, clear?0x8C152030:0xF0152030);
        g.fillGradient(l, 16, r, 54, clear?0xB41B293C:0xFF1B293C, clear?0x8C152030:0xF0152030);
        g.fill(l, 14, r, 16, ACCENT);
        g.fill(l + 12, 25, l + 14, 35, ACCENT);
        g.drawString(font, title, l + 19, 26, INK, false);
        g.enableScissor(l+12,36,r-12,55);
        g.drawString(font, subtitle, l + 19, 40, MUTED, false);
        g.flush();g.disableScissor();
        g.fill(l + 12, 52, r - 12, 53, 0xFF22324A);
    }
    /** Bordered inset panel. */
    void card(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, BORDER);
        g.fill(x1, y1, x2, y2, PANEL);
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
    /** Small pill-shaped tag; returns its width. */
    int badge(GuiGraphics g, String text, int x, int y, int color) {
        int w = font.width(text) + 8;
        g.fill(x, y, x + w, y + 11, blend(PANEL, color, .3f));
        g.fill(x, y, x + 1, y + 11, color);
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
        final int accent;
        final boolean filled, center;
        FlatButton(int x, int y, int w, int h, Component label, OnPress press, int accent,boolean filled,boolean center) {
            super(x, y, w, h, label, press, DEFAULT_NARRATION); this.accent = accent;this.filled=filled;this.center=center;
        }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            boolean hover = active && isHoveredOrFocused(), highlighted = accent != MUTED;
            int background = !active ? 0xFF18202B : hover ? 0xFF304157 : 0xFF223045;
            if (active && highlighted) background = blend(background, accent, hover ? .28f : .12f);
            if(filled) {
                background = !active ? blend(0xFF18202B, accent, .35f) : hover ? blend(accent, 0xFFFFFFFF, .2f) : accent;
            }
            int border = !active ? 0xFF222C38 : hover ? (filled ? blend(accent, 0xFFFFFFFF, .5f) : accent) : filled ? blend(accent, 0xFF000000, .25f) : 0xFF33445A;
            int x1 = getX(), y1 = getY(), x2 = x1 + width, y2 = y1 + height;
            g.fill(x1, y1, x2, y2, border);
            g.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, background);
            if (!filled) {
                g.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, active ? 0x14FFFFFF : 0x08FFFFFF);
                g.fill(x1, y1, x1 + (highlighted && active ? 3 : 2), y2, active ? accent : 0xFF3A4554);
            } else g.fill(x1 + 1, y2 - 2, x2 - 1, y2 - 1, 0x33000000);
            var font = Minecraft.getInstance().font;
            var label = net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(getMessage(), Math.max(1,width - 14)));
            int textColor=active?INK:0xFF5F6D7E;
            if(filled)textColor=((background>>16&255)*299+(background>>8&255)*587+(background&255)*114)>=150000?0xFF101820:INK;
            int textX = center ? x1 + Math.max(8, (width - font.width(label)) / 2) : x1 + 9;
            g.drawString(font, label, textX, y1 + (height - 8) / 2, textColor, false);
        }
    }
}
