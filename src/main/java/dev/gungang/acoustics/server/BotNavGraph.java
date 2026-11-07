package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Whole-map walking grid for long routes. A vanilla path search gives up within the follow range and only sees what it
 * expands, so on long winding maps (왕의 길) a bot heading for the far side walked straight into dead ends. The arena
 * is scanned once, a few chunks per tick, into one byte per block; the grid then answers reachability (connected
 * parts) and A* routes that know jump pads, gap jumps and shutters opened by a nearby lever or button, hops over
 * low railings (iron bars, panes), long drops (any height into water) and swimming at the water surface, such as
 * 이동점령's attackers leaving their ship: over the deck bars, down to the bow, overboard and ashore. Conveyor belts
 * are priced by their live direction and speed: riding one is a shortcut, walking against one is slow or ruled out.
 */
final class BotNavGraph {
    /**
     * WATER: a water cell (swum at its surface). THIN: a low obstacle too narrow to stand on (bars, panes), hopped over.
     * BELT: a conveyor, a floor that carries whoever stands on it.
     */
    static final int PASS=1,FLOOR=2,DANGER=4,DYNAMIC=8,PAD=16,SWITCH=32,SWITCHABLE=64,WATER=128,THIN=256,BELT=512;
    /**
     * Moves between neighbouring stand cells. DOOR walks into a closed shutter that a switch can open; DROP walks off
     * an edge higher than the vanilla pathfinder's 3 blocks; VAULT jumps over a low obstacle (and falls beyond it).
     */
    static final int WALK=0,JUMP=1,LAUNCH=2,DOOR=3,DROP=4,VAULT=5;
    /**
     * Highest fall the grid plans, onto land or into water. Death zones are DANGER cells, so a fall never ends in or
     * passes through one; whether the fall damage is survivable is checked by the bot at the edge (BotRoute).
     */
    private static final int MAX_FALL=24;
    private static final int MAX_CELLS=8_000_000,CHUNKS_PER_TICK=4,LOADS_PER_TICK=1,FINISH_PER_TICK=400_000,
            SEARCHES_PER_TICK=2,EXPANSIONS=60_000,SWITCH_RANGE=8;
    private static final float DOOR_COST=12;
    /** A bot's path-walking pace in blocks per tick, against which a belt's drift is weighed. */
    private static final double PACE=.28;
    private static final Map<String,BotNavGraph> GRAPHS=new HashMap<>();
    private static final Map<BlockState,Short> KINDS=new IdentityHashMap<>();
    private static int searches=SEARCHES_PER_TICK;

    final String key;
    private final int x0,y0,z0,w,h,d;
    private final short[] cells;
    private int[] parts;
    private final ArenaMap arena;
    private final ArrayDeque<long[]> chunks=new ArrayDeque<>();
    private final List<BlockPos> switches=new ArrayList<>(),shutters=new ArrayList<>();
    private final Map<Integer,int[]> landings=new HashMap<>();
    private int finished=-1;
    private boolean ready;

    private BotNavGraph(String key,ArenaMap arena,int[] b){
        this.key=key;this.arena=arena;x0=b[0];y0=b[1];z0=b[2];w=b[3]-b[0]+1;h=b[4]-b[1]+1;d=b[5]-b[2]+1;
        cells=new short[w*h*d];
        for(int cx=x0>>4;cx<=(x0+w-1)>>4;cx++)for(int cz=z0>>4;cz<=(z0+d-1)>>4;cz++)chunks.add(new long[]{cx,cz});
    }
    /** The finished grid for this arena, or null while it is still being scanned (or the map is too large). */
    static BotNavGraph of(ArenaMap arena,Level level){
        if(arena==null||!(level instanceof ServerLevel))return null;
        String key=arena.id()+"/"+arena.dimension();
        var graph=GRAPHS.get(key);
        if(graph==null&&!GRAPHS.containsKey(key)){
            var bounds=bounds(arena);
            graph=bounds==null?null:new BotNavGraph(key,arena,bounds);
            if(bounds==null)com.mojang.logging.LogUtils.getLogger().info("[GunGang] Bot map grid skipped for {}: arena too large",arena.id());
            GRAPHS.put(key,graph);
        }
        return graph!=null&&graph.ready?graph:null;
    }
    static void forget(){GRAPHS.clear();}
    /** Once per server tick: refill the search budget and continue any scan in progress. */
    static void tick(ServerLevel level,ArenaMap arena){
        searches=SEARCHES_PER_TICK;
        if(level==null||arena==null)return;
        of(arena,level);
        var graph=GRAPHS.get(arena.id()+"/"+arena.dimension());
        if(graph!=null&&!graph.ready)graph.build(level);
    }
    static boolean searchSpent(){return searches<=0;}

