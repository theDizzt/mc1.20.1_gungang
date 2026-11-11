package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class NicknameScreen extends Screen {
    private static boolean required;
    private static String error="";
    private EditBox input;
    private Button save;
    private NicknameScreen(){super(Component.literal(Lang.tr("닉네임 설정")));}
    public static void receive(GungangNetwork.Nickname packet){
        required=packet.required();error=packet.error();
        var mc=Minecraft.getInstance();
        if(!required&&mc.screen instanceof NicknameScreen)mc.setScreen(null);
        else if(mc.screen instanceof NicknameScreen s)s.save.active=true;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        var mc=Minecraft.getInstance();
        if(e.phase==TickEvent.Phase.END&&required&&mc.player!=null&&mc.level!=null&&!(mc.screen instanceof NicknameScreen))mc.setScreen(new NicknameScreen());
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){required=false;error="";}
    @Override protected void init(){
        String old=input==null?"":input.getValue();
        input=addRenderableWidget(new EditBox(font,width/2-100,height/2-10,200,20,Component.literal(Lang.tr("닉네임"))));
        input.setMaxLength(64);input.setValue(old);setInitialFocus(input);
        save=addRenderableWidget(Button.builder(Component.literal(Lang.tr("저장")),b->submit()).bounds(width/2-50,height/2+25,100,20).build());
    }
    private void submit(){
        if(!save.active)return;
        try {String value=PlayerRecord.validateNickname(input.getValue());error=Lang.tr("저장 중…");save.active=false;GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.SetNickname(value));}
        catch(IllegalArgumentException e){error=e.getMessage();}
    }
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==257||key==335){submit();return true;}return super.keyPressed(key,scan,mods);}
    @Override public void tick(){input.tick();}
    @Override public boolean shouldCloseOnEsc(){return false;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        renderBackground(g);g.drawCenteredString(font,title,width/2,height/2-60,0xFFFFFF);
        g.drawCenteredString(font,Lang.tr("한글·영문 등 최대 8글자"),width/2,height/2-35,0xCCCCCC);
        super.render(g,x,y,partial);g.drawCenteredString(font,error,width/2,height/2+55,0xFFAA66);
    }
}
