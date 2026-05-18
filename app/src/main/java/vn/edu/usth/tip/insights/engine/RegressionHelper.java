package vn.edu.usth.tip.insights.engine;

public class RegressionHelper {

    public static class OlsResult {
        public final double beta0;
        public final double beta1;

        OlsResult(double beta0, double beta1) {
            this.beta0 = beta0;
            this.beta1 = beta1;
        }
    }

    /**
     * Ordinary Least Squares y = β₀ + β₁·x
     * Tất cả biến cộng dồn PHẢI là double — tránh Silent Overflow với số VNĐ lớn
     * (30 × 50.000.000 = 1.5 tỷ > Integer.MAX_VALUE).
     * Yêu cầu x.length == y.length >= 2.
     */
    public static OlsResult fit(int[] x, double[] y) {
        int n = x.length;
        // Guard: cần ít nhất 2 điểm để vẽ đường thẳng
        // Mẫu số β₁ = n·ΣX² - (ΣX)² = 0 khi n=1 → ArithmeticException
        if (n < 2) return null;

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX  += x[i];
            sumY  += y[i];
            sumXY += (double) x[i] * y[i];
            sumX2 += (double) x[i] * x[i];
        }

        double denom = n * sumX2 - sumX * sumX;
        if (Math.abs(denom) < 1.0) return null; // tránh chia cho 0

        double beta1 = (n * sumXY - sumX * sumY) / denom;
        double beta0 = (sumY - beta1 * sumX) / n;
        return new OlsResult(beta0, beta1);
    }

    /**
     * Sample standard deviation (mẫu số N-1).
     * Trả về 0.0 nếu values.length < 2.
     */
    public static double sampleStdDev(long[] values) {
        int n = values.length;
        if (n < 2) return 0.0;
        double mean = 0;
        for (long v : values) mean += v;
        mean /= n;
        double sumSq = 0;
        for (long v : values) {
            double diff = v - mean;
            sumSq += diff * diff;
        }
        return Math.sqrt(sumSq / (n - 1));
    }

    public static double mean(long[] values) {
        if (values.length == 0) return 0;
        double sum = 0;
        for (long v : values) sum += v;
        return sum / values.length;
    }
}
