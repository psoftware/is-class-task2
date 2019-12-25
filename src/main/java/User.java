package main.java;

import java.util.Objects;

public class User {
    public enum Status{ENABLED, NOTENABLED, ADMIN};

    private String username;
    private String name;
    private String surname;
    private Status status;

    public User(String username, String name, String surname, Status status) {
        this.username = username;
        this.name = name;
        this.surname = surname;
        this.status = status;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSurname() { return surname; }
    public void setSurname(String surname) { this.surname = surname; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    @Override
    public String toString() {
        return "Username: " + username + ", Name: " + name + ", Surname: " + surname +  ", Status: " + status.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == this)
            return true;
        if(obj == null || obj.getClass() != this.getClass())
            return false;
        User user = (User)obj;
        return Objects.equals(user.getUsername(), this.getUsername())
                && Objects.equals(user.getName(), this.getName())
                && Objects.equals(user.getSurname(), this.getSurname())
                && Objects.equals(user.getStatus(), this.getStatus());
    }
}
