package model;

import positioning.DistanceMethod;
import positioning.DistanceModel;
import positioning.PositioningMethod;

import java.util.Objects;

public class Parameters {

    private int windowSize;
    private DistanceMethod distanceMethod;
    private DistanceModel distanceModel;
    private double pathLossExponent;

    private PositioningMethod positioningMethod;
    private double weightExponent;
    private double pdfSharpness;

    public Parameters() {

    }

    public Parameters(Parameters parameters) {
        this.windowSize = parameters.windowSize;
        this.distanceMethod = parameters.distanceMethod;
        this.distanceModel = parameters.distanceModel;
        this.pathLossExponent = parameters.pathLossExponent;

        this.positioningMethod = parameters.positioningMethod;
        this.weightExponent = parameters.weightExponent;
        this.pdfSharpness = parameters.pdfSharpness;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(int windowSize) {
        this.windowSize = windowSize;
    }

    public DistanceMethod getDistanceMethod() {
        return distanceMethod;
    }

    public void setDistanceMethod(DistanceMethod distanceMethod) {
        this.distanceMethod = distanceMethod;
    }

    public DistanceModel getDistanceModel() {
        return distanceModel;
    }

    public void setDistanceModel(DistanceModel distanceModel) {
        this.distanceModel = distanceModel;
    }

    public double getPathLossExponent() {
        return pathLossExponent;
    }

    public void setPathLossExponent(double pathLossExponent) {
        this.pathLossExponent = pathLossExponent;
    }

    public PositioningMethod getPositioningMethod() {
        return positioningMethod;
    }

    public void setPositioningMethod(PositioningMethod positioningMethod) {
        this.positioningMethod = positioningMethod;
    }

    public double getWeightExponent() {
        return weightExponent;
    }

    public void setWeightExponent(double weightExponent) {
        this.weightExponent = weightExponent;
    }

    public double getPdfSharpness() {
        return pdfSharpness;
    }

    public void setPdfSharpness(double pdfSharpness) {
        this.pdfSharpness = pdfSharpness;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Parameters that = (Parameters) o;
        return windowSize == that.windowSize &&
                Double.compare(that.pathLossExponent, pathLossExponent) == 0 &&
                Double.compare(that.weightExponent, weightExponent) == 0 &&
                Double.compare(that.pdfSharpness, pdfSharpness) == 0 &&
                distanceMethod == that.distanceMethod &&
                distanceModel == that.distanceModel &&
                positioningMethod == that.positioningMethod;
    }

    @Override
    public int hashCode() {
        return Objects.hash(windowSize, distanceMethod, distanceModel, pathLossExponent, positioningMethod, weightExponent, pdfSharpness);
    }

    @Override
    public String toString() {
        return "Parameters{" +
                "windowSize=" + windowSize +
                ", distanceMethod=" + distanceMethod +
                ", distanceModel=" + distanceModel +
                ", pathLossExponent=" + String.format("%.1f", pathLossExponent) +
                ", positioningMethod=" + positioningMethod +
                ", weightExponent=" + weightExponent +
                ", pdfSharpness=" + pdfSharpness +
                '}';
    }

    public String toFileName() {
        return "windowSize=" + windowSize +
                "_distanceMethod=" + (distanceMethod == null || windowSize == 1 ? -1 : distanceMethod) +
                "_distanceModel=" + (distanceModel == null ? -1 : distanceModel) +
                "_pathLossExponent=" + String.format("%.1f", pathLossExponent) +
                "_positioningMethod=" + (positioningMethod == null ? -1 : positioningMethod) +
                "_weightExponent=" + weightExponent +
                "_pdfSharpness=" + pdfSharpness;
    }
}
