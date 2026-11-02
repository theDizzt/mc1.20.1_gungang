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
        // A block with its own E effect says so in yellow right after its name.
        // A random colour variant says "색상" in pink.
        boolean special=dev.gungang.acoustics.server.PropInteraction.special(row.block()),colored=dev.gungang.acoustics.server.PropCatalog.colored(row.block());
        int specialW=special?mc.font.width("특수")+4:0,colorW=colored?mc.font.width("색상")+4:0;
        name=mc.font.plainSubstrByWidth(name,Math.max(1,mc.getWindow().getWidth()/4-18-specialW-colorW));
        TaczAmmoHud.text(g,mc.font,name,l.right()-specialW-colorW-mc.font.width(name),l.rowTop()-11,0xFFE2E8F0);
        if(special)TaczAmmoHud.text(g,mc.font,"특수",l.right()-colorW-mc.font.width("특수"),l.rowTop()-11,0xFFFFD84A);
        if(colored)TaczAmmoHud.text(g,mc.font,"색상",l.right()-mc.font.width("색상"),l.rowTop()-11,0xFFFF8AD0);
        icon(g,row.block(),state,l.iconX()+26,l.iconY());
        String number=dev.gungang.acoustics.server.PropCatalog.number(row.block());
        if(number.chars().allMatch(Character::isDigit))number="0".repeat(Math.max(0,3-number.length()))+number;else number="???";
        g.pose().pushPose();g.pose().translate(l.right()-l.countW(),l.countTop(),0);g.pose().scale(TaczAmmoHud.AMMO_SCALE,TaczAmmoHud.AMMO_SCALE,1);
        // This is an identifier: keep all three digits equally legible, including leading zeros.
        TaczAmmoHud.digits(g,mc.font,number,0,0xFFFFFFFF);g.pose().popPose();
        if(row.cooldownTotal()>0)TaczAmmoHud.bar(g,l,1-Math.max(0,remaining)/row.cooldownTotal(),remaining>0?0xFFFFC36B:0xFF75D9AD);
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
        if(item!=null&&item!=net.minecraft.world.item.Items.AIR&&!id.equals("minecraft:flower_pot")&&!liquid){
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
    static void jump(GuiGraphics g,int x,int y,int width,float partial){
        float charge=PropHuntClient.charge(partial);if(charge<0)return;
        g.fill(x,y,x+width,y+2,0x99000000);
        g.fill(x,y,x+Math.round(width*charge),y+2,charge>=1?0xFFFFF27A:0xFFFFD34D);
    }
    static int gauges(GuiGraphics g,int center,int y,int width,int height,float partial){
        var row=PropHuntClient.ability();if(row==null)return y;
        float active=Math.max(0,row.active()-PropHuntClient.elapsed(partial));
        if(active>0){UtilityHud.gauge(g,center,y,width,height,active,100,String.format(java.util.Locale.ROOT,"수평 이동 %.1f초",active/20),partial);y-=height+4;}
        if(row.auxiliaryMax()>0){
            // Dolls show their gauge in seconds (184 beam, 191 cloak); a spawner its spider's health.
            String label=switch(row.block()){
                case "gungang:eunbi_doll"->String.format(java.util.Locale.ROOT,"광선 %.1f초",row.auxiliary());
                case "gungang:hanbi_doll"->String.format(java.util.Locale.ROOT,"투명화 %.1f초",row.auxiliary());
                default->"독거미 "+HudHealth.number(row.auxiliary())+"/"+HudHealth.number(row.auxiliaryMax());
            };
            UtilityHud.gauge(g,center,y,width,height,row.auxiliary(),row.auxiliaryMax(),label,partial);y-=height+4;
        }
        return y;
    }
    private PropAbilityHud(){}
}
