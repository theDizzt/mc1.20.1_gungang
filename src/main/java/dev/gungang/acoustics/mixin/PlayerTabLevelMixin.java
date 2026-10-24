package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.client.PlayerLevels;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Puts the level emblem in front of each vanilla Tab name; the name width grows by the same gap so columns still fit. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabLevelMixin {
    @Unique private static final int GAP=10;
    @Unique private PlayerInfo gungang$row;

    // render asks for each name right before measuring or drawing it.
    @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/components/PlayerTabOverlay;getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;"))
    private Component gungang$name(PlayerTabOverlay self,PlayerInfo info) {gungang$row=info;return self.getNameForDisplay(info);}

    @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I"))
    private int gungang$width(Font font,FormattedText text) {return font.width(text)+(PlayerLevels.active()?GAP:0);}

    @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"))
    private int gungang$draw(GuiGraphics g,Font font,Component name,int x,int y,int color) {
        if(!PlayerLevels.active())return g.drawString(font,name,x,y,color);
        Integer level=gungang$row==null?null:PlayerLevels.level(gungang$row.getProfile().getId());
        if(level!=null)dev.gungang.acoustics.client.LevelEmblem.draw(g,level,x,y,8);
        return g.drawString(font,name,x+GAP,y,color);
    }
}
