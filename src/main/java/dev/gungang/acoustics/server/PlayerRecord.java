package dev.gungang.acoustics.server;

public record PlayerRecord(String id,String name,long experience,long matches,long kills,long deaths,double damage,long playSeconds,long wins) {
    public PlayerRecord {
        java.util.UUID.fromString(id);
        if(name==null||name.isBlank()||name.length()>64||experience<0||matches<0||kills<0||deaths<0||playSeconds<0||wins<0||!Double.isFinite(damage)||damage<0)
            throw new IllegalArgumentException("Invalid player record");
    }
    public static PlayerRecord empty(String id,String name) { return new PlayerRecord(id,name,0,0,0,0,0,0,0); }
    public PlayerRecord named(String name) {return new PlayerRecord(id,name,experience,matches,kills,deaths,damage,playSeconds,wins);}
    public PlayerRecord match(MatchSnapshot.Row row,int xp,boolean won) {
        return new PlayerRecord(id,row.name(),Math.addExact(experience,xp),Math.addExact(matches,1),Math.addExact(kills,row.kills()),
                Math.addExact(deaths,row.deaths()),damage+row.damage(),Math.addExact(playSeconds,row.playSeconds()),Math.addExact(wins,won?1:0));
    }
    public PlayerRecord set(String field,String value) {
        return new PlayerRecord(id,name,field.equals("xp")?Long.parseLong(value):experience,field.equals("matches")?Long.parseLong(value):matches,
                field.equals("kills")?Long.parseLong(value):kills,field.equals("deaths")?Long.parseLong(value):deaths,
                field.equals("damage")?Double.parseDouble(value):damage,field.equals("playSeconds")?Long.parseLong(value):playSeconds,
                field.equals("wins")?Long.parseLong(value):wins);
    }
}
