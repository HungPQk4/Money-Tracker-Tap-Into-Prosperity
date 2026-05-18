package vn.edu.usth.tip.network.dto;

import java.util.List;

public class InsightRequest {

    public List<BudgetAlert> budgetAlerts;
    public List<AnomalyData> anomalies;
    public List<GoalData> goalInsights;
    public List<PatternData> patterns;

    public static class BudgetAlert {
        public String category;
        public double burnRate;
        public long forecastVnd;
        public long limitVnd;
        public int daysLeft;
    }

    public static class AnomalyData {
        public String category;
        public double zScore;
        public long thisMonthVnd;
        public long avgVnd;
    }

    public static class GoalData {
        public String name;
        public long velocityPerWeek;
        public long remainingVnd;
        public long targetDateMs;
        public long projectedDateMs;
    }

    public static class PatternData {
        public String topDayLabel;
        public double ratio;
        public long avgTopDayVnd;
    }
}
