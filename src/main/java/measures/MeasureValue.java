package main.java.measures;

import main.java.City;

import java.time.LocalDateTime;
import java.util.Objects;

public class MeasureValue {
    public City.CityName cityName;
    public LocalDateTime datetime;
    public String name;
    public Double value;
    public String unit;

    public MeasureValue(LocalDateTime datetime, City.CityName cityName, String name, Double value, String unit) {
        this.datetime = datetime;
        this.cityName = cityName;
        this.name = name;
        this.value = value;
        this.unit = unit;
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
