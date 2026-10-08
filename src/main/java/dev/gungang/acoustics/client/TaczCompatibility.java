package dev.gungang.acoustics.client;

/** Optional integration without loading or bundling TaCZ classes. Subclasses cover tracking sounds/addons. */
public final class TaczCompatibility {
    private static final String GUN_SOUND = "com.tacz.guns.client.sound.GunSoundInstance";
    private static final ClassValue<Boolean> GUN_SOUND_TYPES = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            for (Class<?> parent = type; parent != null; parent = parent.getSuperclass()) {
                if (GUN_SOUND.equals(parent.getName())) return true;
            }
            return false;
        }
    };

    public static boolean managesOwnDistance(Object sound) { return GUN_SOUND_TYPES.get(sound.getClass()); }
    private TaczCompatibility() { }
}
