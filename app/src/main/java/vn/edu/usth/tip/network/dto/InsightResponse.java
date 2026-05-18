package vn.edu.usth.tip.network.dto;

import java.util.List;

public class InsightResponse {

    public List<AiInsight> insights;

    public static class AiInsight {
        public String type;
        public String referenceName; // tên category — để Android map đúng icon
        public String title;
        public String body;
    }
}
