package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** One chambered round, automatically replaced 600 ticks after a shot, even while holstered. */
public final class PropAwn {
    public static final String TAG="GunGangPropAwn";
    private static final Map<UUID,Long> DEADLINES=new HashMap<>();
    public static boolean is(ItemStack stack){return stack.hasTag()&&stack.getTag().getBoolean(TAG);}
    public static boolean issued(ItemStack stack){return is(stack)||stack.hasTag()&&stack.getTag().getBoolean("GunGangPropShotgun");}
    static ItemStack create(ServerPlayer p){
        if(!net.minecraftforge.fml.ModList.get().isLoaded("tacz"))return ItemStack.EMPTY;
        try{
            var weapon=Loadouts.load().primary().stream().filter(w->w.itemNbt().contains("tacz:ai_awp")).findFirst()
                .orElseGet(()->new Loadouts.Weapon("prop_awn","{id:\"tacz:modern_kinetic_gun\",Count:1b,tag:{GunId:\"tacz:ai_awp\",GunFireMode:\"SEMI\",AttachmentSCOPE:{id:\"tacz:attachment\",Count:1b,tag:{AttachmentId:\"tacz:scope_elcan_4x\"}}}}","AWN","",null,""));
            var stack=weapon.stack();LoadoutSlots.mark(stack,"GunGangIssued",40);SecondaryRuntime.configure(p,stack,weapon);
            var tag=stack.getOrCreateTag();tag.putBoolean(TAG,true);tag.putInt("GunGangCooldownTicks",600);
            tag.putString("GunGangSecondaryId","prop_awn");tag.putInt("DummyAmmo",0);tag.putInt("MaxDummyAmmo",0);
            tag.putLong("GunGangCooldownEnd",DEADLINES.getOrDefault(p.getUUID(),0L));
            tag.putInt("GunCurrentAmmoCount",0);tag.putBoolean("HasBulletInBarrel",SecondaryRuntime.remaining(p,stack)==0);
            stack.setHoverName(net.minecraft.network.chat.Component.literal("AWN · 숨바꼭질 (1발 / 재장전 30초)"));return stack;
        }catch(java.io.IOException e){throw new IllegalStateException("AWN 설정을 읽을 수 없습니다.",e);}
    }
    public static void fired(Player p,ItemStack stack){
        if(p.level().isClientSide)return;
        SecondaryRuntime.commit(p,stack);DEADLINES.put(p.getUUID(),p.level().getGameTime()+600);
    }
    static void tick(ServerPlayer p,ItemStack stack){
        if(!is(stack))return;
        var tag=stack.getTag();boolean ready=SecondaryRuntime.remaining(p,stack)==0;
        if(tag.getInt("GunCurrentAmmoCount")!=0||tag.getBoolean("HasBulletInBarrel")!=ready){
            tag.putInt("GunCurrentAmmoCount",0);tag.putBoolean("HasBulletInBarrel",ready);p.inventoryMenu.broadcastChanges();
        }
    }
    static void clear(UUID id){DEADLINES.remove(id);}
    @SuppressWarnings("unchecked") public static void registerReload(){
        try{
            var type=(Class<net.minecraftforge.eventbus.api.Event>)Class.forName("com.tacz.guns.api.event.common.GunReloadEvent");
            var item=type.getMethod("getGunItemStack");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,false,type,event->{
                try{if(item.invoke(event) instanceof ItemStack stack&&is(stack))event.setCanceled(true);}
                catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            });
        }catch(ClassNotFoundException absent){/* Optional TaCZ. */}
        catch(ReflectiveOperationException e){throw new IllegalStateException("TaCZ reload hook unavailable",e);}
    }
    private PropAwn(){}
}
