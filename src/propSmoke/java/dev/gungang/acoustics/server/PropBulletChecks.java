package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Exercise the real TaCZ ray tracer and installed mixin. */
final class PropBulletChecks {
    static void run(PropHuntMatch match,ServerPlayer hider,ServerPlayer seeker)throws Exception {
        var level=seeker.serverLevel();var pos=new BlockPos(60,120,60);var wall=pos.east(2);
        var old=level.getBlockState(pos);var oldWall=level.getBlockState(wall);
        var type=Class.forName("com.tacz.guns.entity.EntityKineticBullet");
        var bullet=(Projectile)type.getConstructor(net.minecraft.world.entity.EntityType.class,net.minecraft.world.level.Level.class)
                .newInstance(type.getField("TYPE").get(null),level);
        var ray=Class.forName("com.tacz.guns.util.block.BlockRayTrace").getMethod("rayTraceBlocks",net.minecraft.world.level.Level.class,ClipContext.class);
        var matchField=MatchManager.class.getDeclaredField("propHunt");matchField.setAccessible(true);
        var from=new Vec3(59.5,120.25,60.5);var to=new Vec3(63.5,120.25,60.5);
        var down=new Vec3(60.5,119.5,60.5);var up=new Vec3(60.5,121.5,60.5);
        try {
            bullet.setOwner(seeker);
            level.setBlock(pos,Blocks.IRON_TRAPDOOR.defaultBlockState(),3);
            level.setBlock(wall,Blocks.STONE.defaultBlockState(),3);
            check(new ClipContext(down,up,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,bullet).getBlockShape(level.getBlockState(pos),level,pos).isEmpty(),"Seeker bullet ignores thin trapdoor");
            level.setBlock(pos,Blocks.OAK_FENCE.defaultBlockState(),3);
            var hit=(BlockHitResult)ray.invoke(null,level,new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,bullet));
            check(hit.getBlockPos().equals(wall),"Thin fence passes but solid wall behind still stops TaCZ bullet");
            level.setBlock(pos,Blocks.STONE_SLAB.defaultBlockState(),3);
            hit=(BlockHitResult)ray.invoke(null,level,new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,bullet));
            check(hit.getBlockPos().equals(pos),"Exactly half-block slab stops TaCZ bullet");
            level.setBlock(pos,Blocks.IRON_TRAPDOOR.defaultBlockState(),3);
            check(!new ClipContext(down,up,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,seeker).getBlockShape(level.getBlockState(pos),level,pos).isEmpty(),"Player collision/visibility rays remain unchanged");
            bullet.setOwner(hider);
            check(!new ClipContext(down,up,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,bullet).getBlockShape(level.getBlockState(pos),level,pos).isEmpty(),"Hider bullet does not penetrate");
            bullet.setOwner(seeker);matchField.set(null,null);
            check(!new ClipContext(down,up,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,bullet).getBlockShape(level.getBlockState(pos),level,pos).isEmpty(),"Outside prop hunt issued gun cannot penetrate");
            System.out.println("PROP_THIN_BLOCK_BULLET_PASS");
        } finally {matchField.set(null,match);level.setBlock(pos,old,3);level.setBlock(wall,oldWall,3);bullet.discard();}
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
