package main.java;

import java.util.Arrays;
import java.util.List;

public class City {
    private String country;
    private String city;
    private Coords coords;

    public City(String country, String city, Coords coords){
        this.coords = coords;
        this.city = city;
        this.country = country;
    }

    public String getCountry() {
        return country;
    }

    public String getCity() {
        return city;
    }

    public Coords getCoords() {
        return coords;
    }

    public static class Coords {
        public double lat, lon;
        public Coords(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }

        public List<Double> asList() {
            return Arrays.asList(lat, lon);
        }
    }
}
