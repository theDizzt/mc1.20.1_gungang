package dev.gungang.acoustics.server;

import java.util.*;

/** Server-owned roster numbering and one replaceable point per teammate. No world dependencies. */
public final class TeamPointState {
    public record Member(UUID id,int number) { }
    public record Point(UUID owner,int number,String dimension,double x,double y,double z,long sequence) { }
    /**
     * A living teammate's health, so the team HUD can show it beyond the client's entity tracking range, with their
     * chosen primary/secondary weapon ids and the held item (SNBT, volatile ammo tags removed; "" when empty).
     */
    public record Vital(UUID id,float health,float maximum,float absorption,String primary,String secondary,String held) {
        public Vital(UUID id,float health,float maximum,float absorption){this(id,health,maximum,absorption,"","","");}
    }
    public record View(String team,int color,List<Member> members,List<Point> points,List<Vital> vitals) {
        public View(String team,int color,List<Member> members,List<Point> points){this(team,color,members,points,List.of());}
        public View withVitals(List<Vital> list){return new View(team,color,members,points,List.copyOf(list));}
    }
    private record Mark(String team,String dimension,double x,double y,double z,long sequence) { }
    private final LinkedHashMap<UUID,String> roster=new LinkedHashMap<>();
    private final Map<UUID,Mark> points=new HashMap<>();
    private long sequence;
    public void roster(Map<UUID,String> current) {
        roster.entrySet().removeIf(e->!e.getValue().equals(current.get(e.getKey())));
        current.forEach((id,team)->{if(validTeam(team))roster.putIfAbsent(id,team);});
        points.entrySet().removeIf(e->!e.getValue().team().equals(roster.get(e.getKey())));
    }
    public static boolean validTeam(String team){return "ALPHA".equals(team)||"BRAVO".equals(team);}
    public boolean mark(UUID owner,String dimension,double x,double y,double z) {
        String team=roster.get(owner);
        if(!validTeam(team)||dimension==null||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return false;
        points.put(owner,new Mark(team,dimension,x,y,z,++sequence));return true;
    }
    public View view(String team,int color) {
        if(!validTeam(team))return new View("",color,List.of(),List.of());
        var members=new ArrayList<Member>();var marks=new ArrayList<Point>();
        for(var e:roster.entrySet())if(e.getValue().equals(team)) {
            int number=members.size()+1;members.add(new Member(e.getKey(),number));
            var mark=points.get(e.getKey());
            if(mark!=null)marks.add(new Point(e.getKey(),number,mark.dimension(),mark.x(),mark.y(),mark.z(),mark.sequence()));
        }
        return new View(team,color,List.copyOf(members),List.copyOf(marks));
    }
    public void clear(){points.clear();}
    public void reset(){points.clear();roster.clear();}
}
