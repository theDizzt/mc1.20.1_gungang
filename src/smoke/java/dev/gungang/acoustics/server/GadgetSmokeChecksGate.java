package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

final class GadgetSmokeChecksGate {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void run(MinecraftServer server)throws Exception{
        var level=server.overworld();var saved=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        var alpha=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"GateAlpha"));
        var bravo=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"GateBravo"));
        var victim=EntityType.COW.create(level);var projectiles=new ArrayList<Entity>();
        try{
            for(var pos:BlockPos.betweenClosed(497,239,498,504,244,504)){saved.put(pos.immutable(),level.getBlockState(pos));level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);}
            LobbyManager.station(alpha,StationBlock.Kind.ALPHA,new BlockPos(498,239,501));LobbyManager.station(bravo,StationBlock.Kind.BRAVO,new BlockPos(498,239,501));
            for(var block:List.of(StationBlocks.ALPHA_GATE.get(),StationBlocks.BRAVO_GATE.get())){
                for(var pos:BlockPos.betweenClosed(500,240,499,500,243,503))level.setBlock(pos,block.defaultBlockState(),3);
                var friend=block==StationBlocks.ALPHA_GATE.get()?alpha:bravo;var enemy=friend==alpha?bravo:alpha;
                friend.setPos(498.5,240,501.5);friend.move(MoverType.SELF,new Vec3(4,0,0));check(friend.getX()>502,"Own team crosses gate");
                enemy.setPos(498.5,240,501.5);enemy.move(MoverType.SELF,new Vec3(4,0,0));check(enemy.getX()<500,"Enemy is blocked by gate");
                friend.move(MoverType.SELF,new Vec3(-4,0,0));check(friend.getX()<499,"Own team can leave in either direction");
                var state=block.defaultBlockState();check(!state.getCollisionShape(level,new BlockPos(500,240,501)).isEmpty(),"Teamless collision and ray contexts remain solid");
                for(var shooter:List.of(alpha,bravo)){
                    var arrow=new Arrow(level,shooter);projectiles.add(arrow);arrow.setPos(498.5,241.5,501.5);arrow.setDeltaMovement(6,0,0);level.addFreshEntity(arrow);arrow.tick();
                    check(arrow.isRemoved(),"Gate absorbs arrows from both teams");
                }
            }
            alpha.setPos(498.5,240,501.5);victim.setPos(502.5,240,501.5);level.addFreshEntity(victim);
            float hp=victim.getHealth();check(!victim.hurt(level.damageSources().playerAttack(alpha),4)&&victim.getHealth()==hp,"Melee/hitscan damage cannot cross gate");
            check(!victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,100),alpha),"Harmful potion effects cannot cross gate");
            var direct=new Arrow(level,alpha);projectiles.add(direct);direct.setPos(502.5,241,501.5);direct.xOld=498.5;direct.yOld=241;direct.zOld=501.5;
            check(!victim.hurt(level.damageSources().arrow(direct,alpha),4)&&victim.getHealth()==hp,"Already-penetrated projectile damage is rejected");
            victim.setDeltaMovement(Vec3.ZERO);level.explode(null,498.5,241,501.5,4,false,net.minecraft.world.level.Level.ExplosionInteraction.NONE);
            check(victim.getHealth()==hp&&victim.getDeltaMovement().lengthSqr()==0,"Explosion damage and knockback cannot cross gate");
            check(level.getBlockState(new BlockPos(500,240,501)).getBlock() instanceof SpawnGateBlock,"Explosion leaves gate intact");
            alpha.setPos(502,240,501.5);check(victim.hurt(level.damageSources().playerAttack(alpha),4),"Gate does not grant immunity to attacks on the same side");
            tacz(level,alpha);
        }finally{
            victim.discard();projectiles.forEach(Entity::discard);saved.forEach((p,s)->level.setBlock(p,s,3));
            var field=LobbyManager.class.getDeclaredField("PLAYERS");field.setAccessible(true);var players=(Map<?,?>)field.get(null);players.remove(alpha.getUUID());players.remove(bravo.getUUID());
            server.getScoreboard().removePlayerFromTeam(alpha.getScoreboardName());server.getScoreboard().removePlayerFromTeam(bravo.getScoreboardName());
        }
        System.out.println("GUNGANG_GATE_SMOKE_PASS ally/enemy collision, both-team projectiles, explosion/knockback, potion, damage boundaries");
    }
    private static void tacz(net.minecraft.server.level.ServerLevel level,net.minecraft.server.level.ServerPlayer owner)throws Exception{
        if(!net.minecraftforge.fml.ModList.get().isLoaded("tacz"))return;
        var type=Class.forName("com.tacz.guns.entity.EntityKineticBullet");var bullet=(net.minecraft.world.entity.projectile.Projectile)type.getConstructor(EntityType.class,net.minecraft.world.level.Level.class).newInstance(type.getField("TYPE").get(null),level);
        bullet.setOwner(owner);bullet.setPos(502.5,241,501.5);bullet.xOld=498.5;bullet.yOld=241;bullet.zOld=501.5;
        var start=type.getDeclaredField("startPos");start.setAccessible(true);start.set(bullet,new Vec3(498.5,241,501.5));
        var pair=Class.forName("com.tacz.guns.resource.pojo.data.gun.ExtraDamage$DistanceDamagePair").getConstructor(float.class,float.class).newInstance(100f,9f);
        var values=new LinkedList<>();values.add(pair);var damage=type.getDeclaredField("damageAmount");damage.setAccessible(true);damage.set(bullet,values);
        var distance=type.getDeclaredField("distanceAmount");distance.setAccessible(true);distance.setFloat(bullet,100);
        float actual=((Number)type.getMethod("getDamage",Vec3.class).invoke(bullet,new Vec3(502.5,241,501.5))).floatValue();
        check(actual==0&&bullet.isRemoved(),"Actual TaCZ penetration damage is zero and bullet removed");
        System.out.println("GUNGANG_GATE_TACZ_PASS");
    }
}
