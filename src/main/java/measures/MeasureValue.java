package main.java.measures;

import main.java.City;

import java.time.LocalDateTime;
import java.util.Objects;

public class MeasureValue {
    public City.CityName cityName;
    public LocalDateTime datetime;
    public String name;
    public Object value;
    public String unit;

    public MeasureValue(LocalDateTime datetime, City.CityName cityName, String name, Object value, String unit) {
        this.datetime = datetime;
        this.cityName = cityName;
        this.name = name;
        this.value = value;
        this.unit = unit;
    }

    public <T> T getValue() {
        return (T)value;
    }

    @Override
    public String toString() {
        return "(" + datetime.toString() + " at " + cityName.toString() + ") " + name + ": " + value + " " + unit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cityName, datetime, name, value, unit);
    }
}
