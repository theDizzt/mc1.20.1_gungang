package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Death zone marker (gungang:death_zone), replacing the map JSON death zones. Like a barrier it is invisible and only
 * shows its skull marker to a player holding one; unlike a barrier nothing collides with it. During a match (setup
 * and play) any participating player or bot, of either team, that touches it dies (LobbyManager / BotAgent poll
 * {@link #touching}); bots treat it as deadly ground when pathing.
 */
public final class DeathZoneBlock extends Block {
    /** Client: the local player holds a death zone block; set by ClientSetup. */
    public static java.util.function.BooleanSupplier clientHolding=()->false;

    public DeathZoneBlock(){
        super(Properties.of().strength(-1,3600000).noCollission().noOcclusion().noLootTable().isValidSpawn((s,l,p,e)->false)
                .isRedstoneConductor((s,l,p)->false).isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false));
    }
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
    @Override public boolean propagatesSkylightDown(BlockState state,BlockGetter level,BlockPos pos){return true;}
    @Override public float getShadeBrightness(BlockState state,BlockGetter level,BlockPos pos){return 1;}
    /** Selectable (and breakable in creative) only while held, like a barrier; otherwise clicks and shots pass through. */
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        return context instanceof EntityCollisionContext entity&&holding(entity.getEntity())?Shapes.block():Shapes.empty();
    }
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Shapes.empty();}
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,RandomSource random){
        if(clientHolding.getAsBoolean())level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK_MARKER,state),pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,0,0,0);
    }
    public static boolean holding(Entity entity){return entity instanceof Player p&&(item(p.getMainHandItem())||item(p.getOffhandItem()));}
    private static boolean item(ItemStack stack){return stack.getItem() instanceof BlockItem item&&item.getBlock() instanceof DeathZoneBlock;}

    /** Whether the entity's body overlaps a death zone cell. */
    public static boolean touching(Entity entity){
        var box=entity.getBoundingBox().deflate(1e-3);var level=entity.level();
        for(var pos:BlockPos.betweenClosed(Mth.floor(box.minX),Mth.floor(box.minY),Mth.floor(box.minZ),Mth.floor(box.maxX),Mth.floor(box.maxY),Mth.floor(box.maxZ)))
            if(level.getBlockState(pos).getBlock() instanceof DeathZoneBlock)return true;
        return false;
    }
}
