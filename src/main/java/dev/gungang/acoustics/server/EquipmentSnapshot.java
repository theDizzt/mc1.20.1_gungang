package dev.gungang.acoustics.server;

public record EquipmentSnapshot(boolean allowed, boolean show, String team, String primary, String secondary,
                                int preparationSeconds, Loadouts catalog, boolean spawnHint) {
    public EquipmentSnapshot(boolean allowed,boolean show,String team,String primary,String secondary,int preparationSeconds,Loadouts catalog) {
        this(allowed,show,team,primary,secondary,preparationSeconds,catalog,false);
    }
}
