package dev.gungang.acoustics.server;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Thin scenery is penetrable only by a prop-hunt seeker's issued gun. Disguise entities stay hittable. */
public final class PropBullet {
    public static java.util.function.Predicate<Entity> clientSeeker=entity->false;

    public static boolean penetrates(Entity entity){
        if(!(entity instanceof Projectile bullet)||!entity.getClass().getName().equals("com.tacz.guns.entity.EntityKineticBullet"))return false;
        if(!(bullet.getOwner() instanceof LivingEntity shooter))return false;
        if(!PropAwn.issued(shooter.getMainHandItem())&&!PropAwn.issued(shooter.getOffhandItem()))return false;
        if(entity.level().isClientSide)return clientSeeker.test(shooter);
        var match=MatchManager.realPropHunt();
        return match!=null&&match.game.phase()==PropHuntGame.Phase.HUNTING
                &&match.participant(shooter.getUUID())&&!match.hider(shooter.getUUID());
    }

    /**
     * Retain thick pieces; thin rails/panes do not shield a thick post or another wall behind them.
     * Asked for every block along every seeker pellet, so a shape with no thin piece (most blocks) is returned without
     * building anything, and the rebuilt shapes are remembered: block shapes are shared per block state.
     */
    public static VoxelShape blockingShape(VoxelShape shape){
        if(shape.isEmpty()||shape==FULL)return shape;
        synchronized(BLOCKING){var known=BLOCKING.get(shape);if(known!=null)return known;}
        var pieces=shape.toAabbs();boolean thin=false;
        for(var box:pieces)if(Math.min(box.getXsize(),Math.min(box.getYsize(),box.getZsize()))<.5){thin=true;break;}
        VoxelShape result=shape;
        if(thin){
            result=Shapes.empty();
            for(var box:pieces)if(Math.min(box.getXsize(),Math.min(box.getYsize(),box.getZsize()))>=.5)result=Shapes.or(result,Shapes.create(box));
        }
        synchronized(BLOCKING){BLOCKING.put(shape,result);}
        return result;
    }
    private static final VoxelShape FULL=Shapes.block();
    /** Shape → its bullet-blocking part; weak keys, so shapes made on the fly (moved, combined) are not kept. */
    private static final java.util.Map<VoxelShape,VoxelShape> BLOCKING=new java.util.WeakHashMap<>();
    private PropBullet(){}
}
