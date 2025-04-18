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
 * Client GUI with category dropdowns and module management
 * Based on the Berry client design
 */
public class CGui extends GuiScreen {
    private final int backgroundColor = new Color(0, 0, 0, 180).getRGB();
    private final int categoryTabColor = new Color(0, 0, 0, 200).getRGB();
    private final int categoryHoverColor = new Color(0, 0, 0, 240).getRGB();
    private final int accentColor = new Color(30, 144, 255).getRGB();
    private final int accentColorDarker = new Color(20, 120, 220).getRGB();
    private final int textColor = new Color(255, 255, 255).getRGB();
    private final int textColorDarker = new Color(180, 180, 180).getRGB();
    private final int panelColor = new Color(0, 0, 0, 220).getRGB();

    // GUI dimensions
    private final int categoryTabWidth = 120;
    private final int categoryTabHeight = 20;
    private final int moduleHeight = 25;
    private final int moduleExpandedHeight = 130;
    private final int padding = 5;

    private List<CategoryPanel> categoryPanels = new ArrayList<>();
    private Map<Category, List<ModuleComponent>> moduleMap = new HashMap<>();
    private Map<Category, Integer> scrollOffsets = new HashMap<>();
    private Map<Category, Boolean> expandedCategories = new HashMap<>();
    private Map<Module, Boolean> expandedModules = new HashMap<>();
    private Map<Module, Integer> moduleSettingsScroll = new ConcurrentHashMap<>();

    private Category draggingCategory = null;
    private int dragX, dragY;
    private Module selectedModule = null;
    private Setting<?> draggingSetting = null;
    private Module bindingModule = null;

    public CGui() {
        initializeGUI();
    }

    private void initializeGUI() {
        int x = 10;

        for (Category category : Category.values()) {
            CategoryPanel panel = new CategoryPanel(category, x, 10, categoryTabWidth, 300);
            categoryPanels.add(panel);
            expandedCategories.put(category, false);
            scrollOffsets.put(category, 0);
            x += categoryTabWidth + 10;

            moduleMap.put(category, new ArrayList<>());
        }

        refreshModules();
    }

