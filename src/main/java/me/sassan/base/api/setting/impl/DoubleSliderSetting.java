package me.sassan.base.api.setting.impl;

import me.sassan.base.api.setting.Setting;

public class DoubleSliderSetting extends Setting<double[]> {
    private final double min, max, increment;

    public DoubleSliderSetting(String name, double minValue, double maxValue, double min, double max, double increment) {
        super(name, new double[]{minValue, maxValue});
        this.min = min;
        this.max = max;
        this.increment = increment;
    }

    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getIncrement() { return increment; }

    public double getMinValue() { return getValue()[0]; }
    public double getMaxValue() { return getValue()[1]; }

    public void setMinValue(double value) { getValue()[0] = value; }
    public void setMaxValue(double value) { getValue()[1] = value; }
    // ...no need for setValue(double[]) override...
}
