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
import java.util.List;

/**
 * Redesigned client GUI with modern visuals and improved functionality
 * 
 * @author sassan (original)
 */
public class CGui extends GuiScreen {
    // GUI dimensions and positioning
    private int x, y, width, height;
    private final int leftPanelWidth = 150;
    private final int rightPanelPadding = 20;
    private final int tabHeight = 24;
    private final int tabPadding = 10;
    private final int tabWidth = 80;
    private boolean resizing = false;
    private int resizeCorner = 15;
    private final int minWidth = 350;
    private final int minHeight = 250;

    // Colors
    private final int accentColor = new Color(65, 105, 225).getRGB(); // Royal blue
    private final int headerColor = new Color(15, 15, 20).getRGB();
    private final int bgColor = new Color(20, 20, 25).getRGB();
    private final int panelColor = new Color(25, 25, 30).getRGB();
    private final int highlightColor = new Color(45, 45, 55).getRGB();
    private final int textColor = new Color(220, 220, 220).getRGB();
    private final int subTextColor = new Color(170, 170, 170).getRGB();

    // Module and category state
    private final List<ModuleComponent> moduleComponents = new ArrayList<>();
    private Module selectedModule = null;
    private Category selectedCategory = Category.COMBAT;
    private Module listeningForBind = null;
    private Setting<?> draggingSetting = null;

    // Dragging state
    private boolean dragging = false;
    private int dragOffsetX, dragOffsetY;
    private long lastClickTime = 0;

    // Scrolling
    private int scrollY = 0;
    private int maxScrollY = 0;

    // Add a client name constant
    private final String CLIENT_NAME = "Berry";

