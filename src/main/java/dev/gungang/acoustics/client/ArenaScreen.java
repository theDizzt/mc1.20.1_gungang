package dev.gungang.acoustics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

abstract class ArenaScreen extends Screen {
    static final int INK = 0xFFF0F4FA, MUTED = 0xFF9AAABD, ACCENT = 0xFF70CDBF;
    ArenaScreen(String title) { super(Component.literal(title)); }
    int left() { return Math.max(8, (width - 620) / 2); }
    int body() { return width - left() * 2; }
    void button(String text, int x, int y, int w, int h, boolean enabled, int accent, Runnable action) {
        var button = new FlatButton(x, y, w, h, Component.literal(text), b -> action.run(), accent);
        button.active = enabled; addRenderableWidget(button);
    }
    void shell(GuiGraphics g, String subtitle) {
        shell(g, Component.literal(subtitle));
    }
    void shell(GuiGraphics g, Component subtitle) {
        g.fill(0, 0, width, height, 0xE80A101B);
        g.fill(left() - 1, 14, left() + body() + 1, height - 10, 0xF0152030);
        g.fill(left(), 14, left() + body(), 16, ACCENT);
        g.drawString(font, title, left() + 12, 26, INK, false);
        g.enableScissor(left()+12,36,left()+body()-12,55);
        g.drawString(font, subtitle, left() + 12, 40, MUTED, false);
        g.flush();g.disableScissor();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(null); }
    private static final class FlatButton extends Button {
        final int accent;
        FlatButton(int x, int y, int w, int h, Component label, OnPress press, int accent) {
            super(x, y, w, h, label, press, DEFAULT_NARRATION); this.accent = accent;
        }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            int background = !active ? 0xFF1A2330 : isHoveredOrFocused() ? 0xFF304157 : 0xFF243247;
            g.fill(getX(), getY(), getX() + width, getY() + height, background);
            g.fill(getX(), getY(), getX() + 2, getY() + height, active ? accent : 0xFF4B5665);
            var font = Minecraft.getInstance().font;
            String label = font.plainSubstrByWidth(getMessage().getString(), Math.max(1,width - 14));
            g.drawString(font, label, getX() + 8, getY() + (height - 8) / 2, active ? INK : 0xFF697789, false);
        }
    }
}
