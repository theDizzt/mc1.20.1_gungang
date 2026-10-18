package dev.gungang.acoustics.server;

/** Map-owned round settings; item-owned use/fuse times live in sub_weapons/98 and 99. */
public record BombRules(Integer rounds,Integer swapEveryRounds,Integer preparationSeconds,
                        ArenaMap.Spawn attackSpawn,ArenaMap.Area attackArea,
                        ArenaMap.Spawn defenseSpawn,ArenaMap.Area defenseArea,Point pointA,Point pointB,Site areaA,Site areaB) {
    public BombRules(Integer rounds,Integer swapEveryRounds,Integer preparationSeconds,ArenaMap.Spawn attackSpawn,ArenaMap.Area attackArea,ArenaMap.Spawn defenseSpawn,ArenaMap.Area defenseArea,Point pointA,Point pointB){this(rounds,swapEveryRounds,preparationSeconds,attackSpawn,attackArea,defenseSpawn,defenseArea,pointA,pointB,null,null);}
    public BombRules {
        rounds=rounds==null?5:rounds;
        swapEveryRounds=swapEveryRounds==null?1:swapEveryRounds;
        preparationSeconds=preparationSeconds==null?30:preparationSeconds;
        if(areaA!=null)pointA=areaA.center();
        if(areaB!=null)pointB=areaB.center();
    }
    /** Inclusive floor-block bounds; the bomb is placed immediately above the selected floor. */
    public record Site(int minX,int minY,int minZ,int maxX,int maxY,int maxZ){
        public void validate(){new MapPreviewArea(minX,minY,minZ,maxX,maxY,maxZ).validate();}
        public boolean contains(int x,int y,int z){return x>=minX&&x<=maxX&&y>=minY&&y<=maxY&&z>=minZ&&z<=maxZ;}
        /** Plantable floor: the saved box, extended {@link #PLANT_BELOW} down and {@link #PLANT_ABOVE} up for sunken floors, crates and raised ledges. */
        public boolean plantable(int x,int y,int z){return contains(x,Math.max(minY,Math.min(y,maxY)),z)&&y>=minY-PLANT_BELOW&&y<=maxY+PLANT_ABOVE;}
        public double centerX(){return ((double)minX+maxX+1)/2;}
        public double centerZ(){return ((double)minZ+maxZ+1)/2;}
        public Point center(){return new Point((int)Math.floor(((double)minX+maxX)/2),minY+1,(int)Math.floor(((double)minZ+maxZ)/2));}
    }
    public record Point(int x,int y,int z) {
        public void validate(){if(Math.abs((long)x)>29999000||Math.abs((long)z)>29999000||y< -2048||y>2048)throw new IllegalArgumentException("Invalid bomb point");}
    }
    public static final int PLANT_ABOVE=5,PLANT_BELOW=1;
    /** Site ("A"/"B") that accepts a bomb on top of this floor block, or null. Areas win over legacy single points. */
    public static String siteAt(Site areaA,Site areaB,Point pointA,Point pointB,int x,int y,int z){
        for(String site:java.util.List.of("A","B")){
            var area=site.equals("A")?areaA:areaB;var point=site.equals("A")?pointA:pointB;
            if(area!=null?area.plantable(x,y,z):point!=null&&point.x()==x&&point.y()==y+1&&point.z()==z)return site;
        }
        return null;
    }
    /** Bomb cell for this hit and why it is refused ("" = placeable); shared by the server check and the client preview. */
    public record Placement(net.minecraft.core.BlockPos cell,String reason){
        public boolean ok(){return reason.isEmpty();}
    }
    public static Placement placement(net.minecraft.world.level.BlockGetter level,net.minecraft.world.phys.BlockHitResult hit){
        var pos=hit.getBlockPos();net.minecraft.core.BlockPos cell;
        if(wet(level,pos))return new Placement(pos,WET); // puddle block lying on the floor
        if(open(level.getBlockState(pos)))cell=pos; // aiming at grass or flowers lying on the floor
        else if(hit.getDirection()!=net.minecraft.core.Direction.UP)return new Placement(pos.above(),"블록 윗면을 조준하세요");
        else cell=pos.above();
        if(wet(level,cell)||wet(level,cell.below()))return new Placement(cell,WET);
        if(!open(level.getBlockState(cell)))return new Placement(cell,"위 공간이 막혀 있습니다");
        if(!floor(level,cell.below()))return new Placement(cell,"설치할 수 없는 바닥입니다");
        return new Placement(cell,"");
    }
    static final String WET="물·웅덩이 위에는 설치할 수 없습니다";
    /** Water/acid (also waterlogged), puddle blocks, or a puddle overlay lying on this block. */
    static boolean wet(net.minecraft.world.level.BlockGetter level,net.minecraft.core.BlockPos pos){
        var state=level.getBlockState(pos);
        if(!state.getFluidState().isEmpty()||state.getBlock() instanceof PuddleBlock)return true;
        return level instanceof net.minecraft.world.level.Level world&&!world.getEntitiesOfClass(PuddleOverlay.class,new net.minecraft.world.phys.AABB(pos)).isEmpty();
    }
    /** Bomb cell for a player standing on this floor block (planting uses the feet, not the crosshair). */
    public static Placement standing(net.minecraft.world.level.BlockGetter level,net.minecraft.core.BlockPos floor){
        return placement(level,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(floor),net.minecraft.core.Direction.UP,floor,false));
    }
    /** Air or dry replaceable plants; liquids never count as open space. */
    public static boolean open(net.minecraft.world.level.block.state.BlockState state){
        return state.isAir()||state.canBeReplaced()&&state.getFluidState().isEmpty();
    }
    /** Any dry block with a wide, at most full-height top: slabs, stairs and carpets pass; liquids, fences, walls, panes and small decorations do not. */
    public static boolean floor(net.minecraft.world.level.BlockGetter level,net.minecraft.core.BlockPos pos){
        var state=level.getBlockState(pos);if(!state.getFluidState().isEmpty())return false;
        var shape=state.getCollisionShape(level,pos);if(shape.isEmpty())return false;
        var box=shape.bounds();return box.maxY<=1&&box.getXsize()>=.75&&box.getZsize()>=.75;
    }
    public int winsRequired(){return rounds/2+1;}
    public void validate(){
        if(areaA!=null)areaA.validate();if(areaB!=null)areaB.validate();
        if(rounds<1||rounds>99||swapEveryRounds<1||swapEveryRounds>rounds||preparationSeconds<1||preparationSeconds>600
                ||attackSpawn==null||defenseSpawn==null||attackArea==null||defenseArea==null||pointA==null||pointB==null||pointA.equals(pointB))
            throw new IllegalArgumentException("Bomb mode requires 1-99 rounds, spawn areas and distinct A/B points");
        attackSpawn.validate();defenseSpawn.validate();attackArea.validateRoom(attackSpawn);defenseArea.validateRoom(defenseSpawn);pointA.validate();pointB.validate();
    }
    public static BombRules defaults(){
        return new BombRules(5,1,30,new ArenaMap.Spawn(.5,65,.5,0,0),new ArenaMap.Area(-5,64,-5,5,72,5),
                new ArenaMap.Spawn(40.5,65,.5,180,0),new ArenaMap.Area(35,64,-5,45,72,5),new Point(20,65,-10),new Point(20,65,10));
    }
    public static ArenaMap example(){
        var r=defaults();
        return new ArenaMap("bomb","폭탄전","5라운드 · 3선승 · 매 라운드 공수 교대",false,210,"adventure","minecraft:overworld",
                r.attackSpawn,r.defenseSpawn,new ArenaMap.Spawn(20.5,80,.5,0,30),r.attackArea,r.defenseArea,null,
                java.util.List.of(),java.util.List.of(),java.util.List.of(),null,"","clear","day",r);
    }
}
