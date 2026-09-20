package com.verstile.client.setting;

public class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;

    public NumberSetting(String name, double defaultValue, double min, double max, double step) {
        super(name, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStep() {
        return step;
    }

    public void setValueClamped(double val) {
        double clamped = Math.max(min, Math.min(max, val));
        double stepped = Math.round(clamped / step) * step;
        setValue(stepped);
    }
}
