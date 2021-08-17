package model;

public class Coordinates {

    private double x, y, confidence = -1;

    public Coordinates() {
    }

    public Coordinates(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public Coordinates(double x, double y, double confidence) {
        this.x = x;
        this.y = y;
        this.confidence = confidence;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    // Returns the Euclidean distance between coordinates
    public static double calculateDistance(Coordinates c1, Coordinates c2) {
        // "Naive" implementation instead of Math.hypot() to prevent overhead of checking for overflows
        return Math.sqrt(Math.pow(c1.getX() - c2.getX(), 2) + Math.pow(c1.getY() - c2.getY(), 2));
    }

    @Override
    public String toString() {
        return "(x: " + x + ", y: " + y + ")";
    }
}
