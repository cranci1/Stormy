package me.sassan.base.api.module;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.weavemc.loader.api.event.EventBus;
import me.sassan.base.api.setting.Setting;
import java.util.ArrayList;
import java.util.List;

/**
 * @author sassan
 *         18.11.2023, 2023
 */
@Getter
@Setter
public class Module {
    public enum Category {
        COMBAT, PLAYER, VISUAL, CLIENT
    }

    private String name;
    private String description;
    public int key;
    public boolean enabled;
    protected Minecraft mc = Minecraft.getMinecraft();
    protected List<Setting<?>> settings = new ArrayList<>();
    private final Category category;

    public Module(String name, String description, int key, Category category) {
        this.name = name;
        this.description = description;
        this.key = key;
        this.category = category;
    }

    public Module(String name, String description, int key) {
        this.name = name;
        this.description = description;
        this.key = key;
        this.category = null; // Default category if not provided
    }

    public void toggle() {
        enabled = !enabled;

        if (this.enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    public void onEnable() {
        EventBus.subscribe(this);
    }

    public void onDisable() {
        EventBus.unsubscribe(this);
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }

    protected void addSetting(Setting<?> setting) {
        settings.add(setting);
    }

    public Category getCategory() {
        return category;
    }
}
