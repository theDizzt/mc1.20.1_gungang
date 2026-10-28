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
        name=mc.font.plainSubstrByWidth(name,Math.max(1,mc.getWindow().getWidth()/4-18));
        TaczAmmoHud.text(g,mc.font,name,l.right()-mc.font.width(name),l.rowTop()-11,0xFFE2E8F0);
        icon(g,row.block(),state,l.iconX()+26,l.iconY());
        String number=dev.gungang.acoustics.server.PropCatalog.number(row.block());
        if(number.chars().allMatch(Character::isDigit))number="0".repeat(Math.max(0,3-number.length()))+number;else number="???";
        g.pose().pushPose();g.pose().translate(l.right()-l.countW(),l.countTop(),0);g.pose().scale(TaczAmmoHud.AMMO_SCALE,TaczAmmoHud.AMMO_SCALE,1);
        // This is an identifier: keep all three digits equally legible, including leading zeros.
        TaczAmmoHud.digits(g,mc.font,number,0,0xFFFFFFFF);g.pose().popPose();
        if(row.cooldownTotal()>0)TaczAmmoHud.bar(g,l,1-Math.max(0,remaining)/row.cooldownTotal(),remaining>0?0xFFFFC36B:0xFF75D9AD);
        g.pose().popPose();
    }
    private static void icon(GuiGraphics g,String id,net.minecraft.world.level.block.state.BlockState state,int x,int y){
        var mc=Minecraft.getInstance();var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(dev.gungang.acoustics.server.PropCatalog.id(id));
        // Stateful flower pots/anvils must show the current disguise, not the original draw.
        if(id.equals("minecraft:anvil")||id.equals("minecraft:infested_stone"))item=state.getBlock().asItem();
        g.pose().pushPose();
        if(item!=null&&item!=net.minecraft.world.item.Items.AIR&&!id.equals("minecraft:flower_pot")){
            g.pose().translate(x,y,0);g.pose().scale(26f/16,26f/16,1);
            g.renderItem(new net.minecraft.world.item.ItemStack(item),0,0);
        }else{
            // Fire, portals and planted pots have no matching inventory item.
            g.pose().translate(x+13,y+23,150);g.pose().scale(22,-22,22);
            g.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(25));g.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(225));g.pose().translate(-.5,0,-.5);
            mc.getBlockRenderer().renderSingleBlock(state,g.pose(),g.bufferSource(),0xF000F0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);g.flush();
        }
        g.pose().popPose();
    }
    static int gauges(GuiGraphics g,int center,int y,int width,int height,float partial){
        var row=PropHuntClient.ability();if(row==null)return y;
        float active=Math.max(0,row.active()-PropHuntClient.elapsed(partial));
        if(active>0){UtilityHud.gauge(g,center,y,width,height,active,100,String.format(java.util.Locale.ROOT,"수평 이동 %.1f초",active/20),partial);y-=height+4;}
        if(row.auxiliaryMax()>0){UtilityHud.gauge(g,center,y,width,height,row.auxiliary(),row.auxiliaryMax(),"독거미 "+HudHealth.number(row.auxiliary())+"/"+HudHealth.number(row.auxiliaryMax()),partial);y-=height+4;}
        return y;
    }
    private PropAbilityHud(){}
}
