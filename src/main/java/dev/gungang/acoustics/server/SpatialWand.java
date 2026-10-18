package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class SpatialWand extends Item {
    private static final Map<UUID,Selection> SELECTIONS=new HashMap<>();
    public record Selection(String dimension,BlockPos first,BlockPos second){
        public boolean complete(){return first!=null&&second!=null;}
        public MapPreviewArea bounds(){
            if(!complete())throw new IllegalArgumentException("좌클릭과 우클릭으로 두 지점을 먼저 선택하세요.");
            return new MapPreviewArea(Math.min(first.getX(),second.getX()),Math.min(first.getY(),second.getY()),Math.min(first.getZ(),second.getZ()),Math.max(first.getX(),second.getX()),Math.max(first.getY(),second.getY()),Math.max(first.getZ(),second.getZ()));
        }
    }
    public SpatialWand(){super(new Properties().stacksTo(1));}
    @Override public boolean isFoil(ItemStack stack){return true;}
    public static boolean holding(net.minecraft.world.entity.player.Player p){return p.getMainHandItem().is(GadgetItems.SPATIAL_WAND.get());}
    public static Selection selection(ServerPlayer p){
        var s=SELECTIONS.get(p.getUUID());
        if(s==null||!s.dimension.equals(p.level().dimension().location().toString()))throw new IllegalArgumentException("현재 차원에서 영역을 먼저 선택하세요.");
        return s;
    }
    public static void clear(ServerPlayer p){SELECTIONS.remove(p.getUUID());}
    public static void select(ServerPlayer p,BlockPos pos,boolean first){
        if(!p.hasPermissions(2))throw new IllegalArgumentException("공간마법사는 관리자만 사용할 수 있습니다.");
        if(!p.level().isInWorldBounds(pos))throw new IllegalArgumentException("월드 범위를 벗어난 위치입니다.");
        String dim=p.level().dimension().location().toString();var old=SELECTIONS.get(p.getUUID());
        if(old==null||!old.dimension.equals(dim))old=new Selection(dim,null,null);
        var next=new Selection(dim,first?pos.immutable():old.first,first?old.second:pos.immutable());
        if(next.equals(old))return;
        SELECTIONS.put(p.getUUID(),next);
        String text=(first?"첫 번째":"두 번째")+" 위치: "+pos.getX()+", "+pos.getY()+", "+pos.getZ();
        if(next.complete())text+=" | "+size(next.bounds());
        p.sendSystemMessage(Component.literal(text));p.displayClientMessage(Component.literal(text),true);draw(p,next);
    }
    static String size(MapPreviewArea b){return "가로(X) "+((long)b.maxX()-b.minX()+1)+" × 세로(Z) "+((long)b.maxZ()-b.minZ()+1)+" × 높이(Y) "+((long)b.maxY()-b.minY()+1)+" 블록";}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void left(PlayerInteractEvent.LeftClickBlock e){
        if(!holding(e.getEntity()))return;e.setCanceled(true);
        if(e.getEntity() instanceof ServerPlayer p&&e.getAction()==PlayerInteractEvent.LeftClickBlock.Action.START)pick(p,e.getPos(),true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void right(PlayerInteractEvent.RightClickBlock e){
        if(e.getHand()!=InteractionHand.MAIN_HAND||!holding(e.getEntity()))return;e.setCanceled(true);
        if(e.getEntity() instanceof ServerPlayer p)pick(p,e.getPos(),false);
    }
    private static void pick(ServerPlayer p,BlockPos pos,boolean first){try{select(p,pos,first);}catch(IllegalArgumentException ex){p.displayClientMessage(Component.literal(ex.getMessage()),true);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void breaking(BlockEvent.BreakEvent e){if(holding(e.getPlayer()))e.setCanceled(true);}
    @SubscribeEvent public static void attack(AttackEntityEvent e){if(holding(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){SELECTIONS.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)||p.tickCount%10!=0||!holding(p)||!p.hasPermissions(2))return;
        var s=SELECTIONS.get(p.getUUID());if(s!=null&&s.dimension.equals(p.level().dimension().location().toString()))draw(p,s);
    }
    private static void draw(ServerPlayer p,Selection s){
        if(!s.complete())return;var b=s.bounds();
        double[] lo={b.minX(),b.minY(),b.minZ()},hi={b.maxX()+1.0,b.maxY()+1.0,b.maxZ()+1.0};
        var dust=new DustParticleOptions(new org.joml.Vector3f(.3f,.9f,1f),.8f);
        for(int axis=0;axis<3;axis++)for(int a=0;a<2;a++)for(int c=0;c<2;c++)for(int i=0;i<=12;i++){
            double[] at=lo.clone();int j=(axis+1)%3,k=(axis+2)%3;at[axis]=lo[axis]+(hi[axis]-lo[axis])*i/12;at[j]=a==0?lo[j]:hi[j];at[k]=c==0?lo[k]:hi[k];
            if(p.distanceToSqr(at[0],at[1],at[2])<128*128)p.serverLevel().sendParticles(p,dust,true,at[0],at[1],at[2],1,0,0,0,0);
        }
    }
}
