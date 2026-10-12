package dev.gungang.acoustics.server;

import java.util.List;

public record XpTable(List<Long> cumulativeXp) {
    public XpTable {
        if(cumulativeXp==null||cumulativeXp.size()!=91||cumulativeXp.stream().anyMatch(n->n==null||n<0||n>1000000000000L)||cumulativeXp.get(0)!=0)
            throw new IllegalArgumentException("xp.json cumulativeXp must contain 91 cumulative XP values starting at 0");
        for(int i=1;i<91;i++)if(cumulativeXp.get(i)<=cumulativeXp.get(i-1))throw new IllegalArgumentException("Cumulative XP must strictly increase");
        cumulativeXp=List.copyOf(cumulativeXp);
    }
    public static XpTable defaults() { return new XpTable(java.util.stream.LongStream.of(
            0,81,201,360,558,795,1071,1386,1740,2133,2565,3089,3651,4253,4894,5573,6292,7050,7846,8682,
            9557,10557,11597,12675,13793,14949,16145,17379,18653,19965,21317,22851,24423,26035,27686,
            29376,31105,32873,34680,36525,38410,40571,42770,45008,47286,49602,51958,54352,56785,59258,
            61769,64710,67689,70708,73766,76862,79998,83172,86386,89639,92930,96905,100918,104971,
            109062,113193,117362,121571,125818,130105,134430,139857,145323,150828,156372,161955,
            167577,173238,178938,184677,190455,198025,205634,213282,220969,228695,236460,244264,
            252107,259989,267909).boxed().toList()); }
    public record Progress(int level,long experience,long current,long required) {
        public float fraction() {return level>=91?1:Math.max(0,Math.min(1,current/(float)Math.max(1,required)));}
    }
    public Progress progress(long experience) {
        if(experience<0)throw new IllegalArgumentException("XP cannot be negative");
        for(int i=0;i<90;i++) {
            long start=cumulativeXp.get(i),next=cumulativeXp.get(i+1);
            if(experience<next)return new Progress(i+1,experience,experience-start,next-start);
        }
        return new Progress(91,experience,0,0);
    }
}
