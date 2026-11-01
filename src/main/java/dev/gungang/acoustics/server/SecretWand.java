package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Admin map tool: left click hides a block (it still works), right click makes a block walk-through (it still looks solid). */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class SecretWand extends Item {
    // Held clicks repeat every 4-5 ticks; without a gap one long press would flip the block back and forth.
    private static final Map<UUID,Long> LAST_USE=new HashMap<>();
    public SecretWand(){super(new Properties().stacksTo(1));}
    @Override public boolean isFoil(ItemStack stack){return true;}
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
        lines.add(Component.literal("좌클릭: 블록 투명화 (기능 유지)").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("우클릭: 블록 관통화 (겉모습 유지)").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("같은 클릭을 한 번 더 하면 원래대로 돌아옵니다.").withStyle(ChatFormatting.DARK_GRAY));
    }
    public static boolean holding(net.minecraft.world.entity.player.Player p){return p.getMainHandItem().is(GadgetItems.SECRET_WAND.get());}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void left(PlayerInteractEvent.LeftClickBlock e){
        if(!holding(e.getEntity()))return;e.setCanceled(true);
        if(e.getEntity() instanceof ServerPlayer p&&e.getAction()==PlayerInteractEvent.LeftClickBlock.Action.START)toggle(p,e.getPos(),SecretBlocks.INVISIBLE);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void right(PlayerInteractEvent.RightClickBlock e){
        if(e.getHand()!=InteractionHand.MAIN_HAND||!holding(e.getEntity()))return;e.setCanceled(true);
        if(e.getEntity() instanceof ServerPlayer p)toggle(p,e.getPos(),SecretBlocks.PASSABLE);
    }
    private static void toggle(ServerPlayer p,BlockPos pos,byte mode){
        if(!p.hasPermissions(2)){p.displayClientMessage(Component.literal("비밀의 완드는 관리자만 사용할 수 있습니다."),true);return;}
        long now=p.serverLevel().getGameTime();Long last=LAST_USE.get(p.getUUID());
        if(last!=null&&now-last<10)return;
        var level=p.serverLevel();
        if(level.getBlockState(pos).isAir())return;
        LAST_USE.put(p.getUUID(),now);
        // The two effects are exclusive: switching mode replaces the old one, repeating it restores the block.
        byte next=SecretBlocks.mode(level,pos)==mode?0:mode;
        SecretBlocks.set(level,pos,next);
        String text=next==0?"블록을 원래대로 되돌렸습니다.":next==SecretBlocks.INVISIBLE?"블록을 투명하게 만들었습니다.":"블록을 관통 가능하게 만들었습니다.";
        p.displayClientMessage(Component.literal(text+" ("+pos.getX()+", "+pos.getY()+", "+pos.getZ()+")"),true);
        level.playSound(null,pos,SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.PLAYERS,1f,next==0?.7f:1.3f);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void breaking(BlockEvent.BreakEvent e){if(holding(e.getPlayer()))e.setCanceled(true);}
    @SubscribeEvent public static void attack(AttackEntityEvent e){if(holding(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){LAST_USE.remove(e.getEntity().getUUID());}
}
