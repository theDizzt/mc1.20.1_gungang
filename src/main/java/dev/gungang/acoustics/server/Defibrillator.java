package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Secondary 78 (제세동기, formerly 자비의 총알): hold use (or the off-hand use macro key) to lock onto the teammate nearest
 * the crosshair within 32 blocks and in sight (marked with a green "[+]" on the user's screen); letting go gives them up
 * to 30 bonus (absorption) health, never above maximum health + 30, then the 30 s cooldown starts. Nothing else restores
 * the bonus. It lasts 15 s (whatever is left then disappears) and never outlives the game.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class Defibrillator {
    public static final String ID="78";
    static final float BONUS=30;
    public static final double RANGE=32;
    /** Lock-on cone around the crosshair (degrees); the server accepts a client's pick a little wider for lag. */
    public static final double AIM_ANGLE=40,SERVER_ANGLE=60;
    static final int SHIELD_TICKS=15*20;
    /** Who carries a bonus, until when (game time). */
    private record Shield(LivingEntity target,long end){}
    private static final java.util.Map<java.util.UUID,Shield> SHIELDS=new java.util.HashMap<>();
    /** The target each holding player's client has marked (entity id). */
    private static final java.util.Map<java.util.UUID,Integer> CHOSEN=new java.util.HashMap<>();
    public static boolean is(ItemStack stack){return stack.getItem() instanceof DefibrillatorItem;}
    /** A living, non-spectating player or bot on the user's scoreboard team; never the user. */
    public static boolean ally(Entity user,Entity target){
        return user instanceof Player a&&target!=a&&BotEntity.participant(target)&&a.getTeam()!=null&&a.isAlliedTo(target);
    }
    /** Degrees between the crosshair and the target's body centre. */
    private static double angle(Player p,LivingEntity target){
        var to=target.getBoundingBox().getCenter().subtract(p.getEyePosition());double length=to.length();
        return length<1e-6?0:Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,p.getLookAngle().dot(to)/length))));
    }
    /** In range, inside the cone and not behind a wall (body centre or head). */
    public static boolean reachable(Player p,LivingEntity target,double maxAngle){
        if(!ally(p,target)||target.level()!=p.level())return false;
        var eye=p.getEyePosition();
        if(target.getBoundingBox().getCenter().distanceTo(eye)>RANGE||angle(p,target)>maxAngle)return false;
        for(var point:new Vec3[]{target.getBoundingBox().getCenter(),target.getEyePosition()})
            if(p.level().clip(new ClipContext(eye,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS)return true;
        return false;
    }
    /** The teammate nearest the crosshair that can be locked onto; null when none. Shared by the client marker and the server. */
    public static LivingEntity target(Player p,double maxAngle){
        LivingEntity best=null;double bestAngle=Double.MAX_VALUE;
        for(var other:p.level().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(RANGE),e->ally(p,e))){
            if(!reachable(p,other,maxAngle))continue;
            double a=angle(p,other);if(a<bestAngle){bestAngle=a;best=other;}
        }
        return best;
    }
    /** The client reports the target it is showing, so the release applies to the marked player. */
    static void choose(ServerPlayer p,String value){
        try{if(value.isEmpty())CHOSEN.remove(p.getUUID());else CHOSEN.put(p.getUUID(),Integer.parseInt(value));}
        catch(NumberFormatException ignored){CHOSEN.remove(p.getUUID());}
    }
    /** On release: the client's marked teammate if still valid, else the server's own pick. */
    static LivingEntity release(Player p){
        Integer id=CHOSEN.remove(p.getUUID());
        if(id!=null&&p.level().getEntity(id) instanceof LivingEntity chosen&&reachable(p,chosen,SERVER_ANGLE))return chosen;
        return target(p,AIM_ANGLE);
    }
    static void grant(LivingEntity user,LivingEntity target){
        float before=target.getAbsorptionAmount();
        float bonus=Math.max(before,Math.min(BONUS,target.getMaxHealth()+BONUS-target.getHealth()));
        if(bonus>before)target.setAbsorptionAmount(bonus);
        SHIELDS.put(target.getUUID(),new Shield(target,target.level().getGameTime()+SHIELD_TICKS));
        var level=(net.minecraft.server.level.ServerLevel)user.level();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,target.getX(),target.getY()+target.getBbHeight()+.3,target.getZ(),5,.3,.2,.3,0);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,target.getX(),target.getY()+target.getBbHeight()/2,target.getZ(),16,.3,.4,.3,.15);
        level.playSound(null,target.blockPosition(),net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,net.minecraft.sounds.SoundSource.PLAYERS,1.2f,1.4f);
        level.playSound(null,user.blockPosition(),net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT,net.minecraft.sounds.SoundSource.PLAYERS,.7f,1.8f);
        HealCue.received(target);
        if(target instanceof Player player)player.displayClientMessage(Component.literal(ProgressionService.name(user)+"의 제세동기 · 추가 체력 "+Math.round(bonus)),true);
        if(user instanceof ServerPlayer player)player.displayClientMessage(Component.literal(ProgressionService.name(target)+"에게 추가 체력 "+Math.round(bonus)),true);
    }
    /** Expired bonuses vanish; a running life support (which also uses absorption) keeps its own. */
    @SubscribeEvent public static void expire(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||SHIELDS.isEmpty())return;
        SHIELDS.values().removeIf(s->{
            var t=s.target();if(t.isRemoved()||!t.isAlive())return true;
            if(t.level().getGameTime()<s.end())return false;
            if(!SurvivalGadgets.lifeSupport(t))t.setAbsorptionAmount(0);
            return true;
        });
    }
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){CHOSEN.remove(event.getEntity().getUUID());}
    /** The game ended: every bonus goes with it. */
    static void reset(){
        for(var s:SHIELDS.values())if(!s.target().isRemoved()&&!SurvivalGadgets.lifeSupport(s.target()))s.target().setAbsorptionAmount(0);
        SHIELDS.clear();CHOSEN.clear();
    }
    private Defibrillator(){}
}
