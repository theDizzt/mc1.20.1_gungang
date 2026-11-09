package dev.gungang.acoustics.server;

import java.util.List;
import java.util.Map;

/**
 * {@code takenPrimary}/{@code takenSecondary}: weapon id to the teammate (or bot) already holding it, when the game
 * options forbid duplicates in the team; empty otherwise.
 */
public record EquipmentSnapshot(boolean allowed, boolean show, String team, String primary, String secondary,
                                int preparationSeconds, Loadouts catalog, boolean spawnHint, List<PlayerRecord.Preset> presets, int defaultPreset,
                                Map<String,String> takenPrimary, Map<String,String> takenSecondary) {
    public EquipmentSnapshot {
        presets = presets == null ? List.of() : presets;
        takenPrimary = takenPrimary == null ? Map.of() : takenPrimary;
        takenSecondary = takenSecondary == null ? Map.of() : takenSecondary;
    }
    public EquipmentSnapshot(boolean allowed,boolean show,String team,String primary,String secondary,int preparationSeconds,Loadouts catalog,boolean spawnHint,List<PlayerRecord.Preset> presets,int defaultPreset) {
        this(allowed,show,team,primary,secondary,preparationSeconds,catalog,spawnHint,presets,defaultPreset,Map.of(),Map.of());
    }
    public EquipmentSnapshot(boolean allowed,boolean show,String team,String primary,String secondary,int preparationSeconds,Loadouts catalog,boolean spawnHint) {
        this(allowed,show,team,primary,secondary,preparationSeconds,catalog,spawnHint,List.of(),0);
    }
    public EquipmentSnapshot(boolean allowed,boolean show,String team,String primary,String secondary,int preparationSeconds,Loadouts catalog) {
        this(allowed,show,team,primary,secondary,preparationSeconds,catalog,false);
    }
    public EquipmentSnapshot withCatalog(Loadouts catalog) {
        return new EquipmentSnapshot(allowed,show,team,primary,secondary,preparationSeconds,catalog,spawnHint,presets,defaultPreset,takenPrimary,takenSecondary);
    }
    /** Who already holds this weapon in the team, or null when it is free. */
    public String takenBy(boolean primaryGroup, String id) { return (primaryGroup ? takenPrimary : takenSecondary).get(id); }
}
