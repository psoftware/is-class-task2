package main.java.gui;

public interface ProgressHandler {
    void reportProgressText(String text);
    void increaseProgress();
    void increaseProgress(int steps);
    void setMaxProgress(int steps);
}
