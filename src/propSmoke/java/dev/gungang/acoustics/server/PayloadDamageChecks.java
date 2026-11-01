package dev.gungang.acoustics.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;

final class PayloadDamageChecks {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void run(ServerPlayer player)throws Exception{
        var level=player.serverLevel();var point=player.position();
        var cart=EscortBlocks.CART.get().create(level);cart.setPos(point);level.addFreshEntity(cart);
        var stand=EntityType.ARMOR_STAND.create(level);stand.setPos(point);level.addFreshEntity(stand);
        var summon=PropSummonEntity.TYPE.get().create(level);summon.setPos(point);level.addFreshEntity(summon);
        var strike=GadgetItems.ORDNANCE.get().create(level);strike.setPos(point);strike.setup(player,true);level.addFreshEntity(strike);
        try{
            for(var source:java.util.List.of(level.damageSources().inFire(),level.damageSources().onFire(),level.damageSources().lava(),level.damageSources().freeze(),level.damageSources().lightningBolt(),level.damageSources().magic(),level.damageSources().genericKill())){
                check(cart.isInvulnerableTo(source)&&!cart.hurt(source,10000),"Payload rejects elemental and bypass damage");
            }
            cart.setSecondsOnFire(10);cart.setRemainingFireTicks(200);cart.setTicksFrozen(200);
            cart.thunderHit(level,EntityType.LIGHTNING_BOLT.create(level));
            check(!cart.isOnFire()&&cart.getRemainingFireTicks()==0&&cart.getTicksFrozen()==0&&!cart.isRemoved(),"Payload rejects direct fire, freeze and lightning state");
            check(cart.isPickable()&&cart.ignoreExplosion(),"Payload still intercepts projectiles without explosion damage");
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(point);player.removeAllEffects();player.invulnerableTime=0;
            player.setInvulnerable(false);player.getAbilities().invulnerable=false;player.setAbsorptionAmount(0);
            var immunity=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");immunity.setAccessible(true);immunity.setInt(player,0);
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);player.setHealth(1000);
            float standHealth=stand.getHealth(),summonHealth=summon.getHealth();
            var explode=OrdnanceEntity.class.getDeclaredMethod("explode",net.minecraft.server.level.ServerLevel.class);explode.setAccessible(true);explode.invoke(strike,level);
            check(player.getHealth()<1000,"Orbital strike still damages players");
            check(stand.getHealth()==standHealth&&summon.getHealth()==summonHealth&&!stand.isRemoved()&&!summon.isRemoved()&&!cart.isRemoved(),"Orbital strike leaves non-player entities untouched");
            System.out.println("GUNGANG_PAYLOAD_DAMAGE_PASS elemental_immunity direct_states player_only_orbital");
        }finally{strike.discard();cart.discard();stand.discard();summon.discard();}
    }
}
