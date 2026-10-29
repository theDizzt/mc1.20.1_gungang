package dev.gungang.acoustics.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

final class RecoveryImpulseChecks {
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    static void run(MinecraftServer server,ServerPlayer player)throws Exception{
        var counter=MinecraftServer.class.getDeclaredField("tickCount");counter.setAccessible(true);
        int start=server.getTickCount();var difficulty=server.getWorldData().getDifficulty();var oldConnection=player.connection;
        double max=player.getMaxHealth();boolean gravity=player.isNoGravity();var pos=player.position();
        var resistance=player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);double oldResistance=resistance.getBaseValue();
        var packets=new java.util.ArrayList<net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket>();
        try{
            player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);player.setNoGravity(true);
            for(var mode:java.util.List.of(net.minecraft.world.Difficulty.PEACEFUL,net.minecraft.world.Difficulty.NORMAL)){
                server.setDifficulty(mode,true);player.setHealth(50);player.getFoodData().setFoodLevel(20);player.getFoodData().setSaturation(20);
                counter.setInt(server,start);
                HealthRegeneration.damage(new net.minecraftforge.event.entity.living.LivingHurtEvent(player,player.damageSources().generic(),1));
                for(int tick=0;tick<=160;tick++){
                    counter.setInt(server,start+tick);player.tickCount=tick;player.setPos(300,200,300);player.setDeltaMovement(Vec3.ZERO);
                    player.aiStep();player.getFoodData().tick(player);
                    net.minecraft.world.effect.MobEffects.REGENERATION.applyEffectTick(player,4);
                    var event=new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player);HealthRegeneration.tick(event);HealthRegeneration.tick(event);
                    int expected=50+(tick<100?0:1+(tick-100)/20);
                    check(player.getHealth()==expected,"Single recovery clock difficulty="+mode+" tick="+tick+" health="+player.getHealth());
                }
                player.heal(5);check(player.getHealth()==59,"Explicit healing remains available");
                player.setHealth(58);HealthRegeneration.damage(new net.minecraftforge.event.entity.living.LivingHurtEvent(player,player.damageSources().generic(),1));
                counter.setInt(server,start+259);HealthRegeneration.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));check(player.getHealth()==58,"New damage delays recovery again");
                counter.setInt(server,start+260);HealthRegeneration.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));check(player.getHealth()==59,"New damage recovers at exactly five seconds");
            }
            player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player){
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet){if(packet instanceof net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket motion)packets.add(motion);}
            };
            player.setOnGround(true);player.setDeltaMovement(Vec3.ZERO);resistance.setBaseValue(0);player.knockback(.4,1,0);
            check(player.getDeltaMovement().x<0&&player.getDeltaMovement().y>0&&packets.size()==1,"Accepted knockback reaches player's own connection");
            check(packets.get(0).getXa()<0&&packets.get(0).getYa()>0,"Packet carries applied horizontal and vertical impulse");
            player.setDeltaMovement(Vec3.ZERO);resistance.setBaseValue(1);player.knockback(.4,1,0);
            check(player.getDeltaMovement().equals(Vec3.ZERO)&&packets.size()==1,"Full knockback resistance is respected");
            PlayerImpulse.apply(player,new Vec3(1.4,.5,0));check(packets.size()==2&&packets.get(1).getXa()>0,"Skill impulse sends its own movement packet");
            System.out.println("GUNGANG_RECOVERY_IMPULSE_PASS peaceful=pass normal=pass food=blocked potion=blocked delay=100 interval=20 explicitHealing=pass knockbackPacket=pass resistance=pass");
        }finally{
            player.connection=oldConnection;resistance.setBaseValue(oldResistance);server.setDifficulty(difficulty,true);counter.setInt(server,start);
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);player.setHealth((float)max);player.setNoGravity(gravity);player.setPos(pos);player.setDeltaMovement(Vec3.ZERO);
        }
    }
}
