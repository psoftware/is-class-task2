package main.java;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class City {
    private CityName cityName;
    private Coords coords;

    public City(String country, String city, Coords coords){
        this(new CityName(country, city), coords);
    }

    public City(CityName cityName, Coords coords){
        this.coords = coords;
        this.cityName = cityName;
    }

    public String getCountry() {
        return cityName.getCountry();
    }

    public String getCity() {
        return cityName.getCity();
    }

    public CityName getCityName() {
        return cityName;
    }

    public Coords getCoords() {
        return coords;
    }

    @Override
    public String toString() {
        return cityName.getCity() + ',' + cityName.getCountry() + " at " + ((coords != null) ? coords.toString() : "null");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        City city1 = (City) o;
        return Objects.equals(cityName, city1.cityName) &&
                Objects.equals(coords, city1.coords);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cityName, coords);
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

        @Override
        public String toString() {
            return "(" + lat + ',' + lon + ')';
        }
    }

    public static class CityName {
        private String country;
        private String city;

        public CityName(String country, String city){
            this.city = city;
            this.country = country;
        }

        public String getCountry() {
            return country;
        }

        public String getCity() {
            return city;
        }

        @Override
        public int hashCode() {
            return Objects.hash(country, city);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CityName cityName = (CityName) o;
            return Objects.equals(country, cityName.country) &&
                    Objects.equals(city, cityName.city);
        }

        @Override
        public String toString() {
            return city + ',' + country;
        }
    }
}
