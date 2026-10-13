package dev.gungang.acoustics.server;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class GadgetItem extends Item {
    public final String kind;
    public GadgetItem(String kind) { super(new Properties().stacksTo(1));this.kind=kind; }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        var stack=player.getItemInHand(hand);
        if(player.isSpectator())return InteractionResultHolder.fail(stack);
        // A primary weapon returning PASS must not activate the carried deployable.
        // The explicit H macro swaps it into MAIN_HAND before invoking use.
        if(hand==InteractionHand.OFF_HAND&&placement(kind))return InteractionResultHolder.pass(stack);
        var aimed=kind.equals("landmine")?GadgetManager.aimedMine(player):null;
        if(aimed!=null) {player.getPersistentData().putUUID("GunGangDisarming",aimed.getUUID());player.startUsingItem(hand);return InteractionResultHolder.consume(stack);}
        if(level.isClientSide&&placement(kind))net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->dev.gungang.acoustics.client.GadgetClient.expect(kind));
        if(!level.isClientSide&&player instanceof net.minecraft.server.level.ServerPlayer server)GadgetManager.use(server,stack,kind);
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    @Override public int getUseDuration(ItemStack stack) { return 72000; }
    public static boolean placement(String kind) {return kind.equals("landmine")||kind.equals("barrier")||kind.equals("orbital_strike");}
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }
    @Override public void onUseTick(Level level,LivingEntity entity,ItemStack stack,int remaining) {
        if(entity instanceof net.minecraft.server.level.ServerPlayer player) {
            var mine=GadgetManager.aimedMine(player);
            if(mine==null||!player.getPersistentData().hasUUID("GunGangDisarming")||!mine.getUUID().equals(player.getPersistentData().getUUID("GunGangDisarming"))) { player.stopUsingItem();return; }
            int elapsed=getUseDuration(stack)-remaining;
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("지뢰 철거 · "+Math.min(100,(int)(elapsed*100/(GadgetConfig.mine.disarmSeconds()*20)))+"%"),true);
            if(elapsed>=GadgetConfig.mine.disarmSeconds()*20) { mine.discard();player.stopUsingItem(); }
        }
    }
}
