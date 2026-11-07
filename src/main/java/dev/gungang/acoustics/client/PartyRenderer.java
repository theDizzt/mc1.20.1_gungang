package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gungang.acoustics.server.PartyBlocks;
import dev.gungang.acoustics.server.PartyBlocks.Kind;
import dev.gungang.acoustics.server.PartyBlocks.PartyEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Prop hunt 243~252 (PartyBlocks): drawn in pixels about the cell's floor centre, front towards -z (turned for the
 * block's facing), from vanilla block textures, a white texture with vertex colours, and the doll figure's skins.
 * The same code draws a placed block and a disguise (whose block entity PropRenderer fills from its prop).
 */
public final class PartyRenderer implements BlockEntityRenderer<PartyEntity> {
    private static final ResourceLocation WHITE=new ResourceLocation("gungang","textures/entity/white.png");
    private static final ResourceLocation STATUE_SKIN=new ResourceLocation("gungang","textures/entity/statue/archbear.png");
    private static final ResourceLocation SANGA_SKIN=new ResourceLocation("gungang","textures/entity/doll/sanga.png");
    private static final int BRIGHT=LightTexture.FULL_BRIGHT;
    public PartyRenderer(BlockEntityRendererProvider.Context context){}
    @Override public boolean shouldRenderOffScreen(PartyEntity be){return true;}
    @Override public int getViewDistance(){return 96;}
    @Override public void render(PartyEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var state=be.getBlockState();var kind=be.kind();
        float time=(be.getLevel()==null?0:be.getLevel().getGameTime())+partial;
        pose.pushPose();pose.translate(.5,0,.5);
        if(kind==Kind.CHANDELIER&&!be.disguise)pose.translate(0,-1,0); // a placed chandelier hangs from its cell's top
        float yaw=(kind==Kind.SANGA||kind==Kind.PHOTO||kind==Kind.AUTO)&&be.disguise?be.yaw:state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)?state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot():180;
        pose.mulPose(Axis.YP.rotationDegrees(180-yaw));
        switch(kind){
            case CLOCK->clock(pose,buffers,light);
            case MIRROR->mirror(pose,buffers,light);
            case CHANDELIER->chandelier(pose,buffers,light);
            case SLOT->slot(be,pose,buffers,light,time);
            case BALL->ball(be,pose,buffers,light);
            case WELL->well(be,pose,buffers,light);
            case STATUE->statue(pose,buffers,light,time);
            case TREE->tree(be,pose,buffers,light,time);
            case SANGA->sanga(be,pose,buffers,light);
            case PHOTO->photo(pose,buffers,light);
            case AUTO->auto(be,pose,buffers,light);
            case SHADOW->shadow(pose,buffers,light);
        }
        pose.popPose();
    }

    // ---- helpers (pixels) -----------------------------------------------------------------------------------------

    private static void b(PoseStack pose,MultiBufferSource buffers,int light,Block block,double x1,double y1,double z1,double x2,double y2,double z2){
        EscortRenderer.box(pose,buffers,light,block,x1/16,y1/16,z1/16,(float)(x2-x1)/16,(float)(y2-y1)/16,(float)(z2-z1)/16);
    }
    /** Text centred on (x, y) on a plane at z facing -z (the front), {@code size} pixels tall. */
    private static void text(PoseStack pose,MultiBufferSource buffers,String text,double x,double y,double z,float size,int color,int light){
        var font=Minecraft.getInstance().font;
        pose.pushPose();pose.translate(x/16,y/16,z/16);pose.mulPose(Axis.YP.rotationDegrees(180));
        float s=size/16f/8f;pose.scale(s,-s,s);
        font.drawInBatch(text,-font.width(text)/2f,-3.5f,color,false,pose.last().pose(),buffers,net.minecraft.client.gui.Font.DisplayMode.POLYGON_OFFSET,0,light);
        pose.popPose();
    }
    /** A flat coloured quad (pixels) on the white texture; c are corners in order, normal n. */
    private static void quad(PoseStack pose,com.mojang.blaze3d.vertex.VertexConsumer c,int light,float[][] p,int[] colors,float nx,float ny,float nz){
        var m=pose.last().pose();var n=pose.last().normal();
        for(int i=0;i<4;i++){int col=colors[i];
            c.vertex(m,p[i][0]/16,p[i][1]/16,p[i][2]/16).color(col>>16&255,col>>8&255,col&255,255).uv(.5f,.5f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,nx,ny,nz).endVertex();}
    }
    /** A disc of boxes (rows of {@code step} pixels) in the x/y plane, centre (cx, cy), from z1 to z2. */
    private static void disc(PoseStack pose,MultiBufferSource buffers,int light,Block block,double cx,double cy,double r,double z1,double z2,double step){
        for(double y=-r;y<r-1e-6;y+=step){
            double mid=Math.min(Math.abs(y),Math.abs(y+step));double half=Math.sqrt(Math.max(0,r*r-mid*mid));
            b(pose,buffers,light,block,cx-half,cy+y,z1,cx+half,cy+Math.min(r,y+step),z2);
        }
    }
    /** The doll figure (feet at the origin, facing -z) with {@code skin}, posed by {@code posing}. */
    private static void figure(PoseStack pose,MultiBufferSource buffers,int light,ResourceLocation skin,java.util.function.Consumer<ModelPart> posing){figure(pose,buffers,light,skin,posing,null);}
    /** As above; {@code held} then draws in the figure's model space (as the vanilla held-item layer does). */
    private static void figure(PoseStack pose,MultiBufferSource buffers,int light,ResourceLocation skin,java.util.function.Consumer<ModelPart> posing,java.util.function.Consumer<ModelPart> held){
        var root=PropObjectRenderer.figure(false);root.getAllParts().forEach(ModelPart::resetPose);posing.accept(root);
        pose.pushPose();pose.scale(-1,-1,1);pose.translate(0,-1.501,0);
        root.render(pose,buffers.getBuffer(RenderType.entityTranslucent(skin)),light,OverlayTexture.NO_OVERLAY);
        if(held!=null)held.accept(root);
        pose.popPose();root.getAllParts().forEach(ModelPart::resetPose);
    }

    // ---- 243 analog wall clock ------------------------------------------------------------------------------------

    /** Three blocks round, its back at the cell's back, showing the computer's time with a sweeping second hand. */
    private static void clock(PoseStack pose,MultiBufferSource buffers,int light){
        double cy=24;
        disc(pose,buffers,light,Blocks.DARK_OAK_PLANKS,0,cy,24,5,8,2);
        disc(pose,buffers,light,Blocks.GOLD_BLOCK,0,cy,22.5,4.8,5,1.5);
        disc(pose,buffers,light,Blocks.WHITE_CONCRETE,0,cy,21.5,4.6,4.8,1);
        for(int i=0;i<60;i++){
            boolean hour=i%5==0,quarter=i%15==0;double w=quarter?2.2:hour?1.4:.5,len=quarter?4.5:hour?3.5:1.4;
            pose.pushPose();pose.translate(0,cy/16,0);pose.mulPose(Axis.ZP.rotationDegrees(i*6));
            b(pose,buffers,light,Blocks.BLACK_CONCRETE,-w/2,20-len,4.3,w/2,20,4.6);
            pose.popPose();
        }
        for(int h=3;h<=12;h+=3){double a=Math.toRadians(h*30);text(pose,buffers,String.valueOf(h),-Math.sin(a)*13.5,cy+Math.cos(a)*13.5,4.25,4.5f,0x202020,light);}
        var now=java.time.LocalTime.now();double seconds=now.getSecond()+now.getNano()/1e9,minutes=now.getMinute()+seconds/60,hours=now.getHour()%12+minutes/60;
        hand(pose,buffers,light,Blocks.BLACK_CONCRETE,cy,hours*30,2.4,11,2.5,4.0,4.3);
        hand(pose,buffers,light,Blocks.BLACK_CONCRETE,cy,minutes*6,1.6,17,2.5,3.7,4.0);
        hand(pose,buffers,light,Blocks.RED_CONCRETE,cy,seconds*6,.6,18.5,4.5,3.4,3.7);
        b(pose,buffers,light,Blocks.GOLD_BLOCK,-1.6,cy-1.6,3.0,1.6,cy+1.6,3.4);
    }
    private static void hand(PoseStack pose,MultiBufferSource buffers,int light,Block block,double cy,double degrees,double w,double len,double tail,double z1,double z2){
        pose.pushPose();pose.translate(0,cy/16,0);pose.mulPose(Axis.ZP.rotationDegrees((float)degrees));
        b(pose,buffers,light,block,-w/2,-tail,z1,w/2,len,z2);pose.popPose();
    }

    // ---- 244 mirror -----------------------------------------------------------------------------------------------

    /** A full-length mirror on little feet: dark wooden frame, silvery glass with light streaks. */
    private static void mirror(PoseStack pose,MultiBufferSource buffers,int light){
        b(pose,buffers,light,Blocks.DARK_OAK_PLANKS,-7,0,-3,-5,2,8);b(pose,buffers,light,Blocks.DARK_OAK_PLANKS,5,0,-3,7,2,8);
        b(pose,buffers,light,Blocks.DARK_OAK_PLANKS,-8,2,5,8,32,8);
        b(pose,buffers,light,Blocks.GOLD_BLOCK,-7,3,4.6,7,31,5);
        int lit=LightTexture.pack(Math.max(9,LightTexture.block(light)),LightTexture.sky(light));
        var c=buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));float z=4.5f;
        quad(pose,c,lit,new float[][]{{6.4f,3.6f,z},{-6.4f,3.6f,z},{-6.4f,30.4f,z},{6.4f,30.4f,z}},new int[]{0x8E9CAB,0x8E9CAB,0xDDE6EF,0xDDE6EF},0,0,-1);
        float zs=z-.05f;int s=0xF4F8FC;
        quad(pose,c,lit,new float[][]{{5.5f,18,zs},{3.5f,18,zs},{-2.5f,30.4f,zs},{-.5f,30.4f,zs}},new int[]{s,s,s,s},0,0,-1);
        quad(pose,c,lit,new float[][]{{6.4f,8,zs},{5.6f,8,zs},{-.4f,20,zs},{.4f,20,zs}},new int[]{s,s,s,s},0,0,-1);
        quad(pose,c,lit,new float[][]{{1,3.6f,zs},{-.6f,3.6f,zs},{-6.4f,15,zs},{-6.4f,18.2f,zs}},new int[]{s,s,s,s},0,0,-1);
    }

    // ---- 246 chandelier -------------------------------------------------------------------------------------------

    /** Three wide and two tall: a chain from the top, a gold column and bowl, two rings of arms with lit candles and crystal drops. */
    private static void chandelier(PoseStack pose,MultiBufferSource buffers,int light){
        EscortRenderer.box(pose,buffers,light,Blocks.CHAIN,-.5,22/16.,-.5,1,10/16f,1);
        b(pose,buffers,light,Blocks.GOLD_BLOCK,-4,30,-4,4,32,4);
        b(pose,buffers,light,Blocks.GOLD_BLOCK,-1.5,8,-1.5,1.5,22,1.5);
        b(pose,buffers,light,Blocks.GOLD_BLOCK,-6,6,-6,6,9,6);b(pose,buffers,light,Blocks.GOLD_BLOCK,-3,4,-3,3,6,3);
        b(pose,buffers,light,Blocks.GOLD_BLOCK,-4,15.5,-4,4,17,4);
        arms(pose,buffers,light,8,20,9,0);arms(pose,buffers,light,6,11,16,30);
    }
    private static void arms(PoseStack pose,MultiBufferSource buffers,int light,int count,double radius,double y,double offset){
        for(int i=0;i<count;i++){
            pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees((float)(offset+i*360./count)));
            b(pose,buffers,light,Blocks.GOLD_BLOCK,-.75,y,0,.75,y+1.5,radius);
            b(pose,buffers,light,Blocks.GOLD_BLOCK,-1.6,y+1.5,radius-1.6,1.6,y+2.6,radius+1.6);
            b(pose,buffers,light,Blocks.WHITE_CONCRETE,-.8,y+2.6,radius-.8,.8,y+7,radius+.8);
            b(pose,buffers,BRIGHT,Blocks.SHROOMLIGHT,-.5,y+7,radius-.5,.5,y+8.6,radius+.5);
            b(pose,buffers,BRIGHT,Blocks.GLOWSTONE,-.25,y+8.6,radius-.25,.25,y+9.3,radius+.25);
            b(pose,buffers,light,Blocks.LIGHT_BLUE_STAINED_GLASS,-.6,y-3.5,radius-2.6,.6,y,radius-1.4);
            pose.popPose();
        }
    }

    // ---- 247 slot machine -----------------------------------------------------------------------------------------

    private static final int[] SYMBOL_COLORS={0xE01818,0x1E9E3A,0xE8B000,0xF0508C,0x202028};
    private static void slot(PartyEntity be,PoseStack pose,MultiBufferSource buffers,int light,float time){
        b(pose,buffers,light,Blocks.RED_CONCRETE,-8,0,-6,8,15,8);
        b(pose,buffers,light,Blocks.GOLD_BLOCK,-8.5,14,-7.5,8.5,15.5,-5.5);
        b(pose,buffers,light,Blocks.RED_CONCRETE,-8,15.5,-3,8,28,8);
        b(pose,buffers,light,Blocks.GOLD_BLOCK,-8.5,28,-4,8.5,29,8.5);
        b(pose,buffers,light,Blocks.YELLOW_CONCRETE,-8,29,-3.5,8,32,8);
        text(pose,buffers,"777",0,30.5,-3.55,2.6f,0xD01010,BRIGHT);
        b(pose,buffers,light,Blocks.BLACK_CONCRETE,-7,17,-3.3,7,27,-3);
        b(pose,buffers,light,Blocks.BLACK_CONCRETE,-4,3,-7,4,5,-6);
        b(pose,buffers,light,Blocks.GOLD_BLOCK,-1,10,-6.2,1,12,-6);
        b(pose,buffers,light,Blocks.IRON_BLOCK,8,16,1,9.5,26,2.5);b(pose,buffers,light,Blocks.RED_CONCRETE,7.75,26,.75,9.75,28,2.75);
        float t=time-be.spinAt;boolean spinning=t>=0&&t<PartyBlocks.SPIN_TICKS;
        for(int i=0;i<4;i++){boolean on=!spinning||((int)(time/3)+i)%2==0;b(pose,buffers,on?BRIGHT:light,on?Blocks.GLOWSTONE:Blocks.YELLOW_CONCRETE,-6.5+i*4,28.2,-4.4,-5.5+i*4,28.9,-4);}
        for(int i=0;i<3;i++){
            double x=(i-1)*4.6;
            b(pose,buffers,BRIGHT,Blocks.WHITE_CONCRETE,x-2,18,-3.6,x+2,26,-3.3);
            float stop=PartyBlocks.SPIN_TICKS/2f+i*PartyBlocks.SPIN_TICKS/6f;int symbol;double shift=0;
            if(t>=0&&t<stop){float roll=t*.9f+i*2.3f;symbol=Math.floorMod((int)roll,PartyBlocks.SYMBOLS.length);shift=(roll-Math.floor(roll))*-2.5;}
            else symbol=be.reels[i];
            text(pose,buffers,PartyBlocks.SYMBOLS[symbol],x,22+shift,-3.65,5,SYMBOL_COLORS[symbol],BRIGHT);
        }
    }

    // ---- 248 billiard ball ----------------------------------------------------------------------------------------

    private static final int[] BALL_COLORS={0xF2C12E,0x1F4FBF,0xD02828,0x5B2C8F,0xF07D1A,0x167A3C,0x7A1F1F,0x141414};
    private static void ball(PartyEntity be,PoseStack pose,MultiBufferSource buffers,int light){
        var state=be.getBlockState();int ball=state.hasProperty(PartyBlocks.Ball.BALL)?state.getValue(PartyBlocks.Ball.BALL):0;
        boolean numbered=ball<15,stripe=ball>=8&&ball<15;
        // 1~8 solids (8 black), 9~15 stripes in the colours of 1~7; then the cue ball and the carom yellow and red.
        int color=ball<8?BALL_COLORS[ball]:ball<15?BALL_COLORS[ball-8]:ball==15?0xF4F1E6:ball==16?0xF2D64B:0xD02828;
        int white=0xF4F1E6;float r=6;
        pose.pushPose();pose.translate(0,r/16,0);
        if(be.disguise)pose.mulPose(be.roll);
        var c=buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        int rings=12,segments=20;
        for(int i=0;i<rings;i++)for(int j=0;j<segments;j++){
            double t0=Math.PI*i/rings,t1=Math.PI*(i+1)/rings,p0=2*Math.PI*j/segments,p1=2*Math.PI*(j+1)/segments;
            double tm=(t0+t1)/2,pm=(p0+p1)/2;double dz=Math.cos(tm),dy=Math.sin(tm)*Math.sin(pm);
            int col=numbered&&Math.abs(dz)>.9?white:stripe?(Math.abs(dy)<.42?color:white):color;
            float[][] p=new float[4][];double[][] at={{t0,p0},{t1,p0},{t1,p1},{t0,p1}};
            for(int k=0;k<4;k++){double th=at[k][0],ph=at[k][1];p[k]=new float[]{(float)(Math.sin(th)*Math.cos(ph)*r),(float)(Math.sin(th)*Math.sin(ph)*r),(float)(Math.cos(th)*r)};}
            float nx=(float)(Math.sin(tm)*Math.cos(pm)),ny=(float)dy,nz=(float)dz;
            quad(pose,c,light,p,new int[]{col,col,col,col},nx,ny,nz);
        }
        if(numbered){
            String n=String.valueOf(ball+1);
            text(pose,buffers,n,0,0,-r-.06,2.6f,0x111111,light);
            pose.mulPose(Axis.YP.rotationDegrees(180));text(pose,buffers,n,0,0,-r-.06,2.6f,0x111111,light);
        }
        pose.popPose();
    }

    // ---- 249 wishing well -----------------------------------------------------------------------------------------

    private static void well(PartyEntity be,PoseStack pose,MultiBufferSource buffers,int light){
        be.bucket+=((be.down?1:0)-be.bucket)*.06f;float depth=be.bucket;
        b(pose,buffers,light,Blocks.STONE_BRICKS,-22,0,-22,22,14,-15);b(pose,buffers,light,Blocks.STONE_BRICKS,-22,0,15,22,14,22);
        b(pose,buffers,light,Blocks.MOSSY_STONE_BRICKS,-22,0,-15,-15,14,15);b(pose,buffers,light,Blocks.MOSSY_STONE_BRICKS,15,0,-15,22,14,15);
        b(pose,buffers,light,Blocks.SMOOTH_STONE,-23,14,-23,23,16,-15);b(pose,buffers,light,Blocks.SMOOTH_STONE,-23,14,15,23,16,23);
        b(pose,buffers,light,Blocks.SMOOTH_STONE,-23,14,-15,-15,16,15);b(pose,buffers,light,Blocks.SMOOTH_STONE,15,14,-15,23,16,15);
        b(pose,buffers,light,Blocks.COBBLESTONE,-15,0,-15,15,5,15);
        pose.pushPose();pose.translate(-15/16.,5/16.,-15/16.);pose.scale(30/16f,4/16f,30/16f);
        PropRenderer.fluid(net.minecraft.world.level.material.Fluids.WATER.getSource(false),be.getLevel(),be.getBlockPos(),pose,buffers,light);
        pose.popPose();
        b(pose,buffers,light,Blocks.OAK_LOG,-21,16,-2,-17,40,2);b(pose,buffers,light,Blocks.OAK_LOG,17,16,-2,21,40,2);
        b(pose,buffers,light,Blocks.DARK_OAK_LOG,-26,42.5,-1,26,45,1);
        for(int side=-1;side<=1;side+=2){
            pose.pushPose();pose.translate(0,44/16.,0);pose.mulPose(Axis.XP.rotationDegrees(35*side));
            if(side>0)b(pose,buffers,light,Blocks.SPRUCE_PLANKS,-26,-2,0,26,0,19);else b(pose,buffers,light,Blocks.SPRUCE_PLANKS,-26,-2,-19,26,0,0);
            pose.popPose();
        }
        // The axle and crank turn as the rope winds; the bucket hangs from the middle.
        pose.pushPose();pose.translate(0,32.5/16,0);pose.mulPose(Axis.XP.rotationDegrees(depth*-540));
        b(pose,buffers,light,Blocks.STRIPPED_OAK_LOG,-17,-1.5,-1.5,17,1.5,1.5);
        b(pose,buffers,light,Blocks.IRON_BLOCK,21,-.7,-.7,22.4,6,.7);b(pose,buffers,light,Blocks.DARK_OAK_PLANKS,22.4,4.6,-.8,26,6,.8);
        b(pose,buffers,light,Blocks.IRON_BLOCK,17,-.6,-.6,21,.6,.6);
        pose.popPose();
        double bottom=21-depth*14;
        b(pose,buffers,light,Blocks.BROWN_WOOL,-.4,bottom+5,-.4,.4,31,.4);
        EscortRenderer.box(pose,buffers,light,Blocks.CAULDRON,-3/16.,bottom/16,-3/16.,6/16f,5/16f,6/16f);
        b(pose,buffers,light,Blocks.IRON_BLOCK,-3,bottom+5,-.3,3,bottom+5.6,.3);
    }

    // ---- 250 statue of honorary embezzlement -------------------------------------------------------------------

    /**
     * ArchBear's skin in copper patina at 2.5 times, on a stone plinth: a crown of rays, the torch held high in the
     * right hand (its flame lit) and a money bag in the left, like the Statue of Liberty.
     */
    private static void statue(PoseStack pose,MultiBufferSource buffers,int light,float time){
        b(pose,buffers,light,Blocks.STONE_BRICKS,-24,0,-24,24,6,24);
        b(pose,buffers,light,Blocks.SMOOTH_STONE,-20,6,-20,20,18,20);
        b(pose,buffers,light,Blocks.CHISELED_STONE_BRICKS,-22,18,-22,22,20,22);
        b(pose,buffers,light,Blocks.GOLD_BLOCK,-10,9,-20.4,10,15,-20);
        text(pose,buffers,"명예횡령상",0,12,-20.45,3.2f,0x3A2A00,light);
        float k=2.5f;double feet=20;
        pose.pushPose();pose.translate(0,feet/16,0);pose.scale(k,k,k);
        figure(pose,buffers,light,STATUE_SKIN,root->{root.getChild("right_arm").xRot=(float)-Math.PI;root.getChild("left_arm").xRot=-.9f;root.getChild("left_arm").zRot=-.08f;});
        pose.popPose();
        Block patina=Blocks.OXIDIZED_COPPER,cut=Blocks.OXIDIZED_CUT_COPPER;
        // Crown: a band round the head and seven rays over its front.
        b(pose,buffers,light,cut,-11.6,92,-11.6,11.6,95,11.6);
        for(int i=0;i<7;i++){
            double a=Math.toRadians(-75+i*25);
            pose.pushPose();pose.translate(Math.sin(a)*11/16,95/16.,-Math.cos(a)*11/16);
            pose.mulPose(Axis.YP.rotationDegrees((float)-Math.toDegrees(a)));pose.mulPose(Axis.XP.rotationDegrees(-28));
            b(pose,buffers,light,patina,-1,0,-.6,1,i==3?16:13,.6);
            pose.popPose();
        }
        // Torch in the raised right hand.
        b(pose,buffers,light,cut,13,98,-2,17,112,2);
        b(pose,buffers,light,patina,11,112,-4,19,115,4);
        float flicker=(float)Math.sin(time*.7)*.6f;
        b(pose,buffers,BRIGHT,Blocks.GOLD_BLOCK,12,115,-3,18,123+flicker,3);
        b(pose,buffers,BRIGHT,Blocks.SHROOMLIGHT,13.5,116,-1.5,16.5,127+flicker,1.5);
        // Money bag hanging from the left hand, a gold tie and dollar sign.
        b(pose,buffers,light,patina,-21,42,-21,-9,56,-11);
        b(pose,buffers,light,cut,-18,56,-18,-12,60,-14);
        b(pose,buffers,light,Blocks.GOLD_BLOCK,-18.6,56,-18.6,-11.4,57.6,-13.4);
        text(pose,buffers,"$",-15,49,-21.1,8,0xF2C230,BRIGHT);
    }

    // ---- 251 Christmas tree ---------------------------------------------------------------------------------------

    private static final double[][] TIERS={{10,20,21},{18,28,17},{26,36,13.5},{34,44,10},{42,51,6.8},{49,56,3.8}};
    private static final Block[] BAUBLES={Blocks.RED_CONCRETE,Blocks.GOLD_BLOCK,Blocks.BLUE_CONCRETE,Blocks.WHITE_CONCRETE,Blocks.PURPLE_CONCRETE};
    private static final Block[] BULBS={Blocks.RED_CONCRETE,Blocks.YELLOW_CONCRETE,Blocks.LIME_CONCRETE,Blocks.LIGHT_BLUE_CONCRETE,Blocks.PINK_CONCRETE};
    private static void tree(PartyEntity be,PoseStack pose,MultiBufferSource buffers,int light,float time){
        b(pose,buffers,light,Blocks.RED_CONCRETE,-6,0,-6,6,7,6);b(pose,buffers,light,Blocks.GOLD_BLOCK,-6.3,5,-6.3,6.3,6,6.3);
        b(pose,buffers,light,Blocks.SPRUCE_LOG,-2,7,-2,2,11,2);
        int n=0;boolean on=!be.lightsOff;
        for(var tier:TIERS){
            double y1=tier[0],y2=tier[1],h=tier[2];
            b(pose,buffers,light,Blocks.SPRUCE_LEAVES,-h,y1,-h,h,y2,h);
            pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(45));b(pose,buffers,light,Blocks.SPRUCE_LEAVES,-h*.82,y1+.5,-h*.82,h*.82,y2-.5,h*.82);pose.popPose();
            for(int i=0;i<8;i++){
                double a=Math.toRadians(i*45+(n%2)*22.5);double rr=i%2==0?h+.3:h*.82*1.414*.92;
                double x=Math.sin(a)*rr,z=Math.cos(a)*rr,y=y1+1.2;
                boolean lit=on&&((int)(time/8)+i+n)%3!=0;
                b(pose,buffers,lit?BRIGHT:light,BULBS[(i+n)%BULBS.length],x-.7,y,z-.7,x+.7,y+1.4,z+.7);
                if(i%2==1&&y2-y1>6){double by=(y1+y2)/2,ba=a+Math.toRadians(22.5),br=h*.95;double bx=Math.sin(ba)*br,bz=Math.cos(ba)*br;
                    b(pose,buffers,light,BAUBLES[(i/2+n)%BAUBLES.length],bx-1.3,by-1.3,bz-1.3,bx+1.3,by+1.3,bz+1.3);}
            }
            n++;
        }
        int starLight=on?BRIGHT:light;
        pose.pushPose();pose.translate(0,59/16.,0);
        b(pose,buffers,starLight,Blocks.GOLD_BLOCK,-2.5,-2.5,-.75,2.5,2.5,.75);
        pose.mulPose(Axis.ZP.rotationDegrees(45));b(pose,buffers,starLight,Blocks.GOLD_BLOCK,-2.5,-2.5,-.7,2.5,2.5,.7);
        pose.popPose();
    }

    // ---- 252 Sanga's doll -----------------------------------------------------------------------------------------

    /** Sanga's skin on a skateboard, with a helmet and knee and elbow pads; E poses her like the other dolls. */
    private static void sanga(PartyEntity be,PoseStack pose,MultiBufferSource buffers,int light){
        b(pose,buffers,light,Blocks.DARK_OAK_PLANKS,-4.5,1.6,-12,4.5,2.6,12);
        b(pose,buffers,light,Blocks.BLACK_CONCRETE,-4.3,2.6,-11,4.3,3,11);
        b(pose,buffers,light,Blocks.DARK_OAK_PLANKS,-4.5,2.6,-14,4.5,3.6,-12);b(pose,buffers,light,Blocks.DARK_OAK_PLANKS,-4.5,2.6,12,4.5,3.6,14);
        for(int z=-1;z<=1;z+=2){
            b(pose,buffers,light,Blocks.IRON_BLOCK,-3.5,1,z*8-1,3.5,1.6,z*8+1);
            for(int x=-1;x<=1;x+=2)b(pose,buffers,light,Blocks.PINK_CONCRETE,x*3.6-.9,0,z*8-.9,x*3.6+.9,1.8,z*8+.9);
        }
        int mode=be.disguise?be.mode:0;
        pose.pushPose();pose.translate(0,3/16.,0);
        figure(pose,buffers,light,SANGA_SKIN,root->{
            var head=root.getChild("head");var leftArm=root.getChild("left_arm");var rightArm=root.getChild("right_arm");var leftLeg=root.getChild("left_leg");var rightLeg=root.getChild("right_leg");
            leftArm.zRot=mode==1?-1.57f:mode==2?-.8f:0;rightArm.zRot=mode==1?1.57f:mode==2?.8f:0;
            if(mode==3){leftArm.xRot=-2.6f;rightArm.xRot=-2.6f;}
            if(mode==4){leftLeg.xRot=-1.5f;rightLeg.xRot=-1.5f;leftArm.xRot=-.7f;rightArm.xRot=-.7f;}
            if(mode==5){rightArm.zRot=2.5f;head.zRot=.25f;}
        });
        // Helmet over the head (the figure's head spans 24..32 above its feet), with a pink stripe.
        b(pose,buffers,light,Blocks.WHITE_CONCRETE,-4.8,28.4,-4.8,4.8,32.9,4.8);
        b(pose,buffers,light,Blocks.PINK_CONCRETE,-.8,32.9,-4.9,.8,33.2,4.9);
        b(pose,buffers,light,Blocks.WHITE_CONCRETE,-4.8,28.4,-5.8,4.8,29,-4.8);
        if(mode!=4){b(pose,buffers,light,Blocks.BLACK_CONCRETE,0,4.5,-2.6,3.9,7.5,-1.8);b(pose,buffers,light,Blocks.BLACK_CONCRETE,-3.9,4.5,-2.6,0,7.5,-1.8);}
        if(mode==0){b(pose,buffers,light,Blocks.BLACK_CONCRETE,4,14.5,-2.6,8,17.5,-1.8);b(pose,buffers,light,Blocks.BLACK_CONCRETE,-8,14.5,-2.6,-4,17.5,-1.8);}
        pose.popPose();
    }

    // ---- 254 unpleasant photo -------------------------------------------------------------------------------------

    private static final ResourceLocation PHOTO=new ResourceLocation("gungang","textures/entity/photo/unpleasant.png");
    /** A framed photo on a little back stand, 14 pixels wide and 22 tall (the picture is 266x380). */
    private static void photo(PoseStack pose,MultiBufferSource buffers,int light){
        b(pose,buffers,light,Blocks.DARK_OAK_PLANKS,-7,0,-.5,7,22,1);
        b(pose,buffers,light,Blocks.DARK_OAK_PLANKS,-1,0,1,1,14,5.5);
        var c=buffers.getBuffer(RenderType.entityCutoutNoCull(PHOTO));var m=pose.last().pose();var n=pose.last().normal();
        float x1=6/16f,x2=-6/16f,y1=1.2f/16,y2=20.8f/16,z=-.55f/16;
        // Front faces -z: the viewer's left is +x, so the picture's left edge (u = 0) is at +x.
        c.vertex(m,x1,y1,z).color(255,255,255,255).uv(0,1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,0,0,-1).endVertex();
        c.vertex(m,x2,y1,z).color(255,255,255,255).uv(1,1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,0,0,-1).endVertex();
        c.vertex(m,x2,y2,z).color(255,255,255,255).uv(1,0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,0,0,-1).endVertex();
        c.vertex(m,x1,y2,z).color(255,255,255,255).uv(0,0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,0,0,-1).endVertex();
    }

    // ---- 255 autonomous combat unit -------------------------------------------------------------------------------

    private static net.minecraft.world.item.ItemStack minigun;
    /** The minigun weapon 18 hands out (TaCZ), or empty without TaCZ. */
    private static net.minecraft.world.item.ItemStack minigun(){
        if(minigun==null){
            try{minigun=net.minecraft.world.item.ItemStack.of(net.minecraft.nbt.TagParser.parseTag("{Count:1b,id:\"tacz:modern_kinetic_gun\",tag:{GunId:\"tacz:minigun\",GunFireMode:\"AUTO\",HasBulletInBarrel:1b}}"));}
            catch(Exception e){minigun=net.minecraft.world.item.ItemStack.EMPTY;}
        }
        return minigun;
    }
    /** The mannequin (a seeker's skin) holding the minigun level in both hands. */
    private static void auto(PartyEntity be,PoseStack pose,MultiBufferSource buffers,int light){
        var skin=be.skin==null?net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin():MatchHud.skin(be.skin.toString());
        var gun=minigun();var mc=Minecraft.getInstance();
        figure(pose,buffers,light,skin,root->{
            var right=root.getChild("right_arm");var left=root.getChild("left_arm");
            right.xRot=-1.4f;right.yRot=-.1f;left.xRot=-1.4f;left.yRot=.5f;
        },root->{
            if(gun.isEmpty())return;
            pose.pushPose();root.getChild("right_arm").translateAndRotate(pose);
            pose.mulPose(Axis.XP.rotationDegrees(-90));pose.mulPose(Axis.YP.rotationDegrees(180));pose.translate(1/16f,.125f,-.625f);
            try{mc.getItemRenderer().renderStatic(gun,net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,light,OverlayTexture.NO_OVERLAY,pose,buffers,be.getLevel(),0);}catch(RuntimeException ignored){}
            pose.popPose();
        });
    }

    // ---- 256 shadow -----------------------------------------------------------------------------------------------

    private static final ResourceLocation SHADOW=new ResourceLocation("minecraft","textures/misc/shadow.png");
    /** Only a soft black shadow on the floor. */
    private static void shadow(PoseStack pose,MultiBufferSource buffers,int light){
        var c=buffers.getBuffer(RenderType.entityShadow(SHADOW));var m=pose.last().pose();var n=pose.last().normal();
        float r=.62f,y=.016f;int a=230;
        c.vertex(m,-r,y,-r).color(0,0,0,a).uv(0,0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,0,1,0).endVertex();
        c.vertex(m,-r,y,r).color(0,0,0,a).uv(0,1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,0,1,0).endVertex();
        c.vertex(m,r,y,r).color(0,0,0,a).uv(1,1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,0,1,0).endVertex();
        c.vertex(m,r,y,-r).color(0,0,0,a).uv(1,0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,0,1,0).endVertex();
    }
}
