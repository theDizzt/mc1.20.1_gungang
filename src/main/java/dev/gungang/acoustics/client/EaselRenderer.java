package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gungang.acoustics.server.ToyBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 241 easel: the painted 16x16 canvas on the board's front, and the painting screen. */
public final class EaselRenderer implements BlockEntityRenderer<ToyBlocks.EaselEntity> {
    private static final ResourceLocation WHITE=new ResourceLocation("gungang","textures/misc/white.png");
    /** Paint area on the north-facing model's board: its front plane, the left edge seen from the front (+x), bottom and size. */
    // The board is the model's [4,6,6.5]-[28,30,8] (two blocks wide and tall from the anchor cell); paint inside its frame.
    private static final float FRONT=6.5f/16-.002f,LEFT=27f/16,BOTTOM=7f/16,SIZE=22f/16;
    public EaselRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(ToyBlocks.EaselEntity easel,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        String pixels=easel.pixels;var state=easel.getBlockState();
        if(pixels.isEmpty()||!state.hasProperty(ToyBlocks.Easel.FACING))return;
        float rot=switch(state.getValue(ToyBlocks.Easel.FACING)){case EAST->90;case SOUTH->180;case WEST->270;default->0;};
        pose.pushPose();pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(-rot));pose.translate(-.5,0,-.5);
        var buffer=buffers.getBuffer(RenderType.text(WHITE));var m=pose.last().pose();float cell=SIZE/ToyBlocks.CANVAS;
        for(int row=0;row<ToyBlocks.CANVAS;row++)for(int col=0;col<ToyBlocks.CANVAS;col++){
            int rgb=ToyBlocks.PALETTE[Character.digit(pixels.charAt(row*ToyBlocks.CANVAS+col),16)];
            float r=(rgb>>16&255)/255f,g=(rgb>>8&255)/255f,b=(rgb&255)/255f;
            float left=LEFT-col*cell,right=left-cell,top=BOTTOM+SIZE-row*cell,bottom=top-cell;
            buffer.vertex(m,left,top,FRONT).color(r,g,b,1).uv(0,0).uv2(light).endVertex();
            buffer.vertex(m,left,bottom,FRONT).color(r,g,b,1).uv(0,1).uv2(light).endVertex();
            buffer.vertex(m,right,bottom,FRONT).color(r,g,b,1).uv(1,1).uv2(light).endVertex();
            buffer.vertex(m,right,top,FRONT).color(r,g,b,1).uv(1,0).uv2(light).endVertex();
        }
        pose.popPose();
    }

    /** Opens the painting screen on {@code pixels}; {@code save} receives the finished canvas. */
    public static void paint(String pixels,java.util.function.Consumer<String> save){Minecraft.getInstance().setScreen(new Painter(pixels,save));}

    /** 16x16 pixel editor: left button paints with the chosen colour, right button erases to white. */
    private static final class Painter extends Screen {
        private final char[] canvas=new char[ToyBlocks.CANVAS*ToyBlocks.CANVAS];private final java.util.function.Consumer<String> save;
        private int colour=1;
        Painter(String pixels,java.util.function.Consumer<String> save){
            super(Component.literal(Lang.tr("이젤 그리기")));this.save=save;
            String start=ToyBlocks.canvas(pixels);for(int i=0;i<canvas.length;i++)canvas[i]=start.isEmpty()?'0':start.charAt(i);
        }
        private int cell(){return Math.max(6,Math.min(14,(height-90)/ToyBlocks.CANVAS));}
        private int left(){return width/2-cell()*ToyBlocks.CANVAS/2-20;}
        private int top(){return 34;}
        @Override protected void init(){
            int y=top()+cell()*ToyBlocks.CANVAS+10;
            addRenderableWidget(Button.builder(Component.literal(Lang.tr("저장")),b->{save.accept(new String(canvas));onClose();}).bounds(width/2-154,y,100,20).build());
            addRenderableWidget(Button.builder(Component.literal(Lang.tr("모두 지우기")),b->java.util.Arrays.fill(canvas,'0')).bounds(width/2-50,y,100,20).build());
            addRenderableWidget(Button.builder(Component.literal(Lang.tr("취소")),b->onClose()).bounds(width/2+54,y,100,20).build());
        }
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            renderBackground(g);g.drawCenteredString(font,title,width/2,14,0xFFFFFF);
            int c=cell(),x=left(),y=top(),size=c*ToyBlocks.CANVAS;
            g.fill(x-2,y-2,x+size+2,y+size+2,0xFF3A2A1A);
            for(int i=0;i<canvas.length;i++){int px=x+i%ToyBlocks.CANVAS*c,py=y+i/ToyBlocks.CANVAS*c;g.fill(px,py,px+c,py+c,0xFF000000|ToyBlocks.PALETTE[Character.digit(canvas[i],16)]);}
            // Palette: two columns of eight swatches right of the canvas; the chosen one is outlined.
            for(int i=0;i<ToyBlocks.PALETTE.length;i++){
                int sx=x+size+12+i/8*20,sy=y+i%8*20;
                g.fill(sx-1,sy-1,sx+17,sy+17,i==colour?0xFFFFE27A:0xFF202020);g.fill(sx,sy,sx+16,sy+16,0xFF000000|ToyBlocks.PALETTE[i]);
            }
            g.drawString(font,Lang.tr("좌클릭 칠하기 · 우클릭 지우기"),x,y+size+36,0xFFAAAAAA,false);
            super.render(g,mx,my,partial);
        }
        private boolean at(double mx,double my,int button){
            int c=cell(),x=left(),y=top(),size=c*ToyBlocks.CANVAS;
            if(mx>=x&&mx<x+size&&my>=y&&my<y+size){
                int i=(int)((my-y)/c)*ToyBlocks.CANVAS+(int)((mx-x)/c);
                canvas[i]=button==1?'0':Character.forDigit(colour,16);return true;
            }
            for(int i=0;i<ToyBlocks.PALETTE.length;i++){int sx=x+size+12+i/8*20,sy=y+i%8*20;if(mx>=sx&&mx<sx+16&&my>=sy&&my<sy+16){colour=i;return true;}}
            return false;
        }
        @Override public boolean mouseClicked(double mx,double my,int button){return at(mx,my,button)||super.mouseClicked(mx,my,button);}
        @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){return at(mx,my,button)||super.mouseDragged(mx,my,button,dx,dy);}
        @Override public boolean isPauseScreen(){return false;}
    }
}
