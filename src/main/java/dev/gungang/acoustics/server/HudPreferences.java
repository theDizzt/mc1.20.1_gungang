package dev.gungang.acoustics.server;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class HudPreferences {
    private static final String KEY="GunGangHudHidden";
    public static boolean enabled(Player p){return !p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(KEY);}
    public static void set(ServerPlayer p,boolean enabled){
        CompoundTag data=p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        data.putBoolean(KEY,!enabled);p.getPersistentData().put(Player.PERSISTED_NBT_TAG,data);sync(p);
    }
    private static void sync(ServerPlayer p){GungangNetwork.send(p,new GungangNetwork.Hud(enabled(p)));}
    public static void register(LiteralArgumentBuilder<CommandSourceStack> root){
        var hud=Commands.literal("hud").executes(c->{var p=c.getSource().getPlayerOrException();p.sendSystemMessage(Component.literal("총게임 HUD: "+(enabled(p)?"ON":"OFF")));return 1;});
        for(boolean value:new boolean[]{true,false})hud.then(Commands.literal(value?"on":"off").executes(c->{
            var p=c.getSource().getPlayerOrException();set(p,value);p.sendSystemMessage(Component.literal("총게임 HUD "+(value?"ON":"OFF")));return 1;
        }));
        root.then(hud);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p)sync(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){
        var data=e.getEntity().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        data.putBoolean(KEY,!enabled(e.getOriginal()));e.getEntity().getPersistentData().put(Player.PERSISTED_NBT_TAG,data);
    }
    private HudPreferences(){}
}
