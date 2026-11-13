package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.Loadouts;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Weapon list order of the loadout screen, kept free of screen classes so it can be tested. */
public final class LoadoutScreenOrder {
    /**
     * Weapon number order: the leading digits of the id (01, 28, 74, 96_infrared-scanner ...) ascending; ids without
     * a number follow alphabetically. Equal numbers keep the server's order. Weapons are grouped by category in the
     * order of each group's lowest number, and the 히든 group (random-only weapons) always comes last.
     */
    public static List<Loadouts.Weapon> order(List<Loadouts.Weapon> source){
        var order=java.util.Comparator.comparingLong((Loadouts.Weapon w)->number(w.id())).thenComparing(w->number(w.id())==Long.MAX_VALUE?w.id():"");
        var groups=new LinkedHashMap<String,List<Loadouts.Weapon>>();
        for(var weapon:source.stream().sorted(order).toList())groups.computeIfAbsent(weapon.category(),k->new ArrayList<>()).add(weapon);
        var hidden=groups.remove(Loadouts.Weapon.HIDDEN);
        if(hidden!=null)groups.put(Loadouts.Weapon.HIDDEN,hidden);
        return groups.values().stream().flatMap(List::stream).toList();
    }
    public static long number(String id){
        int end=0;while(end<id.length()&&end<18&&Character.isDigit(id.charAt(end)))end++;
        return end==0?Long.MAX_VALUE:Long.parseLong(id.substring(0,end));
    }
    private LoadoutScreenOrder(){}
}