    /** The preview area when the map has one, else a box around both spawns; inclusive, with room for floors and heads. */
    private static int[] bounds(ArenaMap arena){
        var a=arena.alphaSpawn();var b=arena.bravoSpawn();
        int lowSpawn=Mth.floor(Math.min(a.y(),b.y())),highSpawn=Mth.floor(Math.max(a.y(),b.y()));
        int[] r;var p=arena.previewArea();
        if(p!=null)r=new int[]{p.minX(),p.minY()-1,p.minZ(),p.maxX(),p.maxY()+3,p.maxZ()};
        else r=new int[]{Mth.floor(Math.min(a.x(),b.x()))-48,lowSpawn-16,Mth.floor(Math.min(a.z(),b.z()))-48,
                Mth.floor(Math.max(a.x(),b.x()))+48,highSpawn+24,Mth.floor(Math.max(a.z(),b.z()))+48};
        if(volume(r)>MAX_CELLS){r[1]=Math.max(r[1],lowSpawn-24);r[4]=Math.min(r[4],highSpawn+40);}
        return volume(r)>MAX_CELLS||r[4]<r[1]?null:r;
    }
    private static long volume(int[] r){return (long)(r[3]-r[0]+1)*(r[4]-r[1]+1)*(r[5]-r[2]+1);}

