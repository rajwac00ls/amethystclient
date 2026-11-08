package hack.echo.client.features.settings.impl;

import hack.echo.client.features.settings.Setting;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Predicate;

@Getter
@Setter
public class AngleSetting extends Setting {
    private float value;
    private float minValue;
    private float maxValue;
    private float increment;
    private CharSequence suffixSequence = "\u00B0";
    private String suffix = "\u00B0";

    public AngleSetting(CharSequence name, float defaultValue, float minValue, float maxValue, float increment) {
        super(name);
        this.value = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.increment = increment;
    }

    public AngleSetting(CharSequence name, float defaultValue, float minValue, float maxValue, float increment,
            Predicate<Object> dependency) {
        this(name, defaultValue, minValue, maxValue, increment);
        this.setDependency(dependency);
    }

    public AngleSetting(String name, float defaultValue, float minValue, float maxValue, float increment) {
        this((CharSequence) name, defaultValue, minValue, maxValue, increment);
    }

    @Override
    public String getTypeId() {
        return "ang";
    }

    public void setValue(float value) {
        this.value = Math.max(minValue, Math.min(maxValue, value));
        notifyChange();
    }

    public void setValueNoNotify(float value) {
        this.value = Math.max(minValue, Math.min(maxValue, value));
    }

    public void increment() {
        setValue(Math.min(value + increment, maxValue));
    }

    public void decrement() {
        setValue(Math.max(value - increment, minValue));
    }
}