    public void refreshModules() {
        for (Category category : Category.values()) {
            moduleMap.get(category).clear();
        }

        if (me.sassan.base.Base.INSTANCE != null && me.sassan.base.Base.INSTANCE.moduleRepo != null) {
            for (Module module : me.sassan.base.Base.INSTANCE.moduleRepo.list) {
                Category category = module.getCategory();
                List<ModuleComponent> categoryModules = moduleMap.get(category);

                if (categoryModules != null) {
                    ModuleComponent moduleComponent = new ModuleComponent(module, 0, 0, categoryTabWidth - 10,
                            moduleHeight);
                    categoryModules.add(moduleComponent);
                    expandedModules.put(module, false);
                }
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawGlassBackground();

        for (CategoryPanel panel : categoryPanels) {
            panel.drawPanel(mouseX, mouseY);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawGlassBackground() {
        ScaledResolution sr = new ScaledResolution(mc);
        int w = sr.getScaledWidth();
        int h = sr.getScaledHeight();

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int glassColor = new Color(255, 255, 255, 70).getRGB();
        RenderUtils.drawRoundedRect(0, 0, w, h, 16, glassColor);

        GL11.glPopAttrib();
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (bindingModule != null) {
            bindingModule = null;
            return;
        }

        for (CategoryPanel panel : categoryPanels) {
            if (panel.isTabHovered(mouseX, mouseY)) {
                if (mouseButton == 0) {
                    Category category = panel.getCategory();
                    expandedCategories.put(category, !expandedCategories.get(category));
                    return;
                } else if (mouseButton == 1) {
                    draggingCategory = panel.getCategory();
                    dragX = mouseX - panel.getX();
                    dragY = mouseY - panel.getY();
                    return;
                }
            }
        }

        for (CategoryPanel panel : categoryPanels) {
            Category category = panel.getCategory();
            if (expandedCategories.get(category)) {
                List<ModuleComponent> modules = moduleMap.get(category);
                int moduleY = panel.getY() + categoryTabHeight + padding - scrollOffsets.get(category);

                for (ModuleComponent moduleComponent : modules) {
                    int moduleHeight = expandedModules.get(moduleComponent.getModule()) ? this.moduleExpandedHeight
                            : this.moduleHeight;

                    if (mouseX >= panel.getX() + padding && mouseX <= panel.getX() + categoryTabWidth - padding &&
                            mouseY >= moduleY && mouseY <= moduleY + moduleHeight) {

                        if (mouseY <= moduleY + this.moduleHeight) {
                            if (mouseButton == 0) {
                                moduleComponent.getModule().toggle();
                                return;
                            } else if (mouseButton == 1) {
                                boolean expanded = expandedModules.get(moduleComponent.getModule());
                                expandedModules.put(moduleComponent.getModule(), !expanded);
                                selectedModule = moduleComponent.getModule();
                                return;
                            }
                        }

                        if (expandedModules.get(moduleComponent.getModule())) {
                            handleSettingClick(moduleComponent.getModule(), panel.getX() + padding,
                                    moduleY + this.moduleHeight, mouseX, mouseY, mouseButton);
                        }

                        return;
                    }

                    moduleY += moduleHeight + padding;
                }
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void handleSettingClick(Module module, int settingsX, int settingsY, int mouseX, int mouseY,
            int mouseButton) {
        int scroll = moduleSettingsScroll.getOrDefault(module, 0);
        int currentY = settingsY + padding - scroll;

        if (mouseX >= settingsX && mouseX <= settingsX + categoryTabWidth - 20 &&
                mouseY >= currentY && mouseY <= currentY + 20) {
            bindingModule = module;
            return;
        }
        currentY += 25;

        if (module.getSettings() != null) {
            for (Setting<?> setting : module.getSettings()) {
                int settingHeight = (setting instanceof SliderSetting || setting instanceof DoubleSliderSetting) ? 35
                        : 25;
                if (mouseY >= currentY && mouseY <= currentY + settingHeight) {
                    if (setting instanceof BooleanSetting) {
                        if (mouseX >= settingsX && mouseX <= settingsX + categoryTabWidth - 20 &&
                                mouseY >= currentY && mouseY <= currentY + 20) {
                            ((BooleanSetting) setting).setValue(!((BooleanSetting) setting).getValue());
                            return;
                        }
                    } else if (setting instanceof SliderSetting) {
                        if (mouseX >= settingsX && mouseX <= settingsX + categoryTabWidth - 20 &&
                                mouseY >= currentY && mouseY <= currentY + 30) {
                            draggingSetting = setting;
                            updateSliderValue((SliderSetting) setting, settingsX, currentY, mouseX);
                            return;
                        }
                    } else if (setting instanceof DoubleSliderSetting) {
                        if (mouseX >= settingsX && mouseX <= settingsX + categoryTabWidth - 20 &&
                                mouseY >= currentY && mouseY <= currentY + 30) {
                            draggingSetting = setting;
                            updateDoubleSliderValue((DoubleSliderSetting) setting, settingsX, currentY, mouseX);
                            return;
                        }
                    }
                }
                currentY += settingHeight;
            }
        }
    }

    private void updateSliderValue(SliderSetting slider, int x, int y, int mouseX) {
        float width = categoryTabWidth - 20;
        float percent = MathHelper.clamp_float((mouseX - x) / width, 0, 1);
        double value = slider.getMin() + percent * (slider.getMax() - slider.getMin());
        value = Math.round(value / slider.getIncrement()) * slider.getIncrement();
        slider.setValue(value);
    }

    private void updateDoubleSliderValue(DoubleSliderSetting slider, int x, int y, int mouseX) {
        float width = categoryTabWidth - 20;
        float percent = MathHelper.clamp_float((mouseX - x) / width, 0, 1);
        double value = slider.getMin() + percent * (slider.getMax() - slider.getMin());
        value = Math.round(value / slider.getIncrement()) * slider.getIncrement();

        // Determine which handle is closer
        double minVal = slider.getMinValue();
        double maxVal = slider.getMaxValue();

        if (Math.abs(value - minVal) < Math.abs(value - maxVal)) {
            slider.setMinValue(Math.min(value, maxVal - slider.getIncrement()));
        } else {
            slider.setMaxValue(Math.max(value, minVal + slider.getIncrement()));
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        draggingCategory = null;
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

            for (CategoryPanel panel : categoryPanels) {
                if (expandedCategories.get(panel.getCategory()) &&
                        mouseX >= panel.getX() && mouseX <= panel.getX() + categoryTabWidth &&
                        mouseY >= panel.getY() + categoryTabHeight && mouseY <= panel.getY() + 300) {

                    int maxScroll = calculateMaxScroll(panel.getCategory());
                    int scrollAmount = scroll > 0 ? -10 : 10;
                    int currentScroll = scrollOffsets.get(panel.getCategory());
                    scrollOffsets.put(panel.getCategory(),
                            MathHelper.clamp_int(currentScroll + scrollAmount, 0, maxScroll));
                    break;
                }
            }

            for (CategoryPanel panel : categoryPanels) {
                if (expandedCategories.get(panel.getCategory())) {
                    List<ModuleComponent> modules = moduleMap.get(panel.getCategory());
                    int moduleY = panel.getY() + categoryTabHeight + padding - scrollOffsets.get(panel.getCategory());
                    for (ModuleComponent moduleComponent : modules) {
                        Module module = moduleComponent.getModule();
                        if (expandedModules.get(module)) {
                            int settingsX = panel.getX() + padding;
                            int settingsY = moduleY + moduleHeight;
                            int settingsW = categoryTabWidth - 10 - padding * 2;
                            int settingsH = moduleExpandedHeight - moduleHeight - padding * 2;

                            if (mouseX >= settingsX && mouseX <= settingsX + settingsW &&
                                    mouseY >= settingsY && mouseY <= settingsY + settingsH) {
                                int maxSettingScroll = calculateMaxSettingsScroll(module);
                                int settingScroll = moduleSettingsScroll.getOrDefault(module, 0);
                                int scrollAmount = scroll > 0 ? -15 : 15;
                                moduleSettingsScroll.put(module,
                                        MathHelper.clamp_int(settingScroll + scrollAmount, 0, maxSettingScroll));
                                return;
                            }
                        }
                        moduleY += (expandedModules.get(module) ? moduleExpandedHeight : moduleHeight) + padding;
                    }
                }
            }
        }
    }

    private int calculateMaxScroll(Category category) {
        int totalHeight = 0;
        List<ModuleComponent> modules = moduleMap.get(category);

        for (ModuleComponent moduleComponent : modules) {
            boolean expanded = expandedModules.get(moduleComponent.getModule());
            totalHeight += (expanded ? moduleExpandedHeight : moduleHeight) + padding;
        }

        return Math.max(0, totalHeight - 280);
    }

    private int calculateMaxSettingsScroll(Module module) {
        int totalHeight = 25;
        if (module.getSettings() != null) {
            for (Setting<?> setting : module.getSettings()) {
                totalHeight += (setting instanceof SliderSetting || setting instanceof DoubleSliderSetting) ? 35 : 25;
            }
        }
        int visible = moduleExpandedHeight - moduleHeight - padding * 2;
        return Math.max(0, totalHeight - visible);
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (bindingModule != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                bindingModule.setKey(Keyboard.KEY_NONE);
            } else {
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

    @Override
    public void updateScreen() {
        super.updateScreen();

        if (draggingCategory != null) {
            int mouseX = Mouse.getX() * this.width / mc.displayWidth;
            int mouseY = this.height - Mouse.getY() * this.height / mc.displayHeight - 1;

            for (CategoryPanel panel : categoryPanels) {
                if (panel.getCategory() == draggingCategory) {
                    panel.setX(mouseX - dragX);
                    panel.setY(mouseY - dragY);
                    break;
                }
            }
        }

        if (draggingSetting != null && Mouse.isButtonDown(0)) {
            int mouseX = Mouse.getX() * this.width / mc.displayWidth;

            if (draggingSetting instanceof SliderSetting) {
                for (CategoryPanel panel : categoryPanels) {
                    if (expandedCategories.get(panel.getCategory())) {
                        List<ModuleComponent> modules = moduleMap.get(panel.getCategory());
                        int moduleY = panel.getY() + categoryTabHeight + padding
                                - scrollOffsets.get(panel.getCategory());

                        for (ModuleComponent moduleComponent : modules) {
                            if (expandedModules.get(moduleComponent.getModule()) &&
                                    moduleComponent.getModule().getSettings().contains(draggingSetting)) {

                                updateSliderValue((SliderSetting) draggingSetting, panel.getX() + padding,
                                        moduleY + moduleHeight + padding, mouseX);
                                return;
                            }

                            moduleY += (expandedModules.get(moduleComponent.getModule()) ? moduleExpandedHeight
                                    : moduleHeight) + padding;
                        }
                    }
                }
            } else if (draggingSetting instanceof DoubleSliderSetting) {
                for (CategoryPanel panel : categoryPanels) {
                    if (expandedCategories.get(panel.getCategory())) {
                        List<ModuleComponent> modules = moduleMap.get(panel.getCategory());
                        int moduleY = panel.getY() + categoryTabHeight + padding
                                - scrollOffsets.get(panel.getCategory());

                        for (ModuleComponent moduleComponent : modules) {
                            if (expandedModules.get(moduleComponent.getModule()) &&
                                    moduleComponent.getModule().getSettings().contains(draggingSetting)) {

                                updateDoubleSliderValue((DoubleSliderSetting) draggingSetting, panel.getX() + padding,
                                        moduleY + moduleHeight + padding, mouseX);
                                return;
                            }

                            moduleY += (expandedModules.get(moduleComponent.getModule()) ? moduleExpandedHeight
                                    : moduleHeight) + padding;
                        }
                    }
                }
            }
        }
    }

    /**
     * Class representing a category panel in the GUI
     */
    private class CategoryPanel {
        private Category category;
        private int x, y;
        private int width, height;

        public CategoryPanel(Category category, int x, int y, int width, int height) {
            this.category = category;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public void drawPanel(int mouseX, int mouseY) {
            boolean expanded = expandedCategories.get(category);
            boolean hoveredTab = isTabHovered(mouseX, mouseY);

            int tabBgColor = hoveredTab ? categoryHoverColor : categoryTabColor;
            RenderUtils.drawRect(x, y, width, categoryTabHeight, tabBgColor);

            RenderUtils.drawRect(x, y, 2, categoryTabHeight, accentColor);

            String tabText = category.name();
            RenderUtils.drawString(mc.fontRendererObj, tabText,
                    x + (width - mc.fontRendererObj.getStringWidth(tabText)) / 2,
                    y + (categoryTabHeight - mc.fontRendererObj.FONT_HEIGHT) / 2,
                    textColor);

            String arrow = expanded ? "▼" : "▶";
            RenderUtils.drawString(mc.fontRendererObj, arrow,
                    x + width - 15,
                    y + (categoryTabHeight - mc.fontRendererObj.FONT_HEIGHT) / 2,
                    textColor);

            if (expanded) {
                RenderUtils.drawRect(x, y + categoryTabHeight, width, height - categoryTabHeight, panelColor);

                drawModules(mouseX, mouseY);
            }
        }

        private void drawModules(int mouseX, int mouseY) {
            List<ModuleComponent> modules = moduleMap.get(category);
            int currentY = y + categoryTabHeight + padding - scrollOffsets.get(category);

            GL11Util.startScissor(x, y + categoryTabHeight, width, height - categoryTabHeight);

            for (ModuleComponent moduleComponent : modules) {
                Module module = moduleComponent.getModule();
                boolean expanded = expandedModules.get(module);
                int moduleHeight = expanded ? CGui.this.moduleExpandedHeight : CGui.this.moduleHeight;

                if (currentY + moduleHeight >= y + categoryTabHeight && currentY <= y + height) {
                    boolean moduleHovered = mouseX >= x + padding && mouseX <= x + width - padding &&
                            mouseY >= currentY && mouseY <= currentY + CGui.this.moduleHeight;
                    int moduleBgColor = moduleHovered ? new Color(60, 60, 70).getRGB() : new Color(40, 40, 50).getRGB();

                    if (module.isEnabled()) {
                        RenderUtils.drawRect(x + padding, currentY, width - padding * 2,
                                CGui.this.moduleHeight, accentColorDarker);
                    } else {
                        RenderUtils.drawRect(x + padding, currentY, width - padding * 2,
                                CGui.this.moduleHeight, moduleBgColor);
                    }

                    RenderUtils.drawString(mc.fontRendererObj, module.getName(),
                            x + padding * 2,
                            currentY + (CGui.this.moduleHeight - mc.fontRendererObj.FONT_HEIGHT) / 2,
                            textColor);

                    if (!module.getSettings().isEmpty()) {
                        String arrow = expanded ? "▼" : "▶";
                        RenderUtils.drawString(mc.fontRendererObj, arrow,
                                x + width - padding * 3,
                                currentY + (CGui.this.moduleHeight - mc.fontRendererObj.FONT_HEIGHT) / 2,
                                textColorDarker);
                    }

                    if (expanded) {
                        drawModuleSettings(module, x + padding, currentY + CGui.this.moduleHeight);
                    }
                }

                currentY += moduleHeight + padding;
            }

            GL11Util.endScissor();
        }

        private void drawModuleSettings(Module module, int settingsX, int settingsY) {
            int settingsW = width - padding * 2;
            int settingsH = moduleExpandedHeight - moduleHeight - padding * 2;

            GL11Util.startScissor(settingsX, settingsY, settingsW, settingsH);

            int scroll = moduleSettingsScroll.getOrDefault(module, 0);
            int currentY = settingsY + padding - scroll;

            int buttonColor = bindingModule == module ? accentColor : new Color(50, 50, 60, 180).getRGB();
            RenderUtils.drawRoundedRect(settingsX, currentY, settingsW, 20, 8, buttonColor);

            String bindText = bindingModule == module ? "Press key..."
                    : "Bind: " + Keyboard.getKeyName(module.getKey());
            RenderUtils.drawString(mc.fontRendererObj, bindText,
                    settingsX + 8,
                    currentY + (20 - mc.fontRendererObj.FONT_HEIGHT) / 2,
                    textColor);

            currentY += 25;

            if (module.getSettings() != null) {
                for (Setting<?> setting : module.getSettings()) {
                    if (setting instanceof BooleanSetting) {
                        drawBooleanSetting((BooleanSetting) setting, settingsX, currentY);
                        currentY += 25;
                    } else if (setting instanceof SliderSetting) {
                        drawSliderSetting((SliderSetting) setting, settingsX, currentY);
                        currentY += 35;
                    } else if (setting instanceof DoubleSliderSetting) {
                        drawDoubleSliderSetting((DoubleSliderSetting) setting, settingsX, currentY);
                        currentY += 35;
                    } else {
                        RenderUtils.drawString(mc.fontRendererObj, setting.getName() + ": " + setting.getValue(),
                                settingsX + 8, currentY, textColor);
                        currentY += 25;
                    }
                }
            }

            GL11Util.endScissor();
        }

        private void drawBooleanSetting(BooleanSetting setting, int x, int y) {
            RenderUtils.drawString(mc.fontRendererObj, setting.getName(), x + 5, y, textColor);

            int toggleWidth = 30;
            int toggleHeight = 12;
            int toggleX = x + width - padding * 3 - toggleWidth;
            int toggleY = y + 4;

            boolean enabled = setting.getValue();
            int backgroundColor = new Color(40, 40, 50).getRGB();
            int toggleColor = enabled ? accentColor : new Color(100, 100, 110).getRGB();

            RenderUtils.drawRoundedRect(toggleX, toggleY, toggleWidth, toggleHeight, toggleHeight / 2, backgroundColor);

            int knobSize = toggleHeight - 2;
            int knobX = enabled ? toggleX + toggleWidth - knobSize - 1 : toggleX + 1;
            RenderUtils.drawFilledCircle(knobX + knobSize / 2, toggleY + toggleHeight / 2, knobSize / 2, toggleColor);
        }

        private void drawSliderSetting(SliderSetting setting, int x, int y) {
            RenderUtils.drawString(mc.fontRendererObj, setting.getName(), x + 5, y, textColor);
            String valueText = String.format("%.1f", setting.getValue());
            RenderUtils.drawString(mc.fontRendererObj, valueText,
                    x + width - padding * 3 - mc.fontRendererObj.getStringWidth(valueText),
                    y, textColorDarker);

            int sliderY = y + 15;
            int sliderWidth = width - padding * 4;
            int sliderHeight = 6;

            RenderUtils.drawRoundedRect(x, sliderY, sliderWidth, sliderHeight, sliderHeight / 2,
                    new Color(40, 40, 50).getRGB());

            double percent = (setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
            int fillWidth = Math.max(4, (int) (percent * sliderWidth));
            RenderUtils.drawRoundedRect(x, sliderY, fillWidth, sliderHeight, sliderHeight / 2, accentColor);

            int knobSize = 10;
            int knobX = x + fillWidth - knobSize / 2;
            int knobY = sliderY + (sliderHeight - knobSize) / 2;
            RenderUtils.drawFilledCircle(knobX + knobSize / 2, knobY + knobSize / 2, knobSize / 2, textColor);
        }

        private void drawDoubleSliderSetting(DoubleSliderSetting setting, int x, int y) {
            RenderUtils.drawString(mc.fontRendererObj, setting.getName(), x + 5, y, textColor);
            String valueText = String.format("%.1f-%.1f", setting.getMinValue(), setting.getMaxValue());
            RenderUtils.drawString(mc.fontRendererObj, valueText,
                    x + width - padding * 3 - mc.fontRendererObj.getStringWidth(valueText),
                    y, textColorDarker);

            int sliderY = y + 15;
            int sliderWidth = width - padding * 4;
            int sliderHeight = 6;

            RenderUtils.drawRoundedRect(x, sliderY, sliderWidth, sliderHeight, sliderHeight / 2,
                    new Color(40, 40, 50).getRGB());

            double minPercent = (setting.getMinValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
            double maxPercent = (setting.getMaxValue() - setting.getMin()) / (setting.getMax() - setting.getMin());

            int minPos = x + (int) (minPercent * sliderWidth);
            int maxPos = x + (int) (maxPercent * sliderWidth);

            if (maxPos > minPos) {
                RenderUtils.drawRect(minPos, sliderY, maxPos - minPos, sliderHeight, accentColor);
            }

            int knobSize = 10;
            RenderUtils.drawFilledCircle(minPos, sliderY + sliderHeight / 2, knobSize / 2, textColor);
            RenderUtils.drawFilledCircle(maxPos, sliderY + sliderHeight / 2, knobSize / 2, textColor);
        }

        public boolean isTabHovered(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + categoryTabHeight;
        }

        public Category getCategory() {
            return category;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }
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