package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.PropEntity;
import dev.gungang.acoustics.server.PropGeometry;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Disguises are entities, so the world never ticks them like blocks. This plays their block effects on the client:
 * animateTick particles (torch/candle flames, lava drips), client block-entity tickers (campfire smoke) and light.
 * Light is a client-only invisible light block in the prop's air cell, restored when the prop moves or disappears,
 * so nothing is written to the server world.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class PropEffects {
    private record Glow(BlockPos pos,BlockState original){}
    private static final Map<Integer,Glow> GLOWS=new HashMap<>();
    private static final Map<Integer,Map<BlockPos,BlockEntity>> TICKING=new HashMap<>();
    /** Open/close progress (previous tick, this tick) of shutter and shulker box disguises, 0 = closed. */
    private static final Map<Integer,float[]> MOTION=new HashMap<>();
    private static final Map<String,net.minecraft.world.item.ItemStack> HOLOGRAMS=new HashMap<>();
    /** Last ring count (MODE) seen per bell disguise; a change swings the bell. */
    private static final Map<Integer,Integer> RINGS=new HashMap<>();
    private static ClientLevel last;
    /** Calls per tick of vanilla's animateTick sampling for each of its two radii (ClientLevel.animateTick). */
    private static final int ANIMATE_SAMPLES=667;
    /**
     * How often vanilla would run animateTick on a block at this offset from the player per tick. Vanilla picks offsets
     * as nextInt(r)-nextInt(r) per axis for r=16 and r=32, so blocks near the player animate far more often (about
     * every 6 ticks next to them) than a flat rate; a flat 1/44 left nearby torches, candles and portals almost bare.
     */
    static double animateRate(int dx,int dy,int dz){return ANIMATE_SAMPLES*(axis(dx,16)*axis(dy,16)*axis(dz,16)+axis(dx,32)*axis(dy,32)*axis(dz,32));}
    private static double axis(int d,int r){return Math.max(0,r-Math.abs(d))/(double)(r*r);}
    /**
     * The model is drawn centred on the prop, not on its block cell; block code spawns particles from the cell corner.
     * While a prop's block code runs, PropParticleMixin shifts every new particle by this amount.
     */
    private static net.minecraft.world.phys.Vec3 particleOffset=net.minecraft.world.phys.Vec3.ZERO;
    public static net.minecraft.world.phys.Vec3 particleOffset(){return particleOffset;}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();var level=mc.level;
        if(level!=last){GLOWS.clear();TICKING.clear();MOTION.clear();RINGS.clear();last=level;}
        if(level==null||mc.isPaused())return;
        var seen=new HashSet<Integer>();
        for(var entity:level.entitiesForRendering()){
            if(!(entity instanceof PropEntity prop)||prop.isRemoved())continue;
            seen.add(prop.getId());
            float speed=prop.item().equals("gungang:shutter")?(float)dev.gungang.acoustics.server.ShutterMotion.SPEED:prop.item().endsWith("shulker_box")?.1f:0;
            if(speed>0){var m=MOTION.computeIfAbsent(prop.getId(),id->{float v=prop.open()?1:0;return new float[]{v,v};});m[0]=m[1];m[1]=net.minecraft.util.Mth.clamp(m[1]+(prop.open()?speed:-speed),0,1);}
            int emission=prop.item().equals("minecraft:end_crystal")?15:0;
            particleOffset=new net.minecraft.world.phys.Vec3(prop.getX()-.5-prop.blockPosition().getX(),prop.getY()-prop.blockPosition().getY(),prop.getZ()-.5-prop.blockPosition().getZ());
            var eye=mc.player==null?prop.blockPosition():mc.player.blockPosition();
            try{
                for(var part:PropGeometry.parts(prop)){
                    var state=part.state();var pos=prop.blockPosition().offset(part.offset());
                    emission=Math.max(emission,state.getLightEmission());
                    // Same expected number of animateTick calls as the real block at this distance (several when very close).
                    double rate=animateRate(pos.getX()-eye.getX(),pos.getY()-eye.getY(),pos.getZ()-eye.getZ());
                    for(int calls=(int)rate+(level.random.nextDouble()<rate-(int)rate?1:0);calls>0;calls--){state.getBlock().animateTick(state,level,pos,level.random);if(!state.getFluidState().isEmpty()&&!(state.getBlock() instanceof net.minecraft.world.level.block.BubbleColumnBlock))state.getFluidState().animateTick(level,pos,level.random);}
                    if(state.getBlock() instanceof net.minecraft.world.level.block.BubbleColumnBlock)bubbles(level,pos,state);
                    if(state.getBlock() instanceof EntityBlock block&&!(block instanceof dev.gungang.acoustics.server.ConveyorBlock))clientTicker(level,block,state,pos,blockEntity(prop,part.offset(),state,block));
                }
            }finally{particleOffset=net.minecraft.world.phys.Vec3.ZERO;}
            BlockPos cell=BlockPos.containing(prop.getX(),prop.getY()+.5,prop.getZ());
            glow(level,prop.getId(),emission>0&&!PropRenderer.concealed(prop)?cell:null,emission);
        }
        for(var id:List.copyOf(GLOWS.keySet()))if(!seen.contains(id))glow(level,id,null,0);
        TICKING.keySet().retainAll(seen);MOTION.keySet().retainAll(seen);RINGS.keySet().retainAll(seen);
    }
    /** Vanilla bubble particles die outside water, and the disguise's cell is air: pops rise (or sink) through the column instead. */
    private static void bubbles(ClientLevel level,BlockPos pos,BlockState state){
        boolean down=state.getValue(net.minecraft.world.level.block.BubbleColumnBlock.DRAG_DOWN);var random=level.random;
        for(int i=0;i<2;i++)level.addParticle(net.minecraft.core.particles.ParticleTypes.BUBBLE_POP,pos.getX()+.15+random.nextDouble()*.7,pos.getY()+random.nextDouble()*.85,pos.getZ()+.15+random.nextDouble()*.7,0,down?-.08:.12,0);
        if(!down&&random.nextInt(4)==0)level.addParticle(net.minecraft.core.particles.ParticleTypes.SPLASH,pos.getX()+.5,pos.getY()+.9,pos.getZ()+.5,0,0,0);
        if(random.nextInt(200)==0)level.playLocalSound(pos,down?net.minecraft.sounds.SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT:net.minecraft.sounds.SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT,net.minecraft.sounds.SoundSource.BLOCKS,.2f+random.nextFloat()*.2f,.9f+random.nextFloat()*.15f,false);
    }
    /** Interpolated open progress of a shutter or shulker box disguise. */
    static float motion(PropEntity prop,float partial){var m=MOTION.get(prop.getId());return m==null?prop.open()?1:0:net.minecraft.util.Mth.lerp(partial,m[0],m[1]);}
    /**
     * The one client block entity of a prop part, shared by the ticker here and PropRenderer,
     * so what animates (enchanting book, chest lid) is what gets drawn. Null for blocks without one.
     */
    static BlockEntity blockEntity(PropEntity prop,BlockPos offset,BlockState state,EntityBlock block){
        var cache=TICKING.computeIfAbsent(prop.getId(),id->new HashMap<>());var be=cache.get(offset);
        if(be==null||be.getBlockState().getBlock()!=state.getBlock()){
            be=block instanceof net.minecraft.world.level.block.ShulkerBoxBlock?new Shulker(prop,prop.blockPosition().offset(offset),state):block.newBlockEntity(prop.blockPosition().offset(offset),state);
            if(be==null){cache.remove(offset);return null;}
            be.setLevel(prop.level());cache.put(offset,be);
            if(be instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity spawner)spawner.getSpawner().setEntityId(net.minecraft.world.entity.EntityType.CAVE_SPIDER,prop.level(),prop.level().random,prop.blockPosition());
        }
        be.setBlockState(state);
        if(be instanceof net.minecraft.world.level.block.entity.BellBlockEntity bell){
            // The server counts rings in MODE; each new ring swings the bell like a click on its facing side.
            Integer rung=RINGS.put(prop.getId(),prop.mode());
            if(rung!=null&&rung!=prop.mode())bell.triggerEvent(1,state.getValue(net.minecraft.world.level.block.BellBlock.FACING).get3DDataValue());
        }
        if(be instanceof dev.gungang.acoustics.server.HologramEntity hologram){
            // The server sends the projected weapon as item NBT; size and spin step with E and R.
            var stack=hologram(prop.text());if(!net.minecraft.world.item.ItemStack.matches(hologram.displayed(),stack))hologram.replace(stack);
            int size=dev.gungang.acoustics.server.PropAbilities.hologramSize(prop.mode()),spin=dev.gungang.acoustics.server.PropAbilities.hologramSpeed(prop.mode());
            if(hologram.size()!=size||hologram.rotation()!=spin)hologram.settings(size,spin);
        }
        if(be instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign){
            var text=sign.getFrontText();String[] lines=prop.text().split(", ",3);
            for(int i=0;i<4;i++)text=text.setMessage(i,net.minecraft.network.chat.Component.literal(i<lines.length?lines[i]:""));
            sign.setText(text,true);sign.setText(text,false);
        }
        return be;
    }
    private static net.minecraft.world.item.ItemStack hologram(String nbt){
        if(nbt.isEmpty())return net.minecraft.world.item.ItemStack.EMPTY;
        if(HOLOGRAMS.size()>64)HOLOGRAMS.clear();
        return HOLOGRAMS.computeIfAbsent(nbt,text->{try{return net.minecraft.world.item.ItemStack.of(net.minecraft.nbt.TagParser.parseTag(text));}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){return net.minecraft.world.item.ItemStack.EMPTY;}});
    }
    /** A shulker box whose lid follows the disguise's open state; its own animation (which pushes entities) never runs. */
    private static final class Shulker extends net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity {
        private final PropEntity prop;
        Shulker(PropEntity prop,BlockPos pos,BlockState state){super(net.minecraft.world.level.block.ShulkerBoxBlock.getColorFromBlock(state.getBlock()),pos,state);this.prop=prop;}
        @Override public float getProgress(float partial){return motion(prop,partial);}
    }
    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> void clientTicker(ClientLevel level,EntityBlock block,BlockState state,BlockPos pos,BlockEntity be){
        if(be==null||be instanceof Shulker)return;
        var ticker=block.getTicker(level,state,(net.minecraft.world.level.block.entity.BlockEntityType<T>)be.getType());
        if(ticker!=null)ticker.tick(level,pos,state,(T)be);
    }
    private static void glow(ClientLevel level,int id,BlockPos pos,int emission){
        var current=GLOWS.get(id);
        if(current!=null&&current.pos().equals(pos)&&level.getBlockState(pos).is(Blocks.LIGHT))return;
        if(current!=null){
            GLOWS.remove(id);
            if(level.getBlockState(current.pos()).is(Blocks.LIGHT))level.setBlock(current.pos(),current.original(),3);
        }
        if(pos==null||!level.getBlockState(pos).isAir())return;
        GLOWS.put(id,new Glow(pos,level.getBlockState(pos)));
        level.setBlock(pos,Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL,emission),3);
    }
    private PropEffects(){}
}
