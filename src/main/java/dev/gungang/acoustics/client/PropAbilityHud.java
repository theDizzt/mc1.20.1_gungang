package dev.gungang.acoustics.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
final class PropAbilityHud {
    static boolean ammo(GuiGraphics g,float partial){
        var row=PropHuntClient.ability();if(row==null||row.block().isEmpty())return false;
        drawAmmo(g,row,Math.max(0,row.cooldown()-PropHuntClient.elapsed(partial)));return true;
    }
    static void drawAmmo(GuiGraphics g,dev.gungang.acoustics.server.PropHuntSnapshot.Row row,float remaining){
        var mc=Minecraft.getInstance();var l=TaczAmmoHud.layout(3);
        var state=net.minecraft.world.level.block.Block.stateById(row.state());
        if(state.isAir())state=dev.gungang.acoustics.server.PropCatalog.state(row.block());
        float scale=(float)(2/mc.getWindow().getGuiScale());g.pose().pushPose();g.pose().scale(scale,scale,1);
        String name=dev.gungang.acoustics.server.PropCatalog.name(row.block(),state).getString();
        // Its categories (공격, 디버프, 견고, 접착, 신속, 액체, 광원, 특수, 색상) follow the name in their colours.
        var tags=dev.gungang.acoustics.server.PropInteraction.tags(row.block(),state);
        int tagsW=0;for(var t:tags)tagsW+=mc.font.width(t.label())+4;
        int room=Math.max(1,mc.getWindow().getWidth()/4-18-tagsW);
        TaczAmmoHud.textMarquee(g,mc.font,name,l.right()-tagsW-Math.min(room,mc.font.width(name)),l.rowTop()-11,room,0xFFE2E8F0);
        int tx=l.right()-tagsW+4;
        for(var t:tags){TaczAmmoHud.text(g,mc.font,t.label(),tx,l.rowTop()-11,t.color());tx+=mc.font.width(t.label())+4;}
        icon(g,row.block(),state,l.iconX()+26,l.iconY());
        String number=dev.gungang.acoustics.server.PropCatalog.number(row.block());
        if(number.chars().allMatch(Character::isDigit))number="0".repeat(Math.max(0,3-number.length()))+number;else number="???";
        g.pose().pushPose();g.pose().translate(l.right()-l.countW(),l.countTop(),0);g.pose().scale(TaczAmmoHud.AMMO_SCALE,TaczAmmoHud.AMMO_SCALE,1);
        // This is an identifier: keep all three digits equally legible, including leading zeros.
        TaczAmmoHud.digits(g,mc.font,number,0,0xFFFFFFFF);g.pose().popPose();
        if(row.cooldownTotal()>0)TaczAmmoHud.bar(g,l,1-Math.max(0,remaining)/row.cooldownTotal(),remaining>0?0xFFFFC36B:0xFF75D9AD);
        // 119 · 189 · 222 · 242 · 248 · 252: the speedometer sits over the name row.
        speedometer(g,l.right(),l.rowTop()-15);
        g.pose().popPose();
    }
    private static void icon(GuiGraphics g,String id,net.minecraft.world.level.block.state.BlockState state,int x,int y){icon(g,id,state,x,y,26);}
    /**
     * A disguise icon {@code size} pixels wide: its item, or else the block itself drawn in 3D (fire, portals, planted
     * pots) — and for liquids (water, hot spring water, lava, acid, bubble column), which have neither, a source block
     * of the liquid; the bubble column gets its bubbles on top.
     */
    static void icon(GuiGraphics g,String id,net.minecraft.world.level.block.state.BlockState state,int x,int y,int size){
        var mc=Minecraft.getInstance();var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(dev.gungang.acoustics.server.PropCatalog.id(id));
        // Stateful flower pots/anvils must show the current disguise, not the original draw.
        if(id.equals("minecraft:anvil")||id.equals("minecraft:infested_stone"))item=state.getBlock().asItem();
        g.pose().pushPose();
        float unit=size/26f;
        boolean liquid=state.getRenderShape()!=net.minecraft.world.level.block.RenderShape.MODEL&&!state.getFluidState().isEmpty();
        // 61 / 62 health packs show as a potion: light blue for the small pack, green for the large one.
        int potion=id.equals("gungang:small_health_pack")?0x55C8FF:id.equals("gungang:large_health_pack")?0x3FD44A:-1;
        if(potion>=0){
            var stack=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION);stack.getOrCreateTag().putInt("CustomPotionColor",potion);
            g.pose().translate(x,y,0);g.pose().scale(size/16f,size/16f,1);g.renderItem(stack,0,0);
        }else if(item!=null&&item!=net.minecraft.world.item.Items.AIR&&!id.equals("minecraft:flower_pot")&&!liquid){
            g.pose().translate(x,y,0);g.pose().scale(size/16f,size/16f,1);
            g.renderItem(new net.minecraft.world.item.ItemStack(item),0,0);
        }else{
            // Fire, portals and planted pots have no matching inventory item; liquids have no block model either.
            g.pose().translate(x+13*unit,y+23*unit,150);g.pose().scale(22*unit,-22*unit,22*unit);
            g.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(25));g.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(225));g.pose().translate(-.5,0,-.5);
            if(liquid)PropRenderer.fluid(state.getFluidState(),mc.level,mc.player==null?net.minecraft.core.BlockPos.ZERO:mc.player.blockPosition(),g.pose(),g.bufferSource(),0xF000F0);
            else mc.getBlockRenderer().renderSingleBlock(state,g.pose(),g.bufferSource(),0xF000F0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            g.flush();
        }
        g.pose().popPose();
        // The particle atlas belongs to the particle engine, not the model manager: getTextureAtlas() would crash on it.
        if(state.getBlock() instanceof net.minecraft.world.level.block.BubbleColumnBlock
                &&mc.getTextureManager().getTexture(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_PARTICLES) instanceof net.minecraft.client.renderer.texture.TextureAtlas atlas){
            var bubble=atlas.getSprite(new net.minecraft.resources.ResourceLocation("minecraft","bubble"));
            int b=Math.max(4,Math.round(7*unit));
            g.pose().pushPose();g.pose().translate(0,0,300);
            g.blit(x+Math.round(5*unit),y+Math.round(10*unit),0,b,b,bubble);
            g.blit(x+Math.round(14*unit),y+Math.round(4*unit),0,b-1,b-1,bubble);
            g.blit(x+Math.round(9*unit),y+Math.round(15*unit),0,b-2,b-2,bubble);
            g.pose().popPose();
        }
    }
    /** Hider's jump charge: a thin yellow line right above the health bar (below the compass), only while charging. */
    /** 119 minecart, 189 TNT minecart, 222 money cart, 242 light, 248 billiard ball, 252 Sanga's doll: the fast movers. */
    private static final java.util.Set<String> SPEEDOMETER=java.util.Set.of("minecraft:minecart","minecraft:tnt_minecart","gungang:money_cart","minecraft:light","gungang:billiard_ball","gungang:sanga_doll");
    /** Full bar at 1000 blocks per in-game hour (a sprint is about 280). */
    private static final float SPEEDOMETER_MAX=1000;
    private static float speed;
    /**
     * A small speedometer over the disguise's name in the ammo HUD (fixed two-pixel units, right edge {@code right},
     * bottom {@code bottom}): "000.0 B/h", blocks per in-game hour (1000 ticks), in the Gungang font with every digit in the same cell, so the reading never
     * shifts as it changes, over a bar that fills up to 1000 B/h, green, then yellow, then red. Speed is the body's motion
     * over the last tick, eased so it does not flicker.
     */
    static void speedometer(GuiGraphics g,int right,int bottom){
        var mc=Minecraft.getInstance();var p=mc.player;var block=PropHuntClient.disguise();
        if(p==null||block==null||!SPEEDOMETER.contains(block)){speed=0;return;}
        double dx=p.getX()-p.xo,dy=p.getY()-p.yo,dz=p.getZ()-p.zo;
        float target=(float)(Math.sqrt(dx*dx+dy*dy+dz*dz)*1000); // blocks per tick x 1000 ticks
        speed+=(target-speed)*.2f;if(speed<.05f)speed=0;
        String reading=String.format(java.util.Locale.ROOT,"%05.1f",Math.min(999.9f,speed));
        int cell=0;for(char c='0';c<='9';c++)cell=Math.max(cell,HudText.numberWidth(String.valueOf(c)));
        int dot=HudText.numberWidth("."),unit=HudText.numberWidth("B/h"),gap=3;
        int textW=4*cell+dot+gap+unit;float size=1.5f;
        int width=Math.round(textW*size),barH=3,textTop=bottom-barH-2-Math.round(8*size);
        g.pose().pushPose();g.pose().translate(right-width,textTop,0);g.pose().scale(size,size,1);
        int x=0;
        for(int i=0;i<reading.length();i++){
            String c=String.valueOf(reading.charAt(i));int w=c.equals(".")?dot:cell;
            HudText.drawNumber(g,HudText.number(c),x+(w-HudText.numberWidth(c))/2,0,0xFFFFFFFF);x+=w;
        }
        HudText.drawNumber(g,HudText.number("B/h"),x+gap,0,0xFFB8C2CF);
        g.pose().popPose();
        float fill=Math.min(1,speed/SPEEDOMETER_MAX);
        int color=fill<.4f?0xFF7CF2A0:fill<.75f?0xFFFFD34D:0xFFFF6B5A;
        g.fill(right-width,bottom-barH,right,bottom,0x99000000);
        g.fill(right-width,bottom-barH,right-width+Math.round(width*fill),bottom,color);
    }
    static void jump(GuiGraphics g,int x,int y,int width,float partial){
        float charge=PropHuntClient.charge(partial);if(charge<0)return;
        g.fill(x,y,x+width,y+2,0x99000000);
        g.fill(x,y,x+Math.round(width*charge),y+2,charge>=1?0xFFFFF27A:0xFFFFD34D);
    }
    static int gauges(GuiGraphics g,int center,int y,int width,int height,float partial){
        var row=PropHuntClient.ability();if(row==null)return y;
        float active=Math.max(0,row.active()-PropHuntClient.elapsed(partial));
        if(active>0){AuxBar.draw(g,center,y,width,height,AuxBar.Kind.COOLDOWN,active,100,AuxBar.seconds(active),Lang.tr("수평 이동"),partial);y-=height+4;}
        if(row.auxiliaryMax()>0){
            // Dolls show their gauge in seconds (184 beam, 191 cloak); a spawner its spider's health.
            switch(row.block()){
                case "gungang:eunbi_doll"->AuxBar.draw(g,center,y,width,height,AuxBar.Kind.COOLDOWN,row.auxiliary(),row.auxiliaryMax(),AuxBar.seconds(row.auxiliary()*20),Lang.tr("광선"),partial);
                case "gungang:hanbi_doll"->AuxBar.draw(g,center,y,width,height,AuxBar.Kind.COOLDOWN,row.auxiliary(),row.auxiliaryMax(),AuxBar.seconds(row.auxiliary()*20),Lang.tr("투명화"),partial);
                default->AuxBar.draw(g,center,y,width,height,AuxBar.Kind.HEALTH,row.auxiliary(),row.auxiliaryMax(),AuxBar.ratio(row.auxiliary(),row.auxiliaryMax()),Lang.tr("독거미"),partial);
            }
            y-=height+4;
        }
        return y;
    }
    private PropAbilityHud(){}
}