    // ---- scan ----
    private void build(ServerLevel level){
        if(!chunks.isEmpty()){
            int loads=LOADS_PER_TICK;
            for(int done=0;done<CHUNKS_PER_TICK&&!chunks.isEmpty();done++){
                var next=chunks.peekFirst();int cx=(int)next[0],cz=(int)next[1];
                var chunk=level.getChunkSource().getChunkNow(cx,cz);
                if(chunk==null){if(loads--<=0)return;chunk=level.getChunk(cx,cz);}
                chunks.removeFirst();scan(chunk,cx,cz);
            }
            return;
        }
        if(finished<0){prepare();finished=0;parts=new int[cells.length];Arrays.fill(parts,-1);return;}
        int end=Math.min(cells.length,finished+FINISH_PER_TICK);
        for(int i=finished;i<end;i++){
            int x=x0+i%w,z=z0+(i/w)%d,y=y0+i/(w*d);
            if(stand(x,y,z,null,null,null)<0)continue;
            if(parts[i]<0)parts[i]=i;
            if((cells[i]&PAD)!=0)padLandings(i,level,x,y,z);
            int from=i;moves(x,y,z,null,null,null,(nx,ny,nz,cost,kind)->union(from,index(nx,ny,nz)));
        }
        finished=end;
        if(finished>=cells.length){
            ready=true;
            com.mojang.logging.LogUtils.getLogger().info("[GunGang] Bot map grid ready for {}: {}x{}x{}, {} pads, {} shutters, {} switches",
                    arena.id(),w,h,d,landings.size(),shutters.size(),switches.size());
        }
    }
    private void scan(net.minecraft.world.level.chunk.LevelChunk chunk,int cx,int cz){
        int ax=Math.max(x0,cx<<4),bx=Math.min(x0+w-1,(cx<<4)+15),az=Math.max(z0,cz<<4),bz=Math.min(z0+d-1,(cz<<4)+15);
        var pos=new BlockPos.MutableBlockPos();
        for(int y=y0;y<y0+h;y++){
            int section=chunk.getSectionIndex(y);
            boolean above=section>=chunk.getSectionsCount(),below=section<0;
            var data=above||below?null:chunk.getSection(section);
            boolean air=data==null||data.hasOnlyAir();
            for(int z=az;z<=bz;z++)for(int x=ax;x<=bx;x++){
                int i=index(x,y,z);
                if(air){cells[i]=(short)(below?0:PASS);continue;}
                var state=data.getBlockState(x&15,y&15,z&15);
                int kind=kind(state,chunk,pos.set(x,y,z));
                cells[i]=(short)kind;
                if((kind&SWITCH)!=0)switches.add(pos.immutable());
                if(state.getBlock() instanceof ShutterBlock)shutters.add(pos.immutable());
            }
        }
    }
    private static int kind(BlockState state,net.minecraft.world.level.BlockGetter level,BlockPos pos){
        var cached=KINDS.get(state);if(cached!=null)return cached;
        var block=state.getBlock();int flags=0;
        if(block instanceof ShutterBlock||block instanceof SpawnGateBlock||block instanceof DoorBlock||block instanceof FenceGateBlock||block instanceof TrapDoorBlock)flags=DYNAMIC;
        else {
            if(block instanceof LeverBlock||block instanceof ButtonBlock)flags|=SWITCH;
            if(block instanceof JumpPadBlock)flags|=PAD;
            if(block instanceof ConveyorBlock)flags|=BELT;
            if(state.getFluidState().is(FluidTags.WATER))flags|=WATER;
            if(state.getFluidState().is(FluidTags.LAVA)||state.is(BlockTags.FIRE)||block instanceof AcidBlock||AcidLogging.acid(state)
                    ||block instanceof PuddleBlock puddle&&puddle.acid
                    ||block==Blocks.MAGMA_BLOCK||block==Blocks.CACTUS||block==Blocks.SWEET_BERRY_BUSH||block==Blocks.POWDER_SNOW
                    ||block==Blocks.WITHER_ROSE||block instanceof CampfireBlock&&state.getValue(CampfireBlock.LIT)||block instanceof DeathZoneBlock)flags|=DANGER;
            net.minecraft.world.phys.shapes.VoxelShape shape;
            try{shape=state.getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);}
            catch(RuntimeException error){shape=state.getCollisionShape(level,pos);}
            // Carpets, plates and pads are stood in; anything up to a full block and wide enough is stood on; narrow
            // low blocks (iron bars, panes) are hopped over; fences and walls are neither.
            double top=shape.isEmpty()?0:shape.max(Direction.Axis.Y);
            if(top<=.2)flags|=PASS;
            else if(top<=1){var box=shape.bounds();flags|=box.getXsize()>=.75&&box.getZsize()>=.75?FLOOR:THIN;}
        }
        KINDS.put(state,(short)flags);return flags;
    }
    /** Shutters with a switch close enough to be the one wired to them. Death zones are blocks, flagged by kind(). */
    private void prepare(){
        for(var shutter:shutters)for(var lever:switches)
            if(Math.abs(lever.getX()-shutter.getX())<=SWITCH_RANGE&&Math.abs(lever.getZ()-shutter.getZ())<=SWITCH_RANGE&&Math.abs(lever.getY()-shutter.getY())<=4){
                cells[index(shutter.getX(),shutter.getY(),shutter.getZ())]|=SWITCHABLE;break;
            }
    }
    /** Every ledge a launch from this pad lands on: rise straight up, then drift over at walking pace. */
    private void padLandings(int i,ServerLevel level,int x,int y,int z){
        if(!(level.getBlockState(new BlockPos(x,y,z)).getBlock() instanceof JumpPadBlock pad))return;
        double velocity=MotionRules.jumpVelocity(pad.height);var found=new ArrayList<Integer>();
        for(int ly=y;ly<=y+pad.height&&found.size()<32;ly++)for(int dx=-7;dx<=7;dx++)for(int dz=-7;dz<=7;dz++){
            if(dx*dx+dz*dz<2||stand(x+dx,ly,z+dz,null,null,null)<0)continue;
            if(BotParkour.padFlight(new Vec3(x+.5,y+.125,z+.5),velocity,new Vec3(x+dx+.5,ly,z+dz+.5),this::bodyClear)>0)found.add(index(x+dx,ly,z+dz));
        }
        if(!found.isEmpty())landings.put(i,found.stream().mapToInt(Integer::intValue).toArray());
    }
    /** A standing body at p touches only open cells. */
    boolean bodyClear(Vec3 p){
        for(int x=Mth.floor(p.x-.3);x<=Mth.floor(p.x+.3);x++)for(int z=Mth.floor(p.z-.3);z<=Mth.floor(p.z+.3);z++)
            for(int y=Mth.floor(p.y+.01);y<=Mth.floor(p.y+1.79);y++){int c=cell(x,y,z);if((c&PASS)==0||(c&DANGER)!=0)return false;}
        return true;
    }

    // ---- cells ----
    private boolean inside(int x,int y,int z){return x>=x0&&x<x0+w&&y>=y0&&y<y0+h&&z>=z0&&z<z0+d;}
    private int index(int x,int y,int z){return ((y-y0)*d+(z-z0))*w+(x-x0);}
    private BlockPos pos(int i){return new BlockPos(x0+i%w,y0+i/(w*d),z0+(i/w)%d);}
    Vec3 point(int i){return Vec3.atBottomCenterOf(pos(i));}
    /** Above the scanned box counts as sky; below and beside it as nothing to stand on. */
    private int cell(int x,int y,int z){
        if(x<x0||x>=x0+w||z<z0||z>=z0+d||y<y0)return 0;
        return y>=y0+h?PASS:cells[index(x,y,z)]&0xffff;
    }
    /** 0 open, 1 a closed shutter a switch may open, -1 blocked. Without a level, changeable blocks count as open. */
    private int clear(int x,int y,int z,Level level,BotEntity bot,Set<Long> blocked){
        int c=cell(x,y,z);
        if((c&DANGER)!=0)return -1;
        if((c&PASS)!=0)return 0;
        if((c&DYNAMIC)==0)return -1;
        if(level==null)return 0;
        var pos=new BlockPos(x,y,z);
        if(!level.isLoaded(pos))return 0;
        var state=level.getBlockState(pos);
        if(state.getBlock() instanceof SpawnGateBlock gate)return !gate.present(state)||gate.allows(bot)?0:-1;
        if(state.getBlock() instanceof ShutterBlock){
            if(shutterOpen(level,pos))return 0;
            return (c&SWITCHABLE)!=0&&(blocked==null||!blocked.contains(pos.asLong()))?1:-1;
        }
        var shape=state.getCollisionShape(level,pos);
        return shape.isEmpty()||shape.max(Direction.Axis.Y)<=.2?0:-1;
    }
    static boolean shutterOpen(net.minecraft.world.level.BlockGetter level,BlockPos pos){
        return !(level.getBlockEntity(pos) instanceof ShutterEntity tile)||tile.localBottom(0)>=.95;
    }
    /**
     * Feet and head room on a floor, or a swimmer's place at the water surface (water at the feet, air above):
     * 0 standable, 1 standable once a shutter opens, -1 not. Deep under water is not a place to stand.
     */
    private int stand(int x,int y,int z,Level level,BotEntity bot,Set<Long> blocked){
        if(!inside(x,y,z))return -1;
        int at=cell(x,y,z),over=cell(x,y+1,z);
        if((at&DANGER)!=0)return -1;
        if((at&WATER)!=0&&(at&PASS)!=0){
            if((over&WATER)!=0)return -1;
            return clear(x,y+1,z,level,bot,blocked)==0?0:-1;
        }
        int floor=cell(x,y-1,z);
        if((floor&FLOOR)==0||(floor&DANGER)!=0)return -1;
        int feet=clear(x,y,z,level,bot,blocked);if(feet<0)return -1;
        int head=clear(x,y+1,z,level,bot,blocked);if(head<0)return -1;
        return Math.max(feet,head);
    }
    interface Sink{void move(int x,int y,int z,float cost,int kind);}
    /**
     * Walks to the four neighbours (step up one, drop up to three), longer drops, hops over low obstacles, gap jumps of
     * two to four blocks and pad launches.
     */
    private void moves(int x,int y,int z,Level level,BotEntity bot,Set<Long> blocked,Sink sink){
        for(var direction:Direction.Plane.HORIZONTAL){
            int dx=direction.getStepX(),dz=direction.getStepZ(),nx=x+dx,nz=z+dz;
            float belt=belt(x,y,z,dx,dz,level);if(belt==Float.POSITIVE_INFINITY)continue;
            int s=stand(nx,y,nz,level,bot,blocked);
            if(s>=0){sink.move(nx,y,nz,s==1?belt+DOOR_COST:belt,s==1?DOOR:WALK);continue;}
            if(clear(x,y+2,z,level,bot,blocked)==0&&(s=stand(nx,y+1,nz,level,bot,blocked))>=0){sink.move(nx,y+1,nz,s==1?1.5f*belt+DOOR_COST:1.5f*belt,s==1?DOOR:WALK);continue;}
            if(clear(nx,y,nz,level,bot,blocked)!=0||clear(nx,y+1,nz,level,bot,blocked)!=0) {
                vault(x,y,z,dx,dz,level,bot,blocked,sink);
                continue;
            }
            int fall=landing(nx,y,nz,1,level,bot,blocked);
            if(fall>=1&&(blocked==null||!blocked.contains(BlockPos.asLong(nx,y-fall,nz)))){
                float cost=fallCost(fall,(cell(nx,y-fall,nz)&WATER)!=0);
                if(cost<Float.MAX_VALUE)sink.move(nx,y-fall,nz,belt+cost,fall<=3?WALK:DROP);
                if(fall<=3)continue;
            }
            if(clear(x,y+2,z,level,bot,blocked)!=0)continue;
            jumps:
            for(int length=2;length<=4;length++){
                for(int i=1;i<length;i++)for(int up=0;up<=2;up++)if(clear(x+dx*i,y+up,z+dz*i,level,bot,blocked)!=0)break jumps;
                int lx=x+dx*length,lz=z+dz*length;
                if(stand(lx,y,lz,level,bot,blocked)==0&&clear(lx,y+2,lz,level,bot,blocked)==0
                        &&(blocked==null||!blocked.contains(BlockPos.asLong(lx,y,lz)))){sink.move(lx,y,lz,length+2,JUMP);break;}
            }
        }
        if((cell(x,y,z)&PAD)!=0){
            var targets=landings.get(index(x,y,z));
            if(targets!=null)for(int t:targets){
                var p=pos(t);
                if(blocked!=null&&blocked.contains(p.asLong()))continue;
                if(level==null||stand(p.getX(),p.getY(),p.getZ(),level,bot,blocked)==0)
                    sink.move(p.getX(),p.getY(),p.getZ(),(float)Math.sqrt((p.getX()-x)*(p.getX()-x)+(p.getZ()-z)*(p.getZ()-z))+1,LAUNCH); // pads are a shortcut, used readily
            }
        }
    }
    /**
     * Travel-time multiplier for a one-block step from a cell whose floor is a belt: with the belt the bot moves at
     * pace plus drift, against it at pace minus drift, across it crabbing to cancel the drift. A belt the bot can
     * barely outwalk is not walked against at all. Without a level (the grid scan) every belt costs plain walking,
     * so connectivity stays optimistic, like one-way drops.
     */
    private float belt(int x,int y,int z,int dx,int dz,Level level){
        if(level==null||(cell(x,y-1,z)&BELT)==0)return 1;
        var pos=new BlockPos(x,y-1,z);
        if(!level.isLoaded(pos))return 1;
        var state=level.getBlockState(pos);
        if(!(state.getBlock() instanceof ConveyorBlock)||!(level.getBlockEntity(pos) instanceof ConveyorEntity tile))return 1;
        double drift=.01*tile.speed; // ConveyorEntity's travel per tick
        var out=ConveyorBlock.output(state);var ports=ConveyorBlock.ports(state.getValue(ConveyorBlock.SHAPE));
        var in=ports[0]==out?ports[1]:ports[0];
        double pace;
        if(dx==out.getStepX()&&dz==out.getStepZ())pace=PACE+drift;
        else if(dx==in.getStepX()&&dz==in.getStepZ())pace=PACE-drift;
        else pace=Math.sqrt(Math.max(0,PACE*PACE-drift*drift));
        return pace<PACE*.3?Float.POSITIVE_INFINITY:(float)(PACE/pace);
    }
    /** Whether this stand cell's floor is a belt. */
    private boolean onBelt(int i){var p=pos(i);return (cell(p.getX(),p.getY()-1,p.getZ())&BELT)!=0;}
    /** Blocks fallen before landing in this column, starting {@code from} below y (0 = level); -1 when none in reach. */
    private int landing(int x,int y,int z,int from,Level level,BotEntity bot,Set<Long> blocked){
        for(int k=from;k<=MAX_FALL;k++){
            if(stand(x,y-k,z,level,bot,blocked)==0)return k;
            if(clear(x,y-k,z,level,bot,blocked)!=0)return -1;
        }
        return -1;
    }
    /** Water breaks any fall; onto safe land a drop is taken boldly, only mildly priced for its fall damage. */
    private static float fallCost(int k,boolean water){
        if(water)return k*.3f;
        return k<=3?k*.5f:1.5f+(k-3)*.6f;
    }
    /** Over a low obstacle beside the bot (a railing of bars, a block) to the column beyond it, then down if need be. */
    private void vault(int x,int y,int z,int dx,int dz,Level level,BotEntity bot,Set<Long> blocked,Sink sink){
        int nx=x+dx,nz=z+dz,low=cell(nx,y,nz);
        if((low&(FLOOR|THIN))==0||(low&(DANGER|DYNAMIC))!=0)return;
        if(clear(nx,y+1,nz,level,bot,blocked)!=0||clear(nx,y+2,nz,level,bot,blocked)!=0||clear(x,y+2,z,level,bot,blocked)!=0)return;
        int lx=nx+dx,lz=nz+dz;
        if(clear(lx,y+1,lz,level,bot,blocked)!=0||clear(lx,y+2,lz,level,bot,blocked)!=0)return;
        int fall=landing(lx,y,lz,0,level,bot,blocked);if(fall<0)return;
        if(blocked!=null&&blocked.contains(BlockPos.asLong(lx,y-fall,lz)))return;
        float cost=fallCost(fall,(cell(lx,y-fall,lz)&WATER)!=0);
        if(cost<Float.MAX_VALUE)sink.move(lx,y-fall,lz,4+cost,VAULT);
    }
    private int find(int i){while(parts[i]!=i){parts[i]=parts[parts[i]];i=parts[i];}return i;}
    private void union(int a,int b){
        if(parts[b]<0)parts[b]=b;
        int ra=find(a),rb=find(b);if(ra!=rb)parts[Math.max(ra,rb)]=Math.min(ra,rb);
    }

    // ---- queries ----
    /** The stand cell nearest a point: its own column first (up to 16 above or below), then up to three blocks around. */
    int snap(Vec3 p){
        int bx=Mth.floor(p.x),by=Mth.floor(p.y+.01),bz=Mth.floor(p.z);
        for(int r=0;r<=3;r++)for(int dy=0;dy<=16;dy=dy<=0?1-dy:-dy)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=r)continue;
            int x=bx+dx,y=by+dy,z=bz+dz;
            if(inside(x,y,z)&&stand(x,y,z,null,null,null)>=0)return index(x,y,z);
        }
        return -1;
    }
    boolean contains(Vec3 p){return inside(Mth.floor(p.x),Mth.floor(p.y),Mth.floor(p.z));}
    /** Bounded sampling over the whole connected map, including floors above/below the spawn. */
    BlockPos roamSpot(Vec3 from,Random random){
        int start=snap(from);if(start<0||parts[start]<0)return null;
        int component=find(start);
        for(int attempt=0;attempt<256;attempt++){
            int i=random.nextInt(cells.length);
            if(parts[i]>=0&&find(i)==component)return pos(i);
        }
        return null;
    }
    /** A floor near the sample point, avoiding death zones. Null outside the grid. */
    BlockPos ground(ArenaMap arena,BotEntity bot,Vec3 sample){
        int i=snap(sample);if(i<0)return null;
        var pos=pos(i);return BotMovement.danger(arena,bot,Vec3.atBottomCenterOf(pos))?null:pos;
    }
    /** Same connected part, counting pads, jumps and switchable doors; one-way drops can still make this optimistic. */
    boolean reachable(Vec3 from,Vec3 to){
        int a=snap(from),b=snap(to);
        return a>=0&&b>=0&&parts[a]>=0&&parts[b]>=0&&find(a)==find(b);
    }
    record Node(Vec3 pos,int kind,BlockPos cell,boolean belt) { }
    /** A* from the bot to the goal, honouring live gates and shutters; null when none (or out of budget, see {@link #searchSpent}). */
    List<Node> route(BotEntity bot,Vec3 goal,Set<Long> blocked){
        if(searches<=0)return null;
        int start=snap(bot.position()),end=snap(goal);
        if(start<0||end<0||parts[start]<0||parts[end]<0||find(start)!=find(end))return null;
        searches--;
        var level=bot.level();
        int ex=x0+end%w,ey=y0+end/(w*d),ez=z0+(end/w)%d;
        var g=new HashMap<Integer,Float>();var from=new HashMap<Integer,Long>();
        var open=new PriorityQueue<long[]>(Comparator.comparingDouble(e->Double.longBitsToDouble(e[0])));
        g.put(start,0f);open.add(new long[]{Double.doubleToLongBits(0),start});
        int expanded=0;
        while(!open.isEmpty()&&expanded++<EXPANSIONS){
            var top=open.poll();int i=(int)top[1];
            if(i==end)return path(from,start,end,level);
            float base=g.get(i);int x=x0+i%w,y=y0+i/(w*d),z=z0+(i/w)%d;
            if(Double.longBitsToDouble(top[0])>base+(Math.abs(ex-x)+Math.abs(ez-z)+Math.abs(ey-y)*.5)*1.2+1e-3)continue; // stale entry
            moves(x,y,z,level,bot,blocked,(nx,ny,nz,cost,kind)->{
                int n=index(nx,ny,nz);
                // UXOs re-arm during the match, so they are checked live rather than baked into the grid.
                float uxo=BotMovement.uxoCost(level,nx,ny,nz);if(uxo==Float.POSITIVE_INFINITY)return;
                float extra=uxo+cost+BotManager.routeCost(bot,nx,ny,nz)+(BotManager.avoid(bot,nx,ny,nz)?8:0);
                float score=base+extra;var known=g.get(n);
                if(known!=null&&known<=score)return;
                g.put(n,score);from.put(n,((long)kind<<32)|(i&0xffffffffL));
                double f=score+(Math.abs(ex-nx)+Math.abs(ez-nz)+Math.abs(ey-ny)*.5)*1.2;
                open.add(new long[]{Double.doubleToLongBits(f),n});
            });
        }
        return null;
    }
    private List<Node> path(Map<Integer,Long> from,int start,int end,Level level){
        var nodes=new ArrayList<Node>();
        for(int i=end;i!=start;){
            long link=from.get(i);int kind=(int)(link>>>32);var pos=pos(i);
            // A door move names the shutter tile the bot walks through (feet or head height).
            BlockPos cell=kind!=DOOR?null:level.getBlockState(pos).getBlock() instanceof ShutterBlock?pos:pos.above();
            nodes.add(new Node(point(i),kind,cell,onBelt(i)));i=(int)link;
        }
        nodes.add(new Node(point(start),WALK,null,onBelt(start)));
        Collections.reverse(nodes);return nodes;
    }
    /** Lever and button positions within reach of a shutter tile. */
    List<BlockPos> switchesNear(BlockPos shutter){
        return switches.stream().filter(s->Math.abs(s.getX()-shutter.getX())<=SWITCH_RANGE&&Math.abs(s.getZ()-shutter.getZ())<=SWITCH_RANGE&&Math.abs(s.getY()-shutter.getY())<=4)
                .sorted(Comparator.comparingDouble(s->s.distSqr(shutter))).toList();
    }
    /** Stand cells from which a bot's hand reaches a switch. */
    List<BlockPos> standsNear(BlockPos target){
        var list=new ArrayList<BlockPos>();
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=-3;dy<=1;dy++){
            int x=target.getX()+dx,y=target.getY()+dy,z=target.getZ()+dz;
            if(!inside(x,y,z)||stand(x,y,z,null,null,null)!=0)continue;
            if(new Vec3(x+.5,y+1.62,z+.5).distanceTo(Vec3.atCenterOf(target))<=3.2)list.add(new BlockPos(x,y,z));
        }
        list.sort(Comparator.comparingDouble(p->p.distSqr(target)));
        return list;
    }
}
