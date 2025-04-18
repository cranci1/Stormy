package me.sassan.base.impl.module.render;

import me.sassan.base.Base;
import me.sassan.base.api.module.Module;
import me.sassan.base.api.setting.impl.BooleanSetting;
import me.sassan.base.api.setting.impl.DoubleSliderSetting;
import me.sassan.base.api.setting.impl.SliderSetting;
import me.sassan.base.impl.module.combact.*;
import me.sassan.base.impl.module.player.*;
import me.sassan.base.utils.render.RenderUtils;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.weavemc.loader.api.event.RenderGameOverlayEvent;
import net.weavemc.loader.api.event.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import java.awt.*;
import java.util.List;

public class ArrayList extends Module {
    private final BooleanSetting background = new BooleanSetting("Background", false);
    private final BooleanSetting outline = new BooleanSetting("Outline", false);
    private final BooleanSetting textShadow = new BooleanSetting("Text Shadow", true);
    private final BooleanSetting lowercase = new BooleanSetting("Lowercase", true);
    private final BooleanSetting showValues = new BooleanSetting("Show Values", true);
    private final DoubleSliderSetting colorHue = new DoubleSliderSetting("Color Hue", 180.0, 210.0, 0.0, 360.0, 1.0);
    private final SliderSetting saturation = new SliderSetting("Saturation", 100.0, 0.0, 100.0, 1.0);
    private final SliderSetting brightness = new SliderSetting("Brightness", 100.0, 0.0, 100.0, 1.0);
    private final SliderSetting bgOpacity = new SliderSetting("Bg Opacity", 70.0, 0.0, 100.0, 1.0);
    private final SliderSetting spacing = new SliderSetting("Spacing", 0.0, 0.0, 5.0, 0.5);

    public ArrayList() {
        super("ArrayList", "Shows enabled modules", Keyboard.KEY_NONE, Category.CLIENT);
        this.addSetting(background);
        this.addSetting(outline);
        this.addSetting(textShadow);
        this.addSetting(lowercase);
        this.addSetting(showValues);
        this.addSetting(colorHue);
        this.addSetting(saturation);
        this.addSetting(brightness);
        this.addSetting(bgOpacity);
        this.addSetting(spacing);
        setEnabled(true);
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent event) {
        if (mc.gameSettings.showDebugInfo)
            return;

        ScaledResolution sr = new ScaledResolution(mc);
        FontRenderer fr = mc.fontRendererObj;

        List<Module> enabledModules = Base.INSTANCE.moduleRepo.list;
        int enabledCount = 0;
        String[] displayTexts = new String[enabledModules.size()];
        int[] widths = new int[enabledModules.size()];

        for (int i = 0; i < enabledModules.size(); i++) {
            Module m = enabledModules.get(i);
            if (!m.isEnabled())
                continue;
            String displayText = getModuleDisplayText(m);
            if (lowercase.getValue())
                displayText = displayText.toLowerCase();
            displayTexts[enabledCount] = displayText;
            widths[enabledCount] = fr.getStringWidth(displayText);
            enabledModules.set(enabledCount, m);
            enabledCount++;
        }
        for (int i = 0; i < enabledCount - 1; i++) {
            for (int j = i + 1; j < enabledCount; j++) {
                if (widths[j] > widths[i]) {
                    // swap
                    int w = widths[i];
                    widths[i] = widths[j];
                    widths[j] = w;
                    String t = displayTexts[i];
                    displayTexts[i] = displayTexts[j];
                    displayTexts[j] = t;
                    Module m = enabledModules.get(i);
                    enabledModules.set(i, enabledModules.get(j));
                    enabledModules.set(j, m);
                }
            }
        }

        int y = 2;
        for (int i = 0; i < enabledCount; i++) {
            Module module = enabledModules.get(i);
            String displayText = displayTexts[i];
            int textWidth = widths[i];
            int x = sr.getScaledWidth() - textWidth - 4;
            int height = fr.FONT_HEIGHT + 1;

            if (background.getValue()) {
                int bgAlpha = (int) (bgOpacity.getValue() * 2.55);
                int bgColor = new Color(0, 0, 0, bgAlpha).getRGB();
                RenderUtils.drawRect(x - 2, y, textWidth + 4, height, bgColor);
            }
            if (outline.getValue()) {
                Color outlineColor = getModuleColor(module, y);
                RenderUtils.drawRect(x - 3, y, 1, height, outlineColor.getRGB());
            }
            Color textColor = getModuleColor(module, y);
            if (textShadow.getValue()) {
                fr.drawStringWithShadow(displayText, x, y + 1, textColor.getRGB());
            } else {
                fr.drawString(displayText, x, y + 1, textColor.getRGB());
            }
            y += height + spacing.getValue();
        }
    }

    private String getModuleDisplayText(Module module) {
        StringBuilder sb = new StringBuilder(module.getName());

        if (showValues.getValue()) {
            if (module instanceof AutoClicker) {
                AutoClicker ac = (AutoClicker) module;
                String valueText = ac.getSettings().stream()
                        .filter(s -> s.getName().equals("CPS Range"))
                        .findFirst()
                        .map(s -> String.format("%.1f-%.1f",
                                ((DoubleSliderSetting) s).getMinValue(),
                                ((DoubleSliderSetting) s).getMaxValue()))
                        .orElse("");
                if (!valueText.isEmpty()) {
                    sb.append(" §7[").append(valueText).append("]§r");
                }
            } else if (module instanceof SafeWalk) {
                SafeWalk sw = (SafeWalk) module;
                String valueText = sw.getSettings().stream()
                        .filter(s -> s.getName().equals("Shift Time (ms)"))
                        .findFirst()
                        .map(s -> String.format("%.1f-%.1f",
                                ((DoubleSliderSetting) s).getMinValue(),
                                ((DoubleSliderSetting) s).getMaxValue()))
                        .orElse("");
                if (!valueText.isEmpty()) {
                    sb.append(" §7[").append(valueText).append("]§r");
                }
            } else if (module instanceof FastPlace) {
                FastPlace fp = (FastPlace) module;
                String valueText = fp.getSettings().stream()
                        .filter(s -> s.getName().equals("Delay"))
                        .findFirst()
                        .map(s -> String.format("%.1f", ((SliderSetting) s).getValue()))
                        .orElse("");
                if (!valueText.isEmpty()) {
                    sb.append(" §7[").append(valueText).append("]§r");
                }
            }
        }

        return sb.toString();
    }

    private Color getModuleColor(Module module, int yOffset) {
        float startHue = (float) colorHue.getMinValue() / 360f;
        float endHue = (float) colorHue.getMaxValue() / 360f;

        float hueOffset = (float) (yOffset % 100) / 100f;
        float hue = startHue + (endHue - startHue) * hueOffset;

        if (hue > 1)
            hue -= 1;
        if (hue < 0)
            hue += 1;

        return Color.getHSBColor(
                hue,
                (float) (saturation.getValue() / 100f),
                (float) (brightness.getValue() / 100f));
    }
}