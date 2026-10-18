package dev.gungang.acoustics.server;

import java.util.List;

public record EquipmentSnapshot(boolean allowed, boolean show, String team, String primary, String secondary,
                                int preparationSeconds, Loadouts catalog, boolean spawnHint, List<PlayerRecord.Preset> presets, int defaultPreset) {
    public EquipmentSnapshot { presets = presets == null ? List.of() : presets; }
    public EquipmentSnapshot(boolean allowed,boolean show,String team,String primary,String secondary,int preparationSeconds,Loadouts catalog,boolean spawnHint) {
        this(allowed,show,team,primary,secondary,preparationSeconds,catalog,spawnHint,List.of(),0);
    }
    public EquipmentSnapshot(boolean allowed,boolean show,String team,String primary,String secondary,int preparationSeconds,Loadouts catalog) {
        this(allowed,show,team,primary,secondary,preparationSeconds,catalog,false);
    }
    public EquipmentSnapshot withCatalog(Loadouts catalog) {
        return new EquipmentSnapshot(allowed,show,team,primary,secondary,preparationSeconds,catalog,spawnHint,presets,defaultPreset);
    }
}
