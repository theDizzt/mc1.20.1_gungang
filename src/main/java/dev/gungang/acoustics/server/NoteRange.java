package dev.gungang.acoustics.server;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The vanilla note block's wider range: an {@code octave} property (0-4, 2 is vanilla's two octaves) shifts the vanilla
 * {@code note} (0-24) by whole octaves, so the block plays F#1 to F#7 (73 semitones, pitch 0.125 to 8). A semitone is
 * counted from vanilla note 0 (F#3): -24 is F#1, 0 is F#3, 24 is F#5, 48 is F#7. Inside vanilla's range the octave stays
 * 2, so blocks tuned in vanilla keep their state and sound.
 */
public final class NoteRange {
    public static final IntegerProperty OCTAVE=IntegerProperty.create("octave",0,4);
    public static final int VANILLA_OCTAVE=2,LOWEST=-24,HIGHEST=48;
    private static final String[] NAMES={"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"};

    public static int semitone(BlockState state){
        int octave=state.hasProperty(OCTAVE)?state.getValue(OCTAVE):VANILLA_OCTAVE;
        return (octave-VANILLA_OCTAVE)*12+state.getValue(BlockStateProperties.NOTE);
    }
    /** The state playing {@code semitone} (clamped to the range), keeping vanilla's octave whenever it fits. */
    public static BlockState withSemitone(BlockState state,int semitone){
        int s=Math.max(LOWEST,Math.min(HIGHEST,semitone));
        int octave=s>=0&&s<=24?2:s<0?(s>=-12?1:0):(s<=36?3:4);
        return state.setValue(OCTAVE,octave).setValue(BlockStateProperties.NOTE,s-(octave-VANILLA_OCTAVE)*12);
    }
    /** One semitone up or down, wrapping from the top to the bottom like vanilla's 24 to 0. */
    public static int step(int semitone,int delta){
        int span=HIGHEST-LOWEST+1;
        return LOWEST+Math.floorMod(semitone+delta-LOWEST,span);
    }
    /** Pitch multiplier: 2^((s-12)/12), as vanilla's getPitchFromNote over the wider range. */
    public static float pitch(int semitone){return (float)Math.pow(2,(semitone-12)/12d);}
    /** Scientific pitch name, e.g. F#3 for vanilla note 0. */
    public static String name(int semitone){
        int fromC3=semitone+6;
        return NAMES[Math.floorMod(fromC3,12)]+(3+Math.floorDiv(fromC3,12));
    }
    private NoteRange(){}
}