    public CGui() {
        this.width = 500;
        this.height = 350;
        this.x = 50;
        this.y = 50;
        refreshModules();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Handle mouse dragging for the UI
        handleDragging(mouseX, mouseY);

        // Handle resizing
        handleResizing(mouseX, mouseY);

        // Draw the main window background with a border
        drawMainWindow();

        // Draw category tabs at the top
        drawCategoryTabs(mouseX, mouseY);

        // Draw left panel with modules
        drawModulesPanel(mouseX, mouseY);

        // Draw right panel with settings
        drawSettingsPanel(mouseX, mouseY);

        // Handle scrolling
        handleScroll();

        // Handle continuous slider dragging
        handleSliderDragging(mouseX, mouseY);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void handleDragging(int mouseX, int mouseY) {
        if (dragging) {
            ScaledResolution sr = new ScaledResolution(mc);
            int maxX = sr.getScaledWidth() - width;
            int maxY = sr.getScaledHeight() - height;
            this.x = Math.max(0, Math.min(mouseX - dragOffsetX, maxX));
            this.y = Math.max(0, Math.min(mouseY - dragOffsetY, maxY));
        }
    }

    private void handleResizing(int mouseX, int mouseY) {
        if (resizing) {
            this.width = Math.max(minWidth, mouseX - x);
            this.height = Math.max(minHeight, mouseY - y);
        }

        // Show resize cursor when near corner
        boolean nearCorner = mouseX >= x + width - resizeCorner && mouseX <= x + width
                && mouseY >= y + height - resizeCorner && mouseY <= y + height;

        if (nearCorner) {
            // Would set cursor to resize, but Minecraft doesn't support this directly
        }
    }

    private void drawMainWindow() {
        // Window background with drop shadow effect
        RenderUtils.drawRect(x + 5, y + 5, width, height, 0x55000000); // Shadow
        RenderUtils.drawRect(x, y, width, height, bgColor);

        // Title bar with updated client name
        RenderUtils.drawRect(x, y, width, tabHeight, headerColor);
        RenderUtils.drawString(Minecraft.getMinecraft().fontRendererObj, CLIENT_NAME, x + 10, y + 8, textColor);

        // Resize handle indicator in the bottom-right corner
        RenderUtils.drawRect(x + width - resizeCorner, y + height - 1, resizeCorner, 1, accentColor);
        RenderUtils.drawRect(x + width - 1, y + height - resizeCorner, 1, resizeCorner, accentColor);
    }

    private void drawCategoryTabs(int mouseX, int mouseY) {
        int tabX = x + 100; // Start tabs after client name (adjusted spacing for longer name)
        int tabY = y;

        // Draw category tabs
        for (Category category : Category.values()) {
            boolean isSelected = category == selectedCategory;
            boolean isHovered = mouseX >= tabX && mouseX <= tabX + tabWidth && mouseY >= tabY
                    && mouseY <= tabY + tabHeight;

            int color = isSelected ? accentColor : (isHovered ? highlightColor : headerColor);
            RenderUtils.drawRect(tabX, tabY, tabWidth, tabHeight, color);

            // Draw indicator line under selected tab
            if (isSelected) {
                RenderUtils.drawRect(tabX, tabY + tabHeight - 2, tabWidth, 2, new Color(255, 255, 255).getRGB());
            }

            RenderUtils.drawString(mc.fontRendererObj, category.name(),
                    tabX + (tabWidth - mc.fontRendererObj.getStringWidth(category.name())) / 2,
                    tabY + (tabHeight - mc.fontRendererObj.FONT_HEIGHT) / 2, isSelected ? 0xFFFFFFFF : subTextColor);

            tabX += tabWidth + tabPadding;
        }
    }

    private void drawModulesPanel(int mouseX, int mouseY) {
        // Left panel for modules
        RenderUtils.drawRect(x, y + tabHeight, leftPanelWidth, height - tabHeight, panelColor);

        // Calculate max scroll
        int totalModuleHeight = 0;
        for (ModuleComponent md : moduleComponents) {
            if (md.getModule().getCategory() == selectedCategory) {
                totalModuleHeight += md.height + 5;
            }
        }

        maxScrollY = Math.max(0, totalModuleHeight - (height - tabHeight - 10));
        scrollY = MathHelper.clamp_int(scrollY, 0, Math.max(0, maxScrollY));

        // Draw a scrollbar if needed
        if (maxScrollY > 0) {
            int scrollbarHeight = (int) ((height - tabHeight) * ((float) (height - tabHeight) / totalModuleHeight));
            int scrollbarY = y + tabHeight
                    + (int) ((height - tabHeight - scrollbarHeight) * ((float) scrollY / maxScrollY));

            // Scrollbar track
            RenderUtils.drawRect(x + leftPanelWidth - 5, y + tabHeight, 3, height - tabHeight,
                    new Color(40, 40, 45).getRGB());

            // Scrollbar handle
            RenderUtils.drawRect(x + leftPanelWidth - 5, scrollbarY, 3, scrollbarHeight,
                    new Color(100, 100, 120).getRGB());
        }

        // Scissor test to clip modules in the panel
        GL11Util.startScissor(x, y + tabHeight, leftPanelWidth, height - tabHeight);

        // Draw modules
        int moduleY = y + tabHeight + 10 - scrollY;
        for (ModuleComponent md : moduleComponents) {
            if (md.getModule().getCategory() != selectedCategory)
                continue;

            if (moduleY + md.height > y + tabHeight && moduleY < y + height) {
                md.updateComponent(x + 10, moduleY);
                md.drawScreen(mouseX, mouseY);
            }
            moduleY += md.height + 5;
        }

        GL11Util.endScissor();
    }

    private void drawSettingsPanel(int mouseX, int mouseY) {
        // Right panel for settings
        RenderUtils.drawRect(x + leftPanelWidth, y + tabHeight, width - leftPanelWidth, height - tabHeight, panelColor);

        if (selectedModule != null && selectedModule.getCategory() == selectedCategory) {
            int settingsY = y + tabHeight + rightPanelPadding;
            int settingsX = x + leftPanelWidth + rightPanelPadding;

            // Module header
            RenderUtils.drawString(mc.fontRendererObj, selectedModule.getName() + " Settings", settingsX, settingsY,
                    textColor);
            RenderUtils.drawRect(settingsX, settingsY + 15, width - leftPanelWidth - (rightPanelPadding * 2), 1,
                    new Color(60, 60, 70).getRGB());
            settingsY += 25;

            // Enabled toggle button
            int toggleWidth = 50;
            int toggleHeight = 20;
            boolean isEnabled = selectedModule.isEnabled();
            Color toggleColor = isEnabled ? new Color(65, 165, 80) : new Color(180, 60, 60);
            RenderUtils.drawRoundedRect(settingsX, settingsY, toggleWidth, toggleHeight, 3, toggleColor.getRGB());
            RenderUtils.drawString(mc.fontRendererObj, isEnabled ? "ON" : "OFF",
                    settingsX + (toggleWidth - mc.fontRendererObj.getStringWidth(isEnabled ? "ON" : "OFF")) / 2,
                    settingsY + (toggleHeight - mc.fontRendererObj.FONT_HEIGHT) / 2, 0xFFFFFFFF);

            // Module description if available
            if (selectedModule.getDescription() != null && !selectedModule.getDescription().isEmpty()) {
                RenderUtils.drawString(mc.fontRendererObj, selectedModule.getDescription(),
                        settingsX + toggleWidth + 15,
                        settingsY + 6, subTextColor);
            }

            settingsY += toggleHeight + 15;

            // Keybind setting with improved visuals
            RenderUtils.drawString(mc.fontRendererObj, "Keybind", settingsX, settingsY, textColor);
            settingsY += 15;

            int bindBoxWidth = 100, bindBoxHeight = 20;
            int bindBoxX = settingsX, bindBoxY = settingsY;
            boolean isListening = listeningForBind == selectedModule;

            // Draw keybind box with smooth colors
            RenderUtils.drawRoundedRect(bindBoxX, bindBoxY, bindBoxWidth, bindBoxHeight, 3,
                    isListening ? new Color(100, 100, 200).getRGB() : new Color(60, 60, 70).getRGB());

            String bindText = isListening ? "> PRESS ANY KEY <"
                    : "Key: " + Keyboard.getKeyName(selectedModule.getKey());
            int bindTextColor = isListening ? 0xFFFFFF99 : 0xFFFFFFFF;

            RenderUtils.drawString(mc.fontRendererObj, bindText,
                    bindBoxX + (bindBoxWidth - mc.fontRendererObj.getStringWidth(bindText)) / 2,
                    bindBoxY + (bindBoxHeight - mc.fontRendererObj.FONT_HEIGHT) / 2, bindTextColor);

            settingsY += bindBoxHeight + 20;

            // Show settings for the selected module
            if (selectedModule.getSettings() != null && !selectedModule.getSettings().isEmpty()) {
                RenderUtils.drawString(mc.fontRendererObj, "Module Settings", settingsX, settingsY, textColor);
                RenderUtils.drawRect(settingsX, settingsY + 15, width - leftPanelWidth - (rightPanelPadding * 2), 1,
                        new Color(60, 60, 70).getRGB());
                settingsY += 25;

                // Draw each setting with improved visuals
                for (Setting<?> setting : selectedModule.getSettings()) {
                    if (setting instanceof BooleanSetting) {
                        drawBooleanSetting((BooleanSetting) setting, settingsX, settingsY);
                        settingsY += 25;
                    } else if (setting instanceof SliderSetting) {
                        drawSliderSetting((SliderSetting) setting, settingsX, settingsY, mouseX);
                        settingsY += 35;
                    } else if (setting instanceof DoubleSliderSetting) {
                        drawDoubleSliderSetting((DoubleSliderSetting) setting, settingsX, settingsY, mouseX);
                        settingsY += 35;
                    } else {
                        RenderUtils.drawString(mc.fontRendererObj, setting.getName() + ": " + setting.getValue(),
                                settingsX,
                                settingsY, subTextColor);
                        settingsY += 20;
                    }
                }
            } else {
                RenderUtils.drawString(mc.fontRendererObj, "No settings available.", settingsX, settingsY,
                        subTextColor);
            }
        } else {
            // No module selected
            String noModuleText = "Select a module to view settings";
            RenderUtils.drawString(mc.fontRendererObj, noModuleText,
                    x + leftPanelWidth + (width - leftPanelWidth - mc.fontRendererObj.getStringWidth(noModuleText)) / 2,
                    y + tabHeight + (height - tabHeight) / 2 - 5, subTextColor);
        }
    }

    private void drawBooleanSetting(BooleanSetting setting, int x, int y) {
        // Setting name
        RenderUtils.drawString(mc.fontRendererObj, setting.getName(), x, y, textColor);

        // Toggle switch
        int toggleWidth = 36;
        int toggleHeight = 16;
        int toggleX = x + 150;
        int toggleY = y;

        boolean value = setting.getValue();

        // Background
        RenderUtils.drawRoundedRect(toggleX, toggleY, toggleWidth, toggleHeight, toggleHeight / 2,
                new Color(50, 50, 55).getRGB());

        // Knob
        int knobSize = toggleHeight - 4;
        int knobX = value ? toggleX + toggleWidth - knobSize - 2 : toggleX + 2;
        RenderUtils.drawRoundedRect(knobX, toggleY + 2, knobSize, knobSize, knobSize / 2,
                value ? new Color(65, 165, 80).getRGB() : new Color(180, 60, 60).getRGB());

        setting.setRenderBounds(toggleX, toggleY, toggleWidth, toggleHeight);
    }

    private void drawSliderSetting(SliderSetting setting, int x, int y, int mouseX) {
        // Setting name and value
        RenderUtils.drawString(mc.fontRendererObj, setting.getName(), x, y, textColor);
        String valueText = String.format("%.1f", setting.getValue());
        RenderUtils.drawString(mc.fontRendererObj, valueText, x + 240 - mc.fontRendererObj.getStringWidth(valueText), y,
                subTextColor);

        y += 15;

        // Slider
        int sliderWidth = 240;
        int sliderHeight = 8;

        // Background
        RenderUtils.drawRoundedRect(x, y, sliderWidth, sliderHeight, sliderHeight / 2, new Color(50, 50, 55).getRGB());

        // Filled portion
        double percent = (setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
        int fillWidth = Math.max(4, (int) (percent * sliderWidth));
        RenderUtils.drawRoundedRect(x, y, fillWidth, sliderHeight, sliderHeight / 2, accentColor);

        // Knob
        int knobSize = 12;
        int knobX = x + fillWidth - knobSize / 2;
        int knobY = y + (sliderHeight - knobSize) / 2;
        RenderUtils.drawFilledCircle(knobX + knobSize / 2, knobY + knobSize / 2, knobSize / 2,
                new Color(220, 220, 220).getRGB());

        // Handle hover/preview when mouse is over the slider
        if (mouseX >= x && mouseX <= x + sliderWidth && Mouse.getDY() >= y && Mouse.getDY() <= y + sliderHeight
                && !Mouse.isButtonDown(0)) {
            // Preview value at mouse position
            double hoverPercent = (mouseX - x) / (double) sliderWidth;
            double hoverValue = setting.getMin() + hoverPercent * (setting.getMax() - setting.getMin());
            hoverValue = Math.round(hoverValue / setting.getIncrement()) * setting.getIncrement();

            String hoverText = String.format("%.1f", hoverValue);
            RenderUtils.drawString(mc.fontRendererObj, hoverText,
                    mouseX - mc.fontRendererObj.getStringWidth(hoverText) / 2,
                    y - 15, 0xCCFFFFFF);
        }

        setting.setRenderBounds(x, y, sliderWidth, sliderHeight);
    }

    private void drawDoubleSliderSetting(DoubleSliderSetting setting, int x, int y, int mouseX) {
        // Setting name and range
        RenderUtils.drawString(mc.fontRendererObj, setting.getName(), x, y, textColor);
        String valueText = String.format("%.1f - %.1f", setting.getMinValue(), setting.getMaxValue());
        RenderUtils.drawString(mc.fontRendererObj, valueText, x + 240 - mc.fontRendererObj.getStringWidth(valueText), y,
                subTextColor);

        y += 15;

        // Slider
        int sliderWidth = 240;
        int sliderHeight = 8;

        // Background
        RenderUtils.drawRoundedRect(x, y, sliderWidth, sliderHeight, sliderHeight / 2, new Color(50, 50, 55).getRGB());

        // Calculate positions
        double minPercent = (setting.getMinValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
        double maxPercent = (setting.getMaxValue() - setting.getMin()) / (setting.getMax() - setting.getMin());

        int minPos = x + (int) (minPercent * sliderWidth);
        int maxPos = x + (int) (maxPercent * sliderWidth);

        // Filled portion
        if (maxPos > minPos) {
            RenderUtils.drawRoundedRect(minPos, y, maxPos - minPos, sliderHeight, 0, accentColor);
        }

        // Knobs
        int knobSize = 12;

        // Min knob
        RenderUtils.drawFilledCircle(minPos, y + sliderHeight / 2, knobSize / 2, new Color(220, 220, 220).getRGB());

        // Max knob
        RenderUtils.drawFilledCircle(maxPos, y + sliderHeight / 2, knobSize / 2, new Color(220, 220, 220).getRGB());

        setting.setRenderBounds(x, y, sliderWidth, sliderHeight);
    }

    private void handleScroll() {
        int scroll = Mouse.getDWheel();
        if (scroll != 0) {
            if (scroll > 0) {
                scrollY -= 20;
            } else {
                scrollY += 20;
            }
            scrollY = MathHelper.clamp_int(scrollY, 0, maxScrollY);
        }
    }

    private void handleSliderDragging(int mouseX, int mouseY) {
        if (Mouse.isButtonDown(0) && draggingSetting != null) {
            int[] bounds = draggingSetting.getRenderBounds();
            if (bounds != null) {
                if (draggingSetting instanceof SliderSetting) {
                    SliderSetting slider = (SliderSetting) draggingSetting;
                    double percent = MathHelper.clamp_double((mouseX - bounds[0]) / (double) bounds[2], 0, 1);
                    double value = slider.getMin() + percent * (slider.getMax() - slider.getMin());
                    value = Math.round(value / slider.getIncrement()) * slider.getIncrement();
                    value = MathHelper.clamp_double(value, slider.getMin(), slider.getMax());
                    slider.setValue(value);
                } else if (draggingSetting instanceof DoubleSliderSetting) {
                    DoubleSliderSetting dslider = (DoubleSliderSetting) draggingSetting;
                    double percent = MathHelper.clamp_double((mouseX - bounds[0]) / (double) bounds[2], 0, 1);
                    double value = dslider.getMin() + percent * (dslider.getMax() - dslider.getMin());
                    value = Math.round(value / dslider.getIncrement()) * dslider.getIncrement();

                    double minPercent = (dslider.getMinValue() - dslider.getMin())
                            / (dslider.getMax() - dslider.getMin());
                    double maxPercent = (dslider.getMaxValue() - dslider.getMin())
                            / (dslider.getMax() - dslider.getMin());
                    int minX = bounds[0] + (int) (minPercent * bounds[2]);
                    int maxX = bounds[0] + (int) (maxPercent * bounds[2]);

                    if (Math.abs(mouseX - minX) < Math.abs(mouseX - maxX)) {
                        // Closer to min handle
                        dslider.setMinValue(Math.min(value, dslider.getMaxValue()));
                    } else {
                        // Closer to max handle
                        dslider.setMaxValue(Math.max(value, dslider.getMinValue()));
                    }
                }
            }
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        // If listening for keybind, only handle clicks on the keybind box
        if (listeningForBind != null) {
            // Check if we click outside the keybind box to cancel binding
            int settingsY = y + tabHeight + rightPanelPadding;
            int settingsX = x + leftPanelWidth + rightPanelPadding;

            // Skip some space for module name and toggle button
            settingsY += 25 + 20 + 15;

            // Keybind label height
            settingsY += 15;

            int bindBoxWidth = 100, bindBoxHeight = 20;
            int bindBoxX = settingsX, bindBoxY = settingsY;

            if (!(mouseX >= bindBoxX && mouseX <= bindBoxX + bindBoxWidth && mouseY >= bindBoxY
                    && mouseY <= bindBoxY + bindBoxHeight)) {
                listeningForBind = null;
            }
            return;
        }

        // Handle window dragging (title bar)
        if (mouseButton == 0 && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + tabHeight) {
            dragging = true;
            dragOffsetX = mouseX - x;
            dragOffsetY = mouseY - y;
            return;
        }

        // Handle window resizing
        if (mouseButton == 0 && mouseX >= x + width - resizeCorner && mouseX <= x + width
                && mouseY >= y + height - resizeCorner
                && mouseY <= y + height) {
            resizing = true;
            return;
        }

        // Handle category tab clicks (now works with any mouse button, including left
        // click)
        int tabX = x + 100;
        int tabY = y;
        for (Category category : Category.values()) {
            int tabEndX = tabX + tabWidth;
            int tabEndY = tabY + tabHeight;
            if (mouseX >= tabX && mouseX <= tabEndX && mouseY >= tabY && mouseY <= tabEndY) {
                selectedCategory = category;
                scrollY = 0; // Reset scroll position
                selectedModule = null; // Clear selected module
                return;
            }
            tabX += tabWidth + tabPadding;
        }

        // Handle module clicks (with scrolling offset)
        boolean clickedModule = false;
        if (mouseX >= x && mouseX <= x + leftPanelWidth && mouseY >= y + tabHeight && mouseY <= y + height) {

            for (ModuleComponent md : moduleComponents) {
                if (md.getModule().getCategory() != selectedCategory)
                    continue;

                // Adjust for scrolling to check if we clicked on this module
                int moduleY = md.getY() - scrollY;
                if (mouseX >= md.getX() && mouseX <= md.getX() + md.width &&
                        mouseY >= moduleY && mouseY <= moduleY + md.height) {

                    if (mouseButton == 0) {
                        selectedModule = md.getModule();

                        // Double-click to toggle module
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastClickTime < 300) { // Double click within 300ms
                            md.getModule().toggle();
                        }
                        lastClickTime = currentTime;
                    }
                    clickedModule = true;
                    break;
                }
            }
        }

        // If we have a selected module, handle setting interactions
        if (selectedModule != null && selectedModule.getCategory() == selectedCategory && !clickedModule) {
            int settingsY = y + tabHeight + rightPanelPadding;
            int settingsX = x + leftPanelWidth + rightPanelPadding;

            // Toggle module on/off button
            int toggleWidth = 50;
            int toggleHeight = 20;
            if (mouseX >= settingsX && mouseX <= settingsX + toggleWidth && mouseY >= settingsY + 25
                    && mouseY <= settingsY + 25 + toggleHeight) {
                selectedModule.toggle();
                return;
            }

            // Skip some space for module name and toggle button
            settingsY += 25 + 20 + 15;

            // Keybind label height
            settingsY += 15;

            // Check for keybind box click
            int bindBoxWidth = 100, bindBoxHeight = 20;
            int bindBoxX = settingsX, bindBoxY = settingsY;
            if (mouseX >= bindBoxX && mouseX <= bindBoxX + bindBoxWidth && mouseY >= bindBoxY
                    && mouseY <= bindBoxY + bindBoxHeight) {
                listeningForBind = selectedModule;
                return;
            }

            settingsY += bindBoxHeight + 20;

            // Module settings header + some space
            settingsY += 25;

            // Check for setting clicks
            for (Setting<?> setting : selectedModule.getSettings()) {
                int[] bounds = setting.getRenderBounds();

                if (bounds != null && mouseX >= bounds[0] && mouseX <= bounds[0] + bounds[2] && mouseY >= bounds[1]
                        && mouseY <= bounds[1] + bounds[3]) {

                    if (setting instanceof BooleanSetting) {
                        BooleanSetting bool = (BooleanSetting) setting;
                        bool.setValue(!bool.getValue());
                        return;
                    } else if (setting instanceof SliderSetting || setting instanceof DoubleSliderSetting) {
                        draggingSetting = setting;
                        // Immediately update the value to where the user clicked
                        handleSliderDragging(mouseX, mouseY);
                        return;
                    }
                }

                // Update the y position based on the setting type
                if (setting instanceof BooleanSetting) {
                    settingsY += 25;
                } else if (setting instanceof SliderSetting || setting instanceof DoubleSliderSetting) {
                    settingsY += 35;
                } else {
                    settingsY += 20;
                }
            }
        }

        try {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        } catch (Exception e) {
            // Handle exception silently
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
        resizing = false;
        draggingSetting = null;
        super.mouseReleased(mouseX, mouseY, state);
    }

    public void refreshModules() {
        moduleComponents.clear();
        if (me.sassan.base.Base.INSTANCE != null && me.sassan.base.Base.INSTANCE.moduleRepo != null) {
            for (Module module : me.sassan.base.Base.INSTANCE.moduleRepo.list) {
                moduleComponents.add(new ModuleComponent(module, 0, 0, leftPanelWidth - 20, 24));
            }
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        // Fix for keybinding not working - properly handle key input
        if (listeningForBind != null && selectedModule != null) {
            // ESC key cancels binding without setting it to ESCAPE
            if (keyCode == Keyboard.KEY_ESCAPE) {
                listeningForBind = null;
            } else {
                // Set the key binding
                selectedModule.setKey(keyCode);
                // Clear listening state
                listeningForBind = null;
            }
            return;
        }

        // Default behavior - ESC key closes GUI
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }

        try {
            super.keyTyped(typedChar, keyCode);
        } catch (Exception e) {
            // Handle exception silently
        }
    }

    // Utility class for GL11 scissor operations
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
