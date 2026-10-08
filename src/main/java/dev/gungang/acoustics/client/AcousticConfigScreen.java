package dev.gungang.acoustics.client;

import com.mojang.logging.LogUtils;
import dev.gungang.acoustics.AcousticConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

public final class AcousticConfigScreen extends Screen {
    private final Screen parent;
    private final List<Option> options = new ArrayList<>();
    private final List<EditBox> inputs = new ArrayList<>();
    private int page;
    private Button saveButton;
    private Component status = Component.empty();

    public AcousticConfigScreen(Screen parent) {
        super(text("title"));
        this.parent = parent;
        options.add(new Option(3, "shooterHud", AcousticConfig.SHOOTER_HUD, Boolean::parseBoolean, true));
        toggle("enabled", AcousticConfig.ENABLED);
        toggle("lowPass", AcousticConfig.LOW_PASS);
        toggle("diffraction", AcousticConfig.DIFFRACTION);
        decimal(0, "wallStrength", AcousticConfig.WALL_STRENGTH, 0, 4);
        decimal(0, "smoothingSeconds", AcousticConfig.SMOOTHING, 0.01, 1);
        decimal(1, "referenceDistance", AcousticConfig.REFERENCE_DISTANCE, 0.25, 16);
        decimal(1, "rolloff", AcousticConfig.ROLLOFF, 0.1, 4);
        integer("updatesPerTick", AcousticConfig.UPDATE_BUDGET, 1, 128);
        integer("maxTraceDistance", AcousticConfig.MAX_TRACE_DISTANCE, 16, 192);
        integer("diffractionRadius", AcousticConfig.DIFFRACTION_RADIUS, 1, 8);
        options.add(new Option(2, "excludedSounds", AcousticConfig.EXCLUDED_SOUNDS, s -> ConfigInput.entries(s, false), false));
        options.add(new Option(2, "materialOverrides", AcousticConfig.MATERIALS, s -> ConfigInput.entries(s, true), false));
    }

    private void toggle(String key, ForgeConfigSpec.BooleanValue value) {
        options.add(new Option(0, key, value, Boolean::parseBoolean, true));
    }
    private void decimal(int page, String key, ForgeConfigSpec.DoubleValue value, double min, double max) {
        options.add(new Option(page, key, value, s -> ConfigInput.decimal(s, min, max), false));
    }
    private void integer(String key, ForgeConfigSpec.IntValue value, int min, int max) {
        options.add(new Option(1, key, value, s -> ConfigInput.integer(s, min, max), false));
    }

    @Override protected void init() {
        inputs.clear();
        int left = left(), body = bodyWidth();
        for (int i = 0; i < 4; i++) {
            final int target = i;
            Button tab = Button.builder(text("tab." + i), b -> { page = target; rebuildWidgets(); })
                    .bounds(left + i * (body / 4), 32, body / 4 - 2, 20).build();
            tab.active = page != i;
            addRenderableWidget(tab);
        }
        int y = 66;
        for (Option option : options) {
            if (option.page != page) continue;
            Tooltip tip = Tooltip.create(text(option.key + ".tip"));
            if (option.toggle) {
                addRenderableWidget(Button.builder(toggleLabel(option), b -> {
                    option.draft = Boolean.toString(!Boolean.parseBoolean(option.draft));
                    b.setMessage(toggleLabel(option));
                    status = Component.empty();
                }).bounds(left, y, body, 20).tooltip(tip).build());
            } else {
                int inputX = page == 2 ? left : left + body / 2;
                int inputY = page == 2 ? y + 15 : y;
                EditBox box = new EditBox(font, inputX, inputY, page == 2 ? body : body / 2, 20, text(option.key));
                box.setMaxLength(Math.max(32767, option.draft.length()));
                box.setValue(option.draft);
                box.setTooltip(tip);
                box.setResponder(value -> {
                    option.draft = value;
                    box.setTextColor(option.valid() ? 0xFFFFFF : 0xFF7777);
                    status = Component.empty();
                    validate();
                });
                box.setTextColor(option.valid() ? 0xFFFFFF : 0xFF7777);
                inputs.add(addRenderableWidget(box));
            }
            y += page == 2 ? 60 : 25;
        }
        int buttonWidth = body / 3 - 2;
        if (page == 3) {
            addRenderableWidget(Button.builder(text("keyBindings"), b -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options)))
                    .bounds(left, 101, body, 20).tooltip(Tooltip.create(text("keyBindings.tip"))).build());
        }
        addRenderableWidget(Button.builder(text("defaults"), b -> {
            options.forEach(option -> option.draft = Option.format(option.value.getDefault()));
            status = Component.empty();
            rebuildWidgets();
        }).bounds(left, height - 28, buttonWidth, 20).tooltip(Tooltip.create(text("defaults.tip"))).build());
        addRenderableWidget(Button.builder(text("cancel"), b -> onClose())
                .bounds(left + body / 3, height - 28, buttonWidth, 20).build());
        saveButton = addRenderableWidget(Button.builder(text("save"), b -> save())
                .bounds(left + 2 * (body / 3), height - 28, buttonWidth, 20).build());
        validate();
    }

    private void validate() {
        if (saveButton != null) saveButton.active = options.stream().allMatch(Option::valid);
    }

    private void save() {
        if (!options.stream().allMatch(Option::valid)) return;
        List<Object> before = options.stream().map(o -> (Object) o.value.get()).toList();
        try {
            options.forEach(o -> o.set(o.parse.apply(o.draft)));
            AcousticConfig.SPEC.save();
            onClose();
        } catch (RuntimeException failure) {
            for (int i = 0; i < options.size(); i++) options.get(i).set(before.get(i));
            status = text("saveFailed");
            LogUtils.getLogger().error("Could not save acoustic settings", failure);
        }
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void tick() { inputs.forEach(EditBox::tick); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        int y = 66;
        for (Option option : options) {
            if (option.page != page) continue;
            if (!option.toggle) graphics.drawString(font, text(option.key), left(), y + (page == 2 ? 0 : 6), 0xEEEEEE);
            y += page == 2 ? 60 : 25;
        }
        Component message = !status.getString().isEmpty() ? status :
                options.stream().anyMatch(o -> !o.valid()) ? text("invalid") : text("hint." + page);
        graphics.drawCenteredString(font, message, width / 2, height - 43, 0xCCCCCC);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int bodyWidth() { return Math.min(360, width - 20); }
    private int left() { return (width - bodyWidth()) / 2; }
    private static Component text(String key) { return Component.translatable("gungang.options." + key); }
    private static Component toggleLabel(Option option) {
        return Component.translatable("gungang.options.toggle", text(option.key), text(Boolean.parseBoolean(option.draft) ? "on" : "off"));
    }

    private static final class Option {
        final int page;
        final String key;
        final ForgeConfigSpec.ConfigValue<?> value;
        final Function<String, Object> parse;
        final boolean toggle;
        String draft;
        Option(int page, String key, ForgeConfigSpec.ConfigValue<?> value, Function<String, Object> parse, boolean toggle) {
            this.page = page; this.key = key; this.value = value; this.parse = parse; this.toggle = toggle;
            this.draft = format(value.get());
        }
        boolean valid() {
            try { parse.apply(draft); return true; }
            catch (IllegalArgumentException invalid) { return false; }
        }
        @SuppressWarnings({"rawtypes", "unchecked"})
        void set(Object newValue) { ((ForgeConfigSpec.ConfigValue) value).set(newValue); }
        static String format(Object value) {
            return value instanceof List<?> list ? String.join(", ", list.stream().map(Object::toString).toList()) : value.toString();
        }
    }
}
