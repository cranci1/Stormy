package me.sassan.base.ui;

import me.sassan.base.api.module.Module;
import me.sassan.base.api.module.Module.Category;
import me.sassan.base.api.setting.Setting;
import me.sassan.base.api.setting.impl.BooleanSetting;
import me.sassan.base.api.setting.impl.DoubleSliderSetting;
import me.sassan.base.api.setting.impl.SliderSetting;
import me.sassan.base.ui.components.impl.ModuleComponent;
import me.sassan.base.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modern Client GUI with horizontal tabs and dual-panel layout
 * Based on the Berry client design
 */
public class CGui extends GuiScreen {
    // Dimensions
    private int guiX = 60, guiY = 60;
    private int guiWidth = 600, guiHeight = 350;
    private final int leftPanelWidth = 180;
    private final int tabHeight = 28;
    private final int tabWidth = 90;
    private final int tabPadding = 5;
    private final int moduleHeight = 25;
    private final int settingsPadding = 15;
    private final int cornerRadius = 8;

    // Colors
    private final Color accentColor = new Color(98, 163, 255);
    private final Color accentColorDarker = new Color(78, 143, 235);
    private final Color bgColor = new Color(18, 18, 24, 220);
    private final Color panelColor = new Color(25, 25, 35, 220);
    private final Color moduleColor = new Color(40, 40, 50, 220);
    private final Color moduleHoverColor = new Color(50, 50, 60, 220);
    private final Color textColor = new Color(255, 255, 255);
    private final Color textColorDarker = new Color(180, 180, 180);

    // State
    private Category selectedCategory = Category.COMBAT;
    private Module selectedModule = null;
    private Module bindingModule = null;
    private Setting<?> draggingSetting = null;
    private boolean isDragging = false;
    private int dragOffsetX, dragOffsetY;

    // Scrolling
    private int moduleScrollY = 0;
    private int settingsScrollY = 0;
    private Map<Module, Integer> moduleSettingsScroll = new ConcurrentHashMap<>();
    private Map<Category, List<Module>> categoryModules = new HashMap<>();

    public CGui() {
        refreshModules();
    }

