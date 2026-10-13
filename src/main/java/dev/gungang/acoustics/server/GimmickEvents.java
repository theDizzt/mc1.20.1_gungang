package dev.gungang.acoustics.server;
import dev.gungang.acoustics.GungangAcoustics;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class GimmickEvents {
    @SubscribeEvent public static void punch(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock e){
        var p=e.getEntity();if(e.getLevel().isClientSide||p.isCreative()||p.isSpectator()||CombatGadgets.holdingShield(p)||SurvivalGadgets.jetting(p)||SafeZones.contains(p))return;
        if(GimmickEntity.at(e.getLevel(),e.getPos())==null)return;
        var itemId=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(p.getMainHandItem().getItem());
        if(itemId!=null&&itemId.getNamespace().equals("tacz"))return;
        long now=e.getLevel().getGameTime();var tag=p.getPersistentData();
        if(tag.contains("GunGangGimmickAttack")&&now-tag.getLong("GunGangGimmickAttack")<10)return;
        tag.putLong("GunGangGimmickAttack",now);
        GimmickEntity.hit(e.getLevel(),e.getPos(),(float)(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)*AdrenalineDamage.multiplier(p)));
    }
    @SubscribeEvent public static void breaking(net.minecraftforge.event.level.BlockEvent.BreakEvent e){if(e.getState().getBlock() instanceof GimmickBlock&&!e.getPlayer().isCreative())e.setCanceled(true);}
    @SuppressWarnings("unchecked") public static void registerTacz(){try{
        var type=(Class<net.minecraftforge.eventbus.api.Event>)Class.forName("com.tacz.guns.api.event.server.AmmoHitBlockEvent");
        var level=type.getMethod("getLevel");var hit=type.getMethod("getHitResult");var ammo=type.getMethod("getAmmo");
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,false,type,e->{try{
            var world=(net.minecraft.world.level.Level)level.invoke(e);var result=(net.minecraft.world.phys.BlockHitResult)hit.invoke(e);
            if(world.isClientSide||GimmickEntity.at(world,result.getBlockPos())==null)return;
            var bullet=ammo.invoke(e);float damage=((Number)bullet.getClass().getMethod("getDamage",net.minecraft.world.phys.Vec3.class).invoke(bullet,result.getLocation())).floatValue();
            GimmickEntity.hit(world,result.getBlockPos(),damage);
        }catch(ReflectiveOperationException ex){com.mojang.logging.LogUtils.getLogger().error("GunGang gimmick bullet hit",ex);}});
    }catch(ClassNotFoundException ignored){}catch(ReflectiveOperationException e){com.mojang.logging.LogUtils.getLogger().warn("TaCZ block hit API unavailable",e);}}
    private GimmickEvents(){}
}
