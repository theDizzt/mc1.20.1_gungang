package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Optional Tactical Military Bow integration; no hard dependency on the bow mod. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class CompoundBow {
    private static final ResourceLocation BOW=new ResourceLocation("tacticalmilitarybow","tactical_military_bow");
    private static final ResourceLocation AMMO=new ResourceLocation("tacticalmilitarybow","normal_arrow");
    private static final String ISSUED="GunGangCompoundAmmo",EPOCH="GunGangBowEpoch";
    public static final int AMMO_SLOT=7; // Human hotbar slot 8.
    /** Arrows stuck in a block vanish after 15 s instead of the bow mod's 5 minutes, so they cannot pile up. */
    public static final int WALL_ARROW_TICKS=15*20;
    public interface ArrowAccess {
        float gungang$shotDamage();
        void gungang$embed(BlockHitResult hit);
    }
    public static boolean isBow(ItemStack stack){return !stack.isEmpty()&&BOW.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));}
    public static boolean issuedAmmo(ItemStack stack){return stack.hasTag()&&stack.getTag().getBoolean(ISSUED);}
    public static float damage(boolean aimed,float charge){return (aimed?42:32)*Math.max(0,Math.min(1,charge));}
    public static boolean infiniteAmmo(ServerPlayer player){return isBow(player.getMainHandItem())&&issuedAmmo(player.getInventory().getItem(AMMO_SLOT));}
    public static void syncAmmo(ServerPlayer player){
        boolean holding=player.isAlive()&&!player.isSpectator()&&isBow(player.getMainHandItem());
        var inventory=player.getInventory();boolean changed=false;
        for(int i=0;i<inventory.getContainerSize();i++)if(issuedAmmo(inventory.getItem(i))&&(!holding||i!=AMMO_SLOT)){
            inventory.setItem(i,ItemStack.EMPTY);changed=true;
        }
        if(holding){
            var current=inventory.getItem(AMMO_SLOT);
            if(!issuedAmmo(current)){
                var item=ForgeRegistries.ITEMS.getValue(AMMO);
                if(item!=null&&item!=net.minecraft.world.item.Items.AIR){
                    var arrow=new ItemStack(item);arrow.getOrCreateTag().putBoolean(ISSUED,true);
                    inventory.setItem(AMMO_SLOT,arrow);
                    LoadoutSlots.preserve(player,current);changed=true;
                }
            }else if(current.getCount()!=1){current.setCount(1);changed=true;}
        }
        if(changed){inventory.setChanged();player.inventoryMenu.broadcastChanges();}
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase==TickEvent.Phase.END&&event.player instanceof ServerPlayer player)syncAmmo(player);
    }
    @SubscribeEvent public static void toss(ItemTossEvent event){if(issuedAmmo(event.getEntity().getItem()))event.getEntity().discard();}

    /** Each dimension retains its reset generation so unloaded embedded arrows cannot return later. */
    private static final class Epoch extends SavedData {
        long value;
        static Epoch get(ServerLevel level){return level.getDataStorage().computeIfAbsent(tag->{var e=new Epoch();e.value=tag.getLong("Generation");return e;},Epoch::new,"gungang_bow_arrows");}
        @Override public CompoundTag save(CompoundTag tag){tag.putLong("Generation",value);return tag;}
    }
    /** Bow arrows are issued per weapon, never loot: any arrow item the bow mod drops into the world is dropped. */
    static boolean arrowItem(ItemStack stack){
        var key=ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key!=null&&key.getNamespace().equals(AMMO.getNamespace())&&key.getPath().endsWith("_arrow");
    }
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event){
        if(event.getLevel() instanceof ServerLevel&&event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item&&arrowItem(item.getItem())){event.setCanceled(true);return;}
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getEntity() instanceof ArrowAccess))return;
        var data=event.getEntity().getPersistentData();long generation=Epoch.get(level).value;
        if(event.loadedFromDisk()&&data.getLong(EPOCH)!=generation){event.setCanceled(true);return;}
        data.putLong(EPOCH,generation);
    }
    public static int clearArrows(MinecraftServer server){
        if(server==null)return 0;int count=0;
        for(var level:server.getAllLevels()){
            var epoch=Epoch.get(level);epoch.value++;epoch.setDirty();
            var arrows=new java.util.ArrayList<Entity>();
            for(var entity:level.getAllEntities())if(entity instanceof ArrowAccess)arrows.add(entity);
            for(var arrow:arrows){arrow.discard();count++;}
        }
        return count;
    }
    private CompoundBow(){}
}
