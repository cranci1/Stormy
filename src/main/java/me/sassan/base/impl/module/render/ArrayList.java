package me.sassan.base.impl.module.render;

import me.sassan.base.Base;
import me.sassan.base.api.module.Module;
import me.sassan.base.api.setting.impl.BooleanSetting;
import me.sassan.base.api.setting.impl.DoubleSliderSetting;
import me.sassan.base.api.setting.impl.SliderSetting;
import me.sassan.base.impl.module.combact.AutoClicker;
import me.sassan.base.impl.module.player.SafeWalk;
import me.sassan.base.utils.render.RenderUtils;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.weavemc.loader.api.event.RenderGameOverlayEvent;
import net.weavemc.loader.api.event.SubscribeEvent;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ArrayList extends Module {
    // Settings
    private final BooleanSetting background = new BooleanSetting("Background", true);
    private final BooleanSetting outline = new BooleanSetting("Outline", true);
    private final BooleanSetting lowercase = new BooleanSetting("Lowercase", false);
    private final BooleanSetting showValues = new BooleanSetting("Show Values", true);
    private final DoubleSliderSetting colorHue = new DoubleSliderSetting("Color Hue", 180.0, 210.0, 0.0, 360.0, 1.0);
    private final SliderSetting saturation = new SliderSetting("Saturation", 100.0, 0.0, 100.0, 1.0);
    private final SliderSetting brightness = new SliderSetting("Brightness", 100.0, 0.0, 100.0, 1.0);
    private final SliderSetting bgOpacity = new SliderSetting("Bg Opacity", 70.0, 0.0, 100.0, 1.0);
    private final SliderSetting fadeSpeed = new SliderSetting("Fade Speed", 200.0, 50.0, 500.0, 10.0);
    private final SliderSetting spacing = new SliderSetting("Spacing", 2.0, 0.0, 5.0, 0.5);

    // Animation tracking
    private final Map<String, ModuleAnimation> animations = new HashMap<>();

    public ArrayList() {
        super("ArrayList", "Shows enabled modules", Keyboard.KEY_NONE, Category.CLIENT);
        this.addSetting(background);
        this.addSetting(outline);
        this.addSetting(lowercase);
        this.addSetting(showValues);
        this.addSetting(colorHue);
        this.addSetting(saturation);
        this.addSetting(brightness);
        this.addSetting(bgOpacity);
        this.addSetting(fadeSpeed);
        this.addSetting(spacing);
        setEnabled(true);
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent event) {
        if (mc.gameSettings.showDebugInfo)
            return;

        ScaledResolution sr = new ScaledResolution(mc);
        FontRenderer fr = mc.fontRendererObj;

        // Get all enabled modules and sort by width
        List<Module> enabledModules = Base.INSTANCE.moduleRepo.list.stream()
                .filter(Module::isEnabled)
                .collect(Collectors.toList());

        // Update animations map - remove old entries
        animations.entrySet()
                .removeIf(entry -> enabledModules.stream().noneMatch(m -> m.getName().equals(entry.getKey())) &&
                        entry.getValue().getProgress() <= 0.01);

        // Add new entries and update existing ones
        for (Module module : enabledModules) {
            String name = module.getName();
            if (!animations.containsKey(name)) {
                animations.put(name, new ModuleAnimation(0));
            }
            animations.get(name).setTarget(1);
        }

        // Update animations for disabled modules
        for (Map.Entry<String, ModuleAnimation> entry : animations.entrySet()) {
            if (enabledModules.stream().noneMatch(m -> m.getName().equals(entry.getKey()))) {
                entry.getValue().setTarget(0);
            }
        }

        // Update all animations
        double animSpeed = fadeSpeed.getValue() / 1000.0;
        animations.forEach((name, anim) -> anim.update(animSpeed));

        // Sort modules by text width for rendering
        List<Module> sortedModules = enabledModules.stream()
                .sorted(Comparator.comparing(m -> -getModuleDisplayText(m).length()))
                .collect(Collectors.toList());

        // Add disabled but animating modules
        animations.entrySet().stream()
                .filter(e -> e.getValue().getProgress() > 0 &&
                        enabledModules.stream().noneMatch(m -> m.getName().equals(e.getKey())))
                .forEach(e -> {
                    Module module = Base.INSTANCE.moduleRepo.list.stream()
                            .filter(m -> m.getName().equals(e.getKey()))
                            .findFirst()
                            .orElse(null);
                    if (module != null) {
                        sortedModules.add(module);
                    }
                });

        // Render
        int y = 2; // Starting from top of the screen
        int maxWidth = 0;

        for (Module module : sortedModules) {
            String displayText = getModuleDisplayText(module);
            if (lowercase.getValue()) {
                displayText = displayText.toLowerCase();
            }

            double animProgress = animations.get(module.getName()).getProgress();
            if (animProgress <= 0)
                continue;

            int textWidth = fr.getStringWidth(displayText);
            maxWidth = Math.max(maxWidth, textWidth);

            // Calculate positions with animation - keep it at right side
            int x = (int) (sr.getScaledWidth() - (textWidth + 4) * animProgress);
            int height = fr.FONT_HEIGHT + 1;
            int moduleY = y;

            // Render background if enabled
            if (background.getValue()) {
                int bgAlpha = (int) (bgOpacity.getValue() * 2.55); // Convert 0-100 to 0-255
                int bgColor = new Color(0, 0, 0, bgAlpha).getRGB();
                RenderUtils.drawRect(x - 2, moduleY, textWidth + 4, height, bgColor);
            }

            // Render outline if enabled
            if (outline.getValue()) {
                Color outlineColor = getModuleColor(module, 0);
                RenderUtils.drawRect(x - 3, moduleY, 1, height, outlineColor.getRGB());
            }

            // Render text
            Color textColor = getModuleColor(module, moduleY);
            RenderUtils.drawString(fr, displayText, x, moduleY + 1, textColor.getRGB());

            // Increment Y position for next module - modules grow downward from top
            y += (height + spacing.getValue()) * animProgress;
        }
    }

    private String getModuleDisplayText(Module module) {
        StringBuilder sb = new StringBuilder(module.getName());

        if (showValues.getValue()) {
            // Add specific module info
            if (module instanceof AutoClicker) {
                AutoClicker ac = (AutoClicker) module;
                sb.append(" ").append(ac.getSettings().stream()
                        .filter(s -> s.getName().equals("CPS Range"))
                        .findFirst()
                        .map(s -> String.format("%.1f-%.1f",
                                ((DoubleSliderSetting) s).getMinValue(),
                                ((DoubleSliderSetting) s).getMaxValue()))
                        .orElse(""));
            } else if (module instanceof SafeWalk) {
                SafeWalk sw = (SafeWalk) module;
                sb.append(" ").append(sw.getSettings().stream()
                        .filter(s -> s.getName().equals("Shift Time (ms)"))
                        .findFirst()
                        .map(s -> String.format("%.0f", ((DoubleSliderSetting) s).getMinValue()))
                        .orElse(""));
            }
        }

        return sb.toString();
    }

    private Color getModuleColor(Module module, int yOffset) {
        // Use HSB color model for easier manipulation
        float startHue = (float) colorHue.getMinValue() / 360f;
        float endHue = (float) colorHue.getMaxValue() / 360f;

        // Calculate hue based on position or module name
        float hueOffset = (float) (yOffset % 100) / 100f;
        float hue = startHue + (endHue - startHue) * hueOffset;

        // Ensure hue is in valid range
        if (hue > 1)
            hue -= 1;
        if (hue < 0)
            hue += 1;

        return Color.getHSBColor(
                hue,
                (float) (saturation.getValue() / 100f),
                (float) (brightness.getValue() / 100f));
    }

    /**
     * Helper class to handle smooth module animations
     */
    private class ModuleAnimation {
        private double progress;
        private double target;

        public ModuleAnimation(double initialProgress) {
            this.progress = initialProgress;
            this.target = initialProgress;
        }

        public void setTarget(double target) {
            this.target = target;
        }

        public double getProgress() {
            return progress;
        }

        public void update(double speed) {
            if (progress < target) {
                progress = Math.min(progress + speed, target);
            } else if (progress > target) {
                progress = Math.max(progress - speed, target);
            }
        }
    }
}