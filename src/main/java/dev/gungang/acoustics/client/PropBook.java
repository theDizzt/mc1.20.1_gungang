package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.GungangNetwork;
import dev.gungang.acoustics.server.PropEntity;
import dev.gungang.acoustics.server.PropHuntMatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;

/** 178 lectern disguise: anyone reads its book (right click); its hider writes it (E). The text lives on the prop. */
final class PropBook {
    /** Characters per book page: the book screen wraps lines itself, so pages split on length and blank lines. */
    private static final int PAGE=220;
    static void read(PropEntity prop){
        var book=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WRITTEN_BOOK);var tag=book.getOrCreateTag();
        tag.putString("title",Lang.tr("독서대"));tag.putString("author","");tag.putBoolean("resolved",true);
        var pages=new net.minecraft.nbt.ListTag();
        String text=prop.text().isBlank()?Lang.tr("(빈 책)"):prop.text();
        for(int start=0;start<text.length()&&pages.size()<50;){
            int end=Math.min(text.length(),start+PAGE);int split=text.lastIndexOf("\n\n",end);
            if(end<text.length()&&split>start)end=split+2;
            pages.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(Component.literal(text.substring(start,end)))));start=end;
        }
        tag.put("pages",pages);
        Minecraft.getInstance().setScreen(new BookViewScreen(new BookViewScreen.WrittenBookAccess(book)));
    }
    static void edit(PropEntity prop){Minecraft.getInstance().setScreen(new Editor(prop.text()));}
    private static final class Editor extends Screen {
        private final String initial;private MultiLineEditBox box;
        Editor(String initial){super(Component.literal(Lang.tr("독서대 책 쓰기")));this.initial=initial;}
        @Override protected void init(){
            int w=Math.min(300,width-40),h=Math.min(180,height-80),x=(width-w)/2,y=(height-h)/2-10;
            box=new MultiLineEditBox(font,x,y,w,h,Component.literal(Lang.tr("술래에게 남길 말...")),Component.literal(Lang.tr("책 내용")));
            box.setCharacterLimit(PropHuntMatch.LECTERN_LIMIT);box.setValue(initial);addRenderableWidget(box);setInitialFocus(box);
            addRenderableWidget(Button.builder(Component.literal(Lang.tr("저장")),b->{GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.PropText(box.getValue()));onClose();}).bounds(width/2-102,y+h+8,100,20).build());
            addRenderableWidget(Button.builder(Component.literal(Lang.tr("취소")),b->onClose()).bounds(width/2+2,y+h+8,100,20).build());
        }
        @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);g.drawCenteredString(font,title,width/2,box.getY()-14,0xFFFFFF);super.render(g,mx,my,partial);}
        @Override public boolean isPauseScreen(){return false;}
    }
    private PropBook(){}
}
