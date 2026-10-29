package dev.gungang.acoustics.client;

import com.google.gson.JsonParser;
import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = GungangAcoustics.ID, value = Dist.CLIENT)
public final class LobbyClient {
    public static final KeyMapping LOADOUT = new KeyMapping("key.gungang.loadout", GLFW.GLFW_KEY_E, "key.categories.gungang");
    public static final KeyMapping LOBBY = new KeyMapping("key.gungang.lobby", GLFW.GLFW_KEY_L, "key.categories.gungang");
    public static EquipmentSnapshot equipment;
    public static TeamOptions options=TeamOptions.defaults();
    private static long requestedUntil;
    private static boolean hintVisible;
    public static void acoustics(String json) { AcousticPolicy.receive(JsonParser.parseString(json).getAsJsonObject()); }
    public static void receive(String json) {
        var state = ServerFiles.GSON.fromJson(json, LobbySnapshot.class);
        var previousOptions=options;
        options=state.options()==null?TeamOptions.defaults():state.options();
        Minecraft mc = Minecraft.getInstance();
        if(mc.level!=null&&(previousOptions.color("ALPHA")!=options.color("ALPHA")||previousOptions.color("BRAVO")!=options.color("BRAVO")))mc.levelRenderer.allChanged();
        if (!state.enabled() || !state.phase().equals("LOBBY")) {
            if (mc.screen instanceof LobbyScreen||mc.screen instanceof GameOptionsScreen) mc.setScreen(null);
            return;
        }
        if (mc.screen instanceof GameOptionsScreen screen) screen.update(state);
        else if (mc.screen instanceof LobbyScreen screen) screen.update(state);
        else if (state.show()) mc.setScreen(new LobbyScreen(state));
    }
    public static void equipment(String json) {
        var next = ServerFiles.GSON.fromJson(json, EquipmentSnapshot.class);
        if (next.catalog() == null && equipment != null) next = next.withCatalog(equipment.catalog());
        equipment = next;
        Minecraft mc = Minecraft.getInstance();
        if (!next.allowed()) { requestedUntil = 0; if (mc.screen instanceof LoadoutScreen) mc.setScreen(null); }
        else if (mc.screen instanceof LoadoutScreen screen) screen.update(next);
        else if (next.show() && next.catalog() != null) { requestedUntil = 0; mc.setScreen(new LoadoutScreen(next)); }
    }
    public static void action(String action, String value, String target) { GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.Action(action, value, target)); }
    /** Reserve the configured key before vanilla opens advancements; let the server decide lobby availability. */
    public static boolean lobbyKey(int key,int scan,int action){return lobbyInput(LOBBY.isActiveAndMatches(com.mojang.blaze3d.platform.InputConstants.getKey(key,scan)),action);}
    public static boolean lobbyMouse(int button,int action){return lobbyInput(LOBBY.isActiveAndMatches(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(button)),action);}
    private static boolean lobbyInput(boolean matches,int input){
        var mc=Minecraft.getInstance();
        if(!matches||mc.player==null||mc.screen!=null||mc.getConnection()==null
                ||!GungangNetwork.CHANNEL.isRemotePresent(mc.getConnection().getConnection()))return false;
        if(input==GLFW.GLFW_PRESS)action("lobby","","");
        return true;
    }
    @SubscribeEvent public static void key(InputEvent.Key event) {
        var mc = Minecraft.getInstance();
        if (event.getAction() == GLFW.GLFW_PRESS && LOADOUT.matches(event.getKey(), event.getScanCode())
                && equipment != null && equipment.allowed() && mc.screen == null) {
            requestedUntil = System.nanoTime() + 1_000_000_000L;
            if (mc.options.keyInventory.matches(event.getKey(), event.getScanCode()))
                while (mc.options.keyInventory.consumeClick()) { /* Reserve this press for loadout selection. */ }
            action("equipment", "", "");
        }
    }
    @SubscribeEvent public static void opening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof InventoryScreen && equipment != null && equipment.allowed()
                && requestedUntil > System.nanoTime()) event.setCanceled(true);
    }
    @SubscribeEvent public static void hint(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        boolean safe=SafeZones.contains(mc.player);
        boolean show=safe||mc.player!=null&&mc.screen==null&&equipment!=null&&equipment.spawnHint();
        if(safe)mc.gui.setOverlayMessage(Component.literal("비무장 지대"),false);
        else if(show)mc.gui.setOverlayMessage(Component.translatable("gungang.loadout.hint",LOADOUT.getTranslatedKeyMessage()),false);
        else if(hintVisible)mc.gui.setOverlayMessage(Component.empty(),false);
        hintVisible=show;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { SafeZones.clearClient(); PreviewCache.clear(); AcousticPolicy.clear(); equipment = null; requestedUntil = 0; hintVisible=false; options=TeamOptions.defaults(); }
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event) { AcousticPolicy.clear(); equipment = null; }
    private LobbyClient() { }
}
