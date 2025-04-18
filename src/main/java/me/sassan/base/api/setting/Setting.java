package me.sassan.base.api.setting;

import lombok.Getter;
import me.sassan.base.api.module.Module;

/**
 * @author sassan
 *         18.11.2023, 2023
 */
@Getter
public abstract class Setting<T> {
    private String name;
    private Module parent;
    private T value;

    // Add for GUI interaction
    private int[] renderBounds;

    public Setting(String name, T value) {
        this.name = name;
        this.value = value;

        SettingRepo.INSTANCE.list.add(this);
    }

    public void setRenderBounds(int x, int y, int width, int height) {
        this.renderBounds = new int[] { x, y, width, height };
    }

    public int[] getRenderBounds() {
        return renderBounds;
    }

    public void setValue(T value) {
        this.value = value;
    }
}
