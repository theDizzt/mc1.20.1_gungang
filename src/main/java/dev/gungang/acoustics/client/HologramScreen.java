package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.HologramMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class HologramScreen extends AbstractContainerScreen<HologramMenu> {
    private Button smaller,larger,rotation;
    public HologramScreen(HologramMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=176;imageHeight=197;inventoryLabelY=103;}
    @Override protected void init(){
        super.init();
        smaller=addRenderableWidget(Button.builder(Component.literal("−"),b->send(0)).bounds(leftPos+20,topPos+55,24,20).build());
        larger=addRenderableWidget(Button.builder(Component.literal("+"),b->send(1)).bounds(leftPos+132,topPos+55,24,20).build());
        rotation=addRenderableWidget(Button.builder(Component.empty(),b->send(2)).bounds(leftPos+20,topPos+79,136,20).build());
    }
    private void send(int button){if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,button);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);smaller.active=menu.size()>25;larger.active=menu.size()<300;
        rotation.setMessage(Component.literal(Lang.tr("회전: ")+switch(menu.rotation()){case 0->"OFF";case 1->Lang.tr("느리게");case 3->Lang.tr("빠르게");case 4->Lang.tr("매우 빠르게");default->Lang.tr("보통");}));
        super.render(g,mx,my,partial);renderTooltip(g,mx,my);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFFB7C3CD);
        g.fill(leftPos+2,topPos+2,leftPos+imageWidth-2,topPos+imageHeight-2,0xFF243D50);
        g.fill(leftPos+77,topPos+27,leftPos+99,topPos+49,0xFF65D9FA);
        for(var slot:menu.slots){g.fill(leftPos+slot.x-1,topPos+slot.y-1,leftPos+slot.x+17,topPos+slot.y+17,0xFF748894);g.fill(leftPos+slot.x,topPos+slot.y,leftPos+slot.x+16,topPos+slot.y+16,0xFF142C3A);}
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawCenteredString(font,title,imageWidth/2,8,0xE4F8FF);
        g.drawCenteredString(font,Lang.tr("영사 아이템"),imageWidth/2,19,0xB7D4E4);
        g.drawCenteredString(font,Lang.tr("크기: ")+menu.size()+"%",imageWidth/2,61,0xFFFFFF);
        g.drawString(font,playerInventoryTitle,8,inventoryLabelY,0xD4E8F5,false);
    }
}
