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
        public double centerX(){return ((double)minX+maxX+1)/2;}
        public double centerZ(){return ((double)minZ+maxZ+1)/2;}
        public Point center(){return new Point((int)Math.floor(((double)minX+maxX)/2),minY+1,(int)Math.floor(((double)minZ+maxZ)/2));}
    }
    public record Point(int x,int y,int z) {
        public void validate(){if(Math.abs((long)x)>29999000||Math.abs((long)z)>29999000||y< -2048||y>2048)throw new IllegalArgumentException("Invalid bomb point");}
    }
    public int winsRequired(){return rounds/2+1;}
    public void validate(){
        if(areaA!=null)areaA.validate();if(areaB!=null)areaB.validate();
        if(rounds<1||rounds>99||rounds%2==0||swapEveryRounds<1||swapEveryRounds>rounds||preparationSeconds<1||preparationSeconds>600
                ||attackSpawn==null||defenseSpawn==null||attackArea==null||defenseArea==null||pointA==null||pointB==null||pointA.equals(pointB))
            throw new IllegalArgumentException("Bomb mode requires odd rounds, spawn areas and distinct A/B points");
        attackSpawn.validate();defenseSpawn.validate();attackArea.validate(attackSpawn);defenseArea.validate(defenseSpawn);pointA.validate();pointB.validate();
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
