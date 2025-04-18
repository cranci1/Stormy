package me.sassan.base.api.setting.impl;

import me.sassan.base.api.setting.Setting;

public class SliderSetting extends Setting<Double> {
    private final double min, max, increment;

    public SliderSetting(String name, double value, double min, double max, double increment) {
        super(name, value);
        this.min = min;
        this.max = max;
        this.increment = increment;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getIncrement() {
        return increment;
    }
}