    public void refreshModules() {
        categoryModules.clear();

        for (Category category : Category.values()) {
            categoryModules.put(category, new ArrayList<>());
        }

        if (me.sassan.base.Base.INSTANCE != null && me.sassan.base.Base.INSTANCE.moduleRepo != null) {
            for (Module module : me.sassan.base.Base.INSTANCE.moduleRepo.list) {
                Category category = module.getCategory();
                List<Module> modules = categoryModules.get(category);
                if (modules != null) {
                    modules.add(module);
                }
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawGlassBackground();
        drawGui(mouseX, mouseY);
        handleDragging(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawGlassBackground() {
        ScaledResolution sr = new ScaledResolution(mc);
        drawGradientRect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(),
                new Color(0, 0, 0, 120).getRGB(),
                new Color(0, 0, 0, 90).getRGB());
    }

    private void drawGui(int mouseX, int mouseY) {
        // Main container
        RenderUtils.drawRoundedRect(guiX, guiY, guiWidth, guiHeight, cornerRadius, bgColor.getRGB());

        // Draw tabs
        drawCategoryTabs(mouseX, mouseY);

        // Left panel (modules)
        RenderUtils.drawRoundedRect(guiX + 10, guiY + 10, leftPanelWidth, guiHeight - 20, cornerRadius,
                panelColor.getRGB());
        drawModulesPanel(mouseX, mouseY);

        // Right panel (settings)
        if (selectedModule != null) {
            int rightPanelX = guiX + leftPanelWidth + 20;
            int rightPanelWidth = guiWidth - leftPanelWidth - 30;
            RenderUtils.drawRoundedRect(rightPanelX, guiY + 10, rightPanelWidth, guiHeight - 20, cornerRadius,
                    panelColor.getRGB());
            drawSettingsPanel(mouseX, mouseY);
        }
    }

    private void drawCategoryTabs(int mouseX, int mouseY) {
        int tabX = guiX + 20;
        int tabY = guiY - tabHeight - 5;

        for (Category category : Category.values()) {
            boolean isSelected = category == selectedCategory;
            boolean isHovered = mouseX >= tabX && mouseX <= tabX + tabWidth
                    && mouseY >= tabY && mouseY <= tabY + tabHeight;

            Color tabColor = isSelected ? accentColor : new Color(30, 30, 40);
            if (isHovered && !isSelected) {
                tabColor = new Color(40, 40, 50);
            }

            RenderUtils.drawRoundedRect(tabX, tabY, tabWidth, tabHeight, cornerRadius, tabColor.getRGB());

            String name = formatCategoryName(category);
            RenderUtils.drawString(mc.fontRendererObj, name,
                    tabX + (tabWidth - mc.fontRendererObj.getStringWidth(name)) / 2,
                    tabY + (tabHeight - mc.fontRendererObj.FONT_HEIGHT) / 2,
                    isSelected ? 0xFFFFFFFF : 0xFFAAAAAA);

            tabX += tabWidth + tabPadding;
        }
    }

    private String formatCategoryName(Category category) {
        String name = category.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    private void drawModulesPanel(int mouseX, int mouseY) {
        int panelX = guiX + 10;
        int panelY = guiY + 10;
        int panelWidth = leftPanelWidth;
        int panelHeight = guiHeight - 20;

        // Panel title
        RenderUtils.drawString(mc.fontRendererObj, formatCategoryName(selectedCategory),
                panelX + 15, panelY + 15, textColor.getRGB());

        // Draw modules
        GL11Util.startScissor(panelX + 10, panelY + 30, panelWidth - 20, panelHeight - 40);

        List<Module> modules = categoryModules.get(selectedCategory);
        int moduleY = panelY + 30 - moduleScrollY;

        if (modules != null) {
            for (Module module : modules) {
                if (moduleY + moduleHeight >= panelY + 30 && moduleY <= panelY + panelHeight - 10) {
                    boolean isSelected = module == selectedModule;
                    boolean isHovered = mouseX >= panelX + 15 && mouseX <= panelX + panelWidth - 15
                            && mouseY >= moduleY && mouseY <= moduleY + moduleHeight;

                    Color moduleBgColor = isSelected ? accentColor
                            : (module.isEnabled() ? accentColorDarker : (isHovered ? moduleHoverColor : moduleColor));

                    RenderUtils.drawRoundedRect(panelX + 15, moduleY, panelWidth - 30, moduleHeight, 4,
                            moduleBgColor.getRGB());

                    RenderUtils.drawString(mc.fontRendererObj, module.getName(),
                            panelX + 25, moduleY + (moduleHeight - mc.fontRendererObj.FONT_HEIGHT) / 2,
                            textColor.getRGB());
                }

                moduleY += moduleHeight + 5;
            }
        }

        GL11Util.endScissor();
    }

    private void drawSettingsPanel(int mouseX, int mouseY) {
        if (selectedModule == null)
            return;

        int rightPanelX = guiX + leftPanelWidth + 20;
        int rightPanelWidth = guiWidth - leftPanelWidth - 30;

        // Panel title
        RenderUtils.drawString(mc.fontRendererObj, selectedModule.getName() + " Settings",
                rightPanelX + 15, guiY + 25, textColor.getRGB());

        // Settings area
        GL11Util.startScissor(rightPanelX + 10, guiY + 45, rightPanelWidth - 20, guiHeight - 55);

        int settingsY = guiY + 45 - settingsScrollY;

        // Module bind button
        int buttonColor = bindingModule == selectedModule ? accentColor.getRGB() : new Color(50, 50, 60, 180).getRGB();
        RenderUtils.drawRoundedRect(rightPanelX + 15, settingsY, rightPanelWidth - 30, 25, 4, buttonColor);

        String bindText = bindingModule == selectedModule ? "Press a key..."
                : "Bind: " + Keyboard.getKeyName(selectedModule.getKey());
        RenderUtils.drawString(mc.fontRendererObj, bindText,
                rightPanelX + 25, settingsY + (25 - mc.fontRendererObj.FONT_HEIGHT) / 2, textColor.getRGB());

        settingsY += 35;

        // Module settings
        if (selectedModule.getSettings() != null) {
            for (Setting<?> setting : selectedModule.getSettings()) {
                if (setting instanceof BooleanSetting) {
                    drawBooleanSetting((BooleanSetting) setting, rightPanelX + 15, settingsY, rightPanelWidth - 30);
                    settingsY += 30;
                } else if (setting instanceof SliderSetting) {
                    drawSliderSetting((SliderSetting) setting, rightPanelX + 15, settingsY, rightPanelWidth - 30);
                    settingsY += 45;
                } else if (setting instanceof DoubleSliderSetting) {
                    drawDoubleSliderSetting((DoubleSliderSetting) setting, rightPanelX + 15, settingsY,
                            rightPanelWidth - 30);
                    settingsY += 45;
                } else {
                    RenderUtils.drawString(mc.fontRendererObj, setting.getName() + ": " + setting.getValue(),
                            rightPanelX + 25, settingsY, textColor.getRGB());
                    settingsY += 25;
                }
            }
        }

        GL11Util.endScissor();
    }

    private void drawBooleanSetting(BooleanSetting setting, int x, int y, int width) {
        // Setting name
        RenderUtils.drawString(mc.fontRendererObj, setting.getName(), x + 10, y + 5, textColor.getRGB());

        // Toggle switch
        int toggleWidth = 35;
        int toggleHeight = 14;
        int toggleX = x + width - toggleWidth - 10;
        int toggleY = y + 5;

        boolean enabled = setting.getValue();
        Color bgColor = new Color(40, 40, 50);
        Color toggleColor = enabled ? accentColor : new Color(80, 80, 90);

        RenderUtils.drawRoundedRect(toggleX, toggleY, toggleWidth, toggleHeight, toggleHeight / 2, bgColor.getRGB());

        int knobSize = toggleHeight - 4;
        int knobX = enabled ? toggleX + toggleWidth - knobSize - 2 : toggleX + 2;
        int knobY = toggleY + 2;

        RenderUtils.drawRoundedRect(knobX, knobY, knobSize, knobSize, knobSize / 2, toggleColor.getRGB());
    }

    private void drawSliderSetting(SliderSetting setting, int x, int y, int width) {
        // Setting name and value
        RenderUtils.drawString(mc.fontRendererObj, setting.getName(), x + 10, y + 5, textColor.getRGB());

        String valueStr = String.format("%.1f", setting.getValue());
        RenderUtils.drawString(mc.fontRendererObj, valueStr,
                x + width - 10 - mc.fontRendererObj.getStringWidth(valueStr), y + 5, textColorDarker.getRGB());

        // Slider track
        int sliderY = y + 25;
        int sliderHeight = 6;
        int sliderWidth = width - 20;

        RenderUtils.drawRoundedRect(x + 10, sliderY, sliderWidth, sliderHeight, sliderHeight / 2,
                new Color(40, 40, 50).getRGB());

        // Filled portion
        double percent = (setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
        int fillWidth = Math.max(5, (int) (percent * sliderWidth));

        RenderUtils.drawRoundedRect(x + 10, sliderY, fillWidth, sliderHeight, sliderHeight / 2, accentColor.getRGB());

        // Knob
        int knobSize = 10;
        int knobX = x + 10 + fillWidth - knobSize / 2;
        int knobY = sliderY + (sliderHeight - knobSize) / 2;

        RenderUtils.drawFilledCircle(knobX + knobSize / 2, knobY + knobSize / 2, knobSize / 2, textColor.getRGB());
    }

    private void drawDoubleSliderSetting(DoubleSliderSetting setting, int x, int y, int width) {
        // Setting name and value
        RenderUtils.drawString(mc.fontRendererObj, setting.getName(), x + 10, y + 5, textColor.getRGB());

        String valueStr = String.format("%.1f - %.1f", setting.getMinValue(), setting.getMaxValue());
        RenderUtils.drawString(mc.fontRendererObj, valueStr,
                x + width - 10 - mc.fontRendererObj.getStringWidth(valueStr), y + 5, textColorDarker.getRGB());

        // Slider track
        int sliderY = y + 25;
        int sliderHeight = 6;
        int sliderWidth = width - 20;

        RenderUtils.drawRoundedRect(x + 10, sliderY, sliderWidth, sliderHeight, sliderHeight / 2,
                new Color(40, 40, 50).getRGB());

        // Filled portion
        double minPercent = (setting.getMinValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
        double maxPercent = (setting.getMaxValue() - setting.getMin()) / (setting.getMax() - setting.getMin());

        int minPos = x + 10 + (int) (minPercent * sliderWidth);
        int maxPos = x + 10 + (int) (maxPercent * sliderWidth);

        if (maxPos > minPos) {
            RenderUtils.drawRect(minPos, sliderY, maxPos - minPos, sliderHeight, accentColor.getRGB());
        }

        // Knobs
        int knobSize = 10;
        RenderUtils.drawFilledCircle(minPos, sliderY + sliderHeight / 2, knobSize / 2, textColor.getRGB());
        RenderUtils.drawFilledCircle(maxPos, sliderY + sliderHeight / 2, knobSize / 2, textColor.getRGB());
    }

    private void handleDragging(int mouseX, int mouseY) {
        if (isDragging) {
            guiX = mouseX - dragOffsetX;
            guiY = mouseY - dragOffsetY;
        }

        if (draggingSetting != null && Mouse.isButtonDown(0)) {
            int rightPanelX = guiX + leftPanelWidth + 20;
            int rightPanelWidth = guiWidth - leftPanelWidth - 30;

            if (draggingSetting instanceof SliderSetting) {
                SliderSetting slider = (SliderSetting) draggingSetting;
                int sliderX = rightPanelX + 15 + 10;
                int sliderWidth = rightPanelWidth - 30 - 20;

                float percent = MathHelper.clamp_float((mouseX - sliderX) / (float) sliderWidth, 0, 1);
                double value = slider.getMin() + percent * (slider.getMax() - slider.getMin());
                value = Math.round(value / slider.getIncrement()) * slider.getIncrement();
                slider.setValue(value);
            } else if (draggingSetting instanceof DoubleSliderSetting) {
                DoubleSliderSetting slider = (DoubleSliderSetting) draggingSetting;
                int sliderX = rightPanelX + 15 + 10;
                int sliderWidth = rightPanelWidth - 30 - 20;

                float percent = MathHelper.clamp_float((mouseX - sliderX) / (float) sliderWidth, 0, 1);
                double value = slider.getMin() + percent * (slider.getMax() - slider.getMin());
                value = Math.round(value / slider.getIncrement()) * slider.getIncrement();

                // Determine which handle is closer to adjust
                double minVal = slider.getMinValue();
                double maxVal = slider.getMaxValue();

                if (Math.abs(value - minVal) < Math.abs(value - maxVal)) {
                    slider.setMinValue(Math.min(value, maxVal - slider.getIncrement()));
                } else {
                    slider.setMaxValue(Math.max(value, minVal + slider.getIncrement()));
                }
            }
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (bindingModule != null) {
            bindingModule = null;
            return;
        }

        // Check if clicking the window header for dragging
        if (mouseButton == 0 && mouseY >= guiY && mouseY <= guiY + 20
                && mouseX >= guiX && mouseX <= guiX + guiWidth) {
            isDragging = true;
            dragOffsetX = mouseX - guiX;
            dragOffsetY = mouseY - guiY;
            return;
        }

        // Check tab clicks
        int tabX = guiX + 20;
        int tabY = guiY - tabHeight - 5;

        for (Category category : Category.values()) {
            if (mouseX >= tabX && mouseX <= tabX + tabWidth
                    && mouseY >= tabY && mouseY <= tabY + tabHeight) {
                selectedCategory = category;
                // Only reset selected module if switching categories
                if (selectedModule != null && selectedModule.getCategory() != category) {
                    selectedModule = null;
                }
                return;
            }
            tabX += tabWidth + tabPadding;
        }

        // Check module clicks
        int panelX = guiX + 10;
        int panelY = guiY + 10;
        int panelWidth = leftPanelWidth;
        int moduleY = panelY + 30 - moduleScrollY;

        List<Module> modules = categoryModules.get(selectedCategory);

        if (modules != null) {
            for (Module module : modules) {
                if (mouseX >= panelX + 15 && mouseX <= panelX + panelWidth - 15
                        && mouseY >= moduleY && mouseY <= moduleY + moduleHeight) {
                    if (mouseButton == 0) {
                        selectedModule = module;
                        settingsScrollY = 0;
                    } else if (mouseButton == 1) {
                        module.toggle();
                    }
                    return;
                }
                moduleY += moduleHeight + 5;
            }
        }

        // Check settings clicks
        if (selectedModule != null) {
            int rightPanelX = guiX + leftPanelWidth + 20;
            int rightPanelWidth = guiWidth - leftPanelWidth - 30;
            int settingsY = guiY + 45 - settingsScrollY;

            // Check bind button
            if (mouseX >= rightPanelX + 15 && mouseX <= rightPanelX + rightPanelWidth - 15
                    && mouseY >= settingsY && mouseY <= settingsY + 25) {
                bindingModule = selectedModule;
                return;
            }

            settingsY += 35;

            // Check settings
            if (selectedModule.getSettings() != null) {
                for (Setting<?> setting : selectedModule.getSettings()) {
                    int settingHeight = 30;
                    if (setting instanceof SliderSetting || setting instanceof DoubleSliderSetting) {
                        settingHeight = 45;
                    }

                    if (mouseY >= settingsY && mouseY <= settingsY + settingHeight) {
                        if (setting instanceof BooleanSetting) {
                            if (mouseX >= rightPanelX + rightPanelWidth - 60
                                    && mouseX <= rightPanelX + rightPanelWidth - 15
                                    && mouseY >= settingsY && mouseY <= settingsY + 25) {
                                ((BooleanSetting) setting).setValue(!((BooleanSetting) setting).getValue());
                                return;
                            }
                        } else if (setting instanceof SliderSetting) {
                            if (mouseY >= settingsY + 20 && mouseY <= settingsY + 35) {
                                draggingSetting = setting;
                                return;
                            }
                        } else if (setting instanceof DoubleSliderSetting) {
                            if (mouseY >= settingsY + 20 && mouseY <= settingsY + 35) {
                                draggingSetting = setting;
                                return;
                            }
                        }
                    }

                    settingsY += settingHeight;
                }
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        isDragging = false;
        draggingSetting = null;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();

        int scroll = Mouse.getEventDWheel();
        if (scroll != 0) {
            int mouseX = Mouse.getEventX() * this.width / mc.displayWidth;
            int mouseY = this.height - Mouse.getEventY() * this.height / mc.displayHeight - 1;

            int panelX = guiX + 10;
            int panelY = guiY + 10;
            int panelWidth = leftPanelWidth;
            int panelHeight = guiHeight - 20;

            // Scroll modules panel
            if (mouseX >= panelX && mouseX <= panelX + panelWidth
                    && mouseY >= panelY + 30 && mouseY <= panelY + panelHeight) {
                moduleScrollY = MathHelper.clamp_int(
                        moduleScrollY + (scroll > 0 ? -15 : 15),
                        0,
                        Math.max(0, categoryModules.get(selectedCategory).size() * (moduleHeight + 5)
                                - (panelHeight - 40)));
                return;
            }

            // Scroll settings panel
            if (selectedModule != null) {
                int rightPanelX = guiX + leftPanelWidth + 20;
                int rightPanelWidth = guiWidth - leftPanelWidth - 30;

                if (mouseX >= rightPanelX && mouseX <= rightPanelX + rightPanelWidth
                        && mouseY >= guiY + 45 && mouseY <= guiY + guiHeight - 10) {
                    settingsScrollY = MathHelper.clamp_int(
                            settingsScrollY + (scroll > 0 ? -15 : 15),
                            0,
                            calculateSettingsContentHeight() - (guiHeight - 55));
                    return;
                }
            }
        }
    }

    private int calculateSettingsContentHeight() {
        int height = 35; // Bind button

        if (selectedModule != null && selectedModule.getSettings() != null) {
            for (Setting<?> setting : selectedModule.getSettings()) {
                if (setting instanceof SliderSetting || setting instanceof DoubleSliderSetting) {
                    height += 45;
                } else {
                    height += 30;
                }
            }
        }

        return height;
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (bindingModule != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                bindingModule.setKey(Keyboard.KEY_NONE);
            } else if (keyCode != Keyboard.KEY_BACK && keyCode != Keyboard.KEY_DELETE) {
                bindingModule.setKey(keyCode);
            }
            bindingModule = null;
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    /**
     * Utility class for GL11 scissor operations
     */
    private static class GL11Util {
        public static void startScissor(int x, int y, int width, int height) {
            ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
            int scaleFactor = sr.getScaleFactor();

            GL11.glPushMatrix();
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(x * scaleFactor, Minecraft.getMinecraft().displayHeight - (y + height) * scaleFactor,
                    width * scaleFactor, height * scaleFactor);
        }

        public static void endScissor() {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glPopMatrix();
        }
    }
}