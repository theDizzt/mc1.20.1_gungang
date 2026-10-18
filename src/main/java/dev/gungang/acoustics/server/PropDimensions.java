package dev.gungang.acoustics.server;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID)
public final class PropDimensions {
    public static java.util.function.Function<Player,PropEntity> clientProp=p->null;
    @SubscribeEvent public static void size(net.minecraftforge.event.entity.EntityEvent.Size e){
        if(!(e.getEntity() instanceof Player p)||p.level()==null)return;
        var prop=prop(p);if(prop==null)return;
        var box=prop.getBoundingBox();float width=(float)Math.max(.1,Math.max(box.getXsize(),box.getZsize())),height=(float)Math.max(.1,box.getYsize());
        // The health pack's hologram and sunk part are shot targets only; the hider keeps the block's own size.
        if(prop.healthPack())height=1;
        e.setNewSize(EntityDimensions.scalable(width,height));e.setNewEyeHeight(Math.max(.05f,height*.85f));
    }
    /** The live disguise of a hiding player on either side, or null. */
    public static PropEntity prop(Player p){var match=MatchManager.propHunt();return p.level().isClientSide?clientProp.apply(p):match==null?null:match.prop(p.getUUID());}
    private PropDimensions(){}
}
