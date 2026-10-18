package dev.gungang.acoustics.server;

import java.util.*;

public record PlayerRecord(String id,String name,long experience,long matches,long kills,long deaths,double damage,long playSeconds,long wins,
                           List<Preset> presets,int defaultPreset) {
    public static final int PRESETS=5;
    /** Saved primary/secondary combination, referenced by weapon id. */
    public record Preset(int slot,String name,String primary,String secondary) {
        public Preset {
            if(slot<1||slot>PRESETS)throw new IllegalArgumentException("프리셋 번호는 1~"+PRESETS+" 사이여야 합니다.");
            name=name==null||name.isBlank()?"프리셋 "+slot:name.strip();
            if(name.length()>20||name.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("프리셋 이름은 20자 이하로 입력하세요.");
            if(primary==null||!primary.matches("[a-z0-9_-]{1,48}")||secondary==null||!secondary.matches("[a-z0-9_-]{1,48}"))
                throw new IllegalArgumentException("Invalid preset weapon id");
        }
    }
    public PlayerRecord {
        java.util.UUID.fromString(id);
        if(name==null||name.isBlank()||name.length()>64||experience<0||matches<0||kills<0||deaths<0||playSeconds<0||wins<0||!Double.isFinite(damage)||damage<0)
            throw new IllegalArgumentException("Invalid player record");
        // Records written before presets existed have neither field.
        presets=presets==null?List.of():presets.stream().sorted(Comparator.comparingInt(Preset::slot)).toList();
        for(int i=0;i<presets.size();i++)if(presets.get(i)==null||i>0&&presets.get(i).slot()==presets.get(i-1).slot())throw new IllegalArgumentException("Invalid player presets");
        if(defaultPreset<0||defaultPreset>PRESETS)throw new IllegalArgumentException("Invalid default preset");
        int chosen=defaultPreset;
        if(presets.stream().noneMatch(p->p.slot()==chosen))defaultPreset=0;
    }
    public PlayerRecord(String id,String name,long experience,long matches,long kills,long deaths,double damage,long playSeconds,long wins) {
        this(id,name,experience,matches,kills,deaths,damage,playSeconds,wins,List.of(),0);
    }
    public static PlayerRecord empty(String id,String name) { return new PlayerRecord(id,name,0,0,0,0,0,0,0); }
    public PlayerRecord named(String name) {return new PlayerRecord(id,name,experience,matches,kills,deaths,damage,playSeconds,wins,presets,defaultPreset);}
    public PlayerRecord match(MatchSnapshot.Row row,int xp,boolean won) {
        return new PlayerRecord(id,row.name(),Math.addExact(experience,xp),Math.addExact(matches,1),Math.addExact(kills,row.kills()),
                Math.addExact(deaths,row.deaths()),damage+row.damage(),Math.addExact(playSeconds,row.playSeconds()),Math.addExact(wins,won?1:0),presets,defaultPreset);
    }
    public PlayerRecord set(String field,String value) {
        return new PlayerRecord(id,name,field.equals("xp")?Long.parseLong(value):experience,field.equals("matches")?Long.parseLong(value):matches,
                field.equals("kills")?Long.parseLong(value):kills,field.equals("deaths")?Long.parseLong(value):deaths,
                field.equals("damage")?Double.parseDouble(value):damage,field.equals("playSeconds")?Long.parseLong(value):playSeconds,
                field.equals("wins")?Long.parseLong(value):wins,presets,defaultPreset);
    }
    public Preset preset(int slot) {return presets.stream().filter(p->p.slot()==slot).findFirst().orElse(null);}
    public Preset defaultLoadout() {return defaultPreset==0?null:preset(defaultPreset);}
    public PlayerRecord withPreset(Preset preset) {
        var next=new ArrayList<>(presets);next.removeIf(p->p.slot()==preset.slot());next.add(preset);
        return new PlayerRecord(id,name,experience,matches,kills,deaths,damage,playSeconds,wins,next,defaultPreset);
    }
    public PlayerRecord withoutPreset(int slot) {
        var next=new ArrayList<>(presets);next.removeIf(p->p.slot()==slot);
        return new PlayerRecord(id,name,experience,matches,kills,deaths,damage,playSeconds,wins,next,defaultPreset==slot?0:defaultPreset);
    }
    public PlayerRecord withDefault(int slot) {return new PlayerRecord(id,name,experience,matches,kills,deaths,damage,playSeconds,wins,presets,slot);}
}
