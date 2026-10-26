package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class Las5SmokeChecks {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void field(Object obj,String name,Object value)throws Exception{
        var f=obj.getClass().getDeclaredField(name);f.setAccessible(true);f.set(obj,value);
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){
        var server=event.getServer();var level=server.overworld();
        try {
            var player=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"Las5Tester"));
            player.setPos(0,240,0);level.addNewPlayer(player);
            var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz","modern_kinetic_gun"));
            check(item!=null,"TaCZ gun missing");
            var stack=new ItemStack(item);stack.getOrCreateTag().putString("GunId","zeta:las_5");stack.getTag().putFloat("HeatAmount",59);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
            Class<?> type=Class.forName("com.tacz.guns.entity.EntityKineticBullet");
            var bullet=(Projectile)type.getConstructor(EntityType.class,Level.class).newInstance(type.getField("TYPE").get(null),level);
            bullet.setOwner(player);bullet.setPos(0,240,0);field(bullet,"gunId",new ResourceLocation("zeta","las_5"));field(bullet,"startPos",bullet.position());
            check(bullet instanceof Las5Damage.BulletAccess,"TaCZ mixin not applied");
            check(level.addFreshEntity(bullet),"Bullet spawn failed");
            check(Math.abs(bullet.getPersistentData().getFloat(Las5Damage.SHOT_DAMAGE)-3.18f)<.0001,"Heat not captured at spawn");
            stack.getTag().putFloat("HeatAmount",0);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            float damage=((Number)type.getMethod("getDamage",Vec3.class).invoke(bullet,bullet.position().add(1,0,0))).floatValue();
            check(Math.abs(damage-3.18f)<.0001,"Damage changed after cooling/weapon swap");
            check(Math.abs(((Number)type.getMethod("getDamage",Vec3.class).invoke(bullet,bullet.position())).floatValue()-3.18f)<.0001,"Repeated damage query multiplied twice");
            bullet.discard();
            var other=(Projectile)type.getConstructor(EntityType.class,Level.class).newInstance(type.getField("TYPE").get(null),level);
            other.setOwner(player);field(other,"gunId",new ResourceLocation("tacz","ak47"));other.setPos(0,240,0);level.addFreshEntity(other);
            check(!other.getPersistentData().contains(Las5Damage.SHOT_DAMAGE),"Other gun modified");other.discard();
            System.out.println("GUNGANG_LAS5_PASS tacz_mixin heat59 damage3.18 snapshot repeat other_gun");
        }catch(Throwable error){error.printStackTrace();System.out.println("GUNGANG_LAS5_FAIL");}
        finally{server.halt(false);}
    }
}
