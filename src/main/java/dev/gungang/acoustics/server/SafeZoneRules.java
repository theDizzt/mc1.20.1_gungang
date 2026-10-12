package dev.gungang.acoustics.server;

import java.util.List;

/** Inclusive block-coordinate corners, independent of corner order. */
public record SafeZoneRules(List<Zone> zones) {
    public record Point(int x,int y,int z) { }
    public record Zone(String name,String dimension,Boolean enabled,Point from,Point to) {
        public Zone {
            enabled=enabled==null?true:enabled;
            if(name==null||name.isBlank()||name.length()>80||dimension==null||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||from==null||to==null)
                throw new IllegalArgumentException("Invalid safe zone");
            for(var p:List.of(from,to))if(Math.abs((long)p.x())>30_000_000||Math.abs((long)p.z())>30_000_000||p.y() < -2048||p.y()>2048)throw new IllegalArgumentException("Invalid safe zone coordinates: "+name);
        }
        public boolean contains(String world,double x,double y,double z){
            return enabled&&dimension.equals(world)&&x>=Math.min(from.x(),to.x())&&x<Math.max(from.x(),to.x())+1d
                    &&y>=Math.min(from.y(),to.y())&&y<Math.max(from.y(),to.y())+1d&&z>=Math.min(from.z(),to.z())&&z<Math.max(from.z(),to.z())+1d;
        }
    }
    public SafeZoneRules {
        if(zones==null||zones.size()>128||zones.stream().anyMatch(java.util.Objects::isNull))throw new IllegalArgumentException("Configure at most 128 safe zones");
        zones=List.copyOf(zones);
    }
    public static SafeZoneRules empty(){return new SafeZoneRules(List.of());}
    public boolean contains(String world,double x,double y,double z){return zones.stream().anyMatch(zone->zone.contains(world,x,y,z));}
}
