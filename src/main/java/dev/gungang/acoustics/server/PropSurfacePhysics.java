package dev.gungang.acoustics.server;
import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Virtual block surfaces use the same physics on the predicting client and the authoritative mob server. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class PropSurfacePhysics {
    static PropEntity support(LivingEntity e){
        return e.level().getEntitiesOfClass(PropEntity.class,e.getBoundingBox().inflate(.05,.2,.05),p->!p.marker()&&!e.getUUID().equals(p.owner())&&Math.abs(e.getY()-p.getBoundingBox().maxY)<.15).stream().findFirst().orElse(null);
    }
    public static double jump(LivingEntity e,double fallback){
        var pos=net.minecraft.core.BlockPos.containing(e.getX(),e.getY()-.04,e.getZ());
        JumpPadBlock pad=e.level().getBlockState(pos).getBlock() instanceof JumpPadBlock block&&Math.abs(e.getY()-(pos.getY()+.125))<.08?block:null;
        if(pad==null){var prop=support(e);if(prop!=null&&prop.state().getBlock() instanceof JumpPadBlock block)pad=block;}
        if(pad==null)return fallback;
        e.fallDistance=0;e.getPersistentData().putBoolean("GunGangPadJump",true);return MotionRules.jumpVelocity(pad.height);
    }
    @SubscribeEvent public static void jumped(LivingEvent.LivingJumpEvent event){var e=event.getEntity();double velocity=jump(e,e.getDeltaMovement().y);if(velocity!=e.getDeltaMovement().y){e.setDeltaMovement(e.getDeltaMovement().x,velocity,e.getDeltaMovement().z);e.getPersistentData().putBoolean("GunGangPadJump",true);}}
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event){
        var e=event.getEntity();if(e instanceof PropEntity||!e.onGround()||e.isSpectator())return;
        if(e.level().isClientSide?!(e instanceof Player p&&p.isLocalPlayer()):e instanceof Player)return;
        var prop=support(e);if(prop==null)return;
        var s=prop.state();var v=e.getDeltaMovement();
        if(s.is(Blocks.ICE)||s.is(Blocks.PACKED_ICE)){var pos=net.minecraft.core.BlockPos.containing(e.getX(),e.getY()-.500001,e.getZ());double friction=e.level().getBlockState(pos).getFriction(e.level(),pos,e);e.setDeltaMovement(v.x*.98/friction,v.y,v.z*.98/friction);}
        if(s.getBlock() instanceof ConveyorBlock){var d=ConveyorBlock.output(s);e.move(MoverType.SELF,new Vec3(d.getStepX()*.1,0,d.getStepZ()*.1));e.setOnGround(true);}
    }
    private PropSurfacePhysics(){}
}
