package vn.edu.usth.tip.insights.models;

public class ForecastPoint {
    public final int day;
    public final long amountVnd;
    public final boolean isProjected;

    public ForecastPoint(int day, long amountVnd, boolean isProjected) {
        this.day = day;
        this.amountVnd = amountVnd;
        this.isProjected = isProjected;
    }
}
