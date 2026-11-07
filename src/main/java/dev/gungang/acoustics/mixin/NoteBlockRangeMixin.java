package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.NoteRange;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Widens the vanilla note block to six octaves (F#1 to F#7, see NoteRange): an {@code octave} state, right click one
 * semitone up and sneaking right click (empty hand) one down with the note name on the action bar, and the played pitch
 * over the whole range. The client lets these sounds past its 0.5-2 pitch clamp (SoundEngineMixin).
 */
@Mixin(NoteBlock.class)
public abstract class NoteBlockRangeMixin extends Block {
    private NoteBlockRangeMixin(Properties properties){super(properties);}
    @Shadow private void playNote(Entity entity,BlockState state,Level level,BlockPos pos){throw new AssertionError();}

    @Inject(method="createBlockStateDefinition",at=@At("TAIL"))
    private void gungang$octave(StateDefinition.Builder<Block,BlockState> builder,CallbackInfo ci){builder.add(NoteRange.OCTAVE);}

    /** The state definition's first value would be octave 0; new and unsaved blocks start in vanilla's octave. */
    @Inject(method="<init>",at=@At("TAIL"))
    private void gungang$defaultOctave(Properties properties,CallbackInfo ci){
        registerDefaultState(defaultBlockState().setValue(NoteRange.OCTAVE,NoteRange.VANILLA_OCTAVE));
    }

    @Inject(method="use",at=@At("HEAD"),cancellable=true)
    private void gungang$tune(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit,CallbackInfoReturnable<InteractionResult> cir){
        // A mob head placed on top keeps vanilla's handling.
        if(player.getItemInHand(hand).is(ItemTags.NOTE_BLOCK_TOP_INSTRUMENTS)&&hit.getDirection()==Direction.UP)return;
        if(level.isClientSide){cir.setReturnValue(InteractionResult.SUCCESS);return;}
        int next=NoteRange.step(NoteRange.semitone(state),player.isShiftKeyDown()?-1:1);
        BlockState tuned=NoteRange.withSemitone(state,next);
        int note=net.minecraftforge.common.ForgeHooks.onNoteChange(level,pos,state,state.getValue(BlockStateProperties.NOTE),tuned.getValue(BlockStateProperties.NOTE));
        if(note==-1){cir.setReturnValue(InteractionResult.FAIL);return;}
        tuned=tuned.setValue(BlockStateProperties.NOTE,note);
        level.setBlock(pos,tuned,3);
        playNote(player,tuned,level,pos);
        player.awardStat(Stats.TUNE_NOTEBLOCK);
        if(tuned.getValue(NoteBlock.INSTRUMENT).isTunable())player.displayClientMessage(Component.literal(NoteRange.name(NoteRange.semitone(tuned))),true);
        cir.setReturnValue(InteractionResult.CONSUME);
    }

    @Redirect(method="triggerEvent",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/NoteBlock;getPitchFromNote(I)F"))
    private float gungang$pitch(int note,BlockState state,Level level,BlockPos pos,int id,int param){
        return NoteRange.pitch(NoteRange.semitone(state));
    }
}
