package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Finds what still breaks map blocks: whenever a map block turns into air or fire on the server, the code that did it
 * (the first frame outside Minecraft, Forge and Mixin) and the nearest fighters with what they hold go to the server
 * log and to {@code logs/gungang-block-breaks.log}. One line per cause every 30 seconds.
 */
public final class BlockBreakTrace {
    private static final StackWalker WALKER=StackWalker.getInstance();
    private static final Map<String,Long> LAST=new HashMap<>();
    private static final long QUIET_MS=30_000;
    private static final Path FILE=Paths.get("logs","gungang-block-breaks.log");
    private static boolean busy;

    /** Called from {@code BlockBreakTraceMixin} before {@code Level.setBlock}. */
    public static void check(Level level,BlockPos pos,BlockState next){
        if(busy||level.isClientSide||!(level instanceof ServerLevel server))return;
        if(!next.isAir()&&!(next.getBlock() instanceof BaseFireBlock))return;
        var old=level.getBlockState(pos);
        if(old.isAir()||old.getBlock()==next.getBlock()||ambient(old))return;
        var oldId=ForgeRegistries.BLOCKS.getKey(old.getBlock());
        if(oldId==null||oldId.getNamespace().equals("gungang"))return;
        busy=true;
        try{
            String cause=cause();
            // Creative players building the map, and WorldEdit.
            if(cause.contains("ServerPlayerGameMode")||cause.contains("worldedit"))return;
            long now=System.currentTimeMillis();
            Long last=LAST.get(cause);
            if(last!=null&&now-last<QUIET_MS)return;
            LAST.put(cause,now);
            String line="%s %s at %s %d,%d,%d -> %s | cause: %s | near: %s".formatted(
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),oldId,server.dimension().location(),
                    pos.getX(),pos.getY(),pos.getZ(),ForgeRegistries.BLOCKS.getKey(next.getBlock()),cause,near(server,pos));
            com.mojang.logging.LogUtils.getLogger().warn("[GunGang] Map block broken: {}",line);
            try{
                Files.createDirectories(FILE.getParent());
                Files.writeString(FILE,line+System.lineSeparator(),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            }catch(java.io.IOException ignored){}
        }catch(RuntimeException ignored){
        }finally{busy=false;}
    }
    /** Blocks the world removes by itself all the time (fire burning out, snow, plants, leaves, liquids). */
    private static boolean ambient(BlockState state){
        var block=state.getBlock();
        return block instanceof BaseFireBlock||block instanceof SnowLayerBlock||block instanceof LeavesBlock||block instanceof LiquidBlock
                ||block instanceof BushBlock||block instanceof GimmickBlock||block instanceof BubbleColumnBlock;
    }
    /** The first frames outside the game, Forge and Mixin, else the vanilla caller of setBlock. */
    private static String cause(){
        return WALKER.walk(frames->{
            var list=frames.skip(2).limit(48).toList();
            var outside=new StringBuilder();String vanilla=null;
            for(var f:list){
                String c=f.getClassName();
                if(c.equals(BlockBreakTrace.class.getName()))continue;
                boolean game=c.startsWith("net.minecraft.")||c.startsWith("net.minecraftforge.")||c.startsWith("org.spongepowered.")
                        ||c.startsWith("java.")||c.startsWith("jdk.")||c.startsWith("sun.")||c.startsWith("cpw.")||c.startsWith("kotlin.");
                String frame=c+"."+f.getMethodName()+":"+f.getLineNumber();
                if(!game){if(outside.length()>0)outside.append(" <- ");outside.append(frame);if(outside.length()>300)break;}
                else if(outside.length()>0)break;
                else if(vanilla==null&&!f.getMethodName().matches("setBlock|m_6933_|m_7731_|removeBlock|m_7471_|destroyBlock|m_7740_|m_46953_|m_46961_"))vanilla=frame;
            }
            return outside.length()>0?outside.toString():"vanilla "+vanilla;
        });
    }
    /** The three nearest players or bots within 24 blocks, with what they hold. */
    private static String near(ServerLevel level,BlockPos pos){
        var center=net.minecraft.world.phys.Vec3.atCenterOf(pos);
        var bodies=level.getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(24),
                e->e instanceof net.minecraft.server.level.ServerPlayer||e instanceof BotEntity);
        bodies.sort(java.util.Comparator.comparingDouble(e->e.distanceToSqr(center)));
        var text=new StringBuilder();
        for(var e:bodies.subList(0,Math.min(3,bodies.size()))){
            var stack=e.getMainHandItem();var tag=stack.getTag();
            String held=tag!=null&&tag.contains("GunId")?tag.getString("GunId"):String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
            if(text.length()>0)text.append(", ");
            text.append(e.getName().getString()).append(" (").append(held).append(", ").append(Math.round(Math.sqrt(e.distanceToSqr(center)))).append("m)");
        }
        return text.length()>0?text.toString():"nobody";
    }
    private BlockBreakTrace(){}
}
