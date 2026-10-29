package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class ConveyorScreen extends Screen {
    private final BlockPos pos;
    private int speed;
    private boolean reverse;
    private ConveyorScreen(BlockPos pos,int speed){super(Component.translatable("gungang.conveyor.title"));this.pos=pos;this.speed=speed;}
    public static void open(GungangNetwork.ConveyorView view){Minecraft.getInstance().setScreen(new ConveyorScreen(view.pos(),view.speed()));}
    @Override protected void init(){
        int x=width/2-110,y=height/2-40;
        addRenderableWidget(new AbstractSliderButton(x,y,220,20,Component.empty(),(speed-1)/29d){
            {updateMessage();}
            @Override protected void updateMessage(){setMessage(Component.translatable("gungang.conveyor.speed",String.format(java.util.Locale.ROOT,"%.1f",speed/10d)));}
            @Override protected void applyValue(){speed=1+(int)Math.round(value*29);}
        });
        addRenderableWidget(Button.builder(direction(),b->{reverse=!reverse;b.setMessage(direction());}).bounds(x,y+28,220,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gungang.conveyor.apply"),b->{GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.ConveyorSet(pos,speed,reverse));onClose();}).bounds(x,y+64,106,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(x+114,y+64,106,20).build());
    }
    private Component direction(){return Component.translatable(reverse?"gungang.conveyor.reverse":"gungang.conveyor.forward");}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);g.drawCenteredString(font,title,width/2,height/2-82,0xFFFFFF);g.drawCenteredString(font,Component.translatable("gungang.conveyor.network"),width/2,height/2-62,0xA9C2CC);super.render(g,mx,my,partial);}
    @Override public boolean isPauseScreen(){return false;}
}
