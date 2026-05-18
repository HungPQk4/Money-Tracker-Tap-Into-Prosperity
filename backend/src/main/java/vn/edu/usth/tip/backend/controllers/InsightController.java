package vn.edu.usth.tip.backend.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.usth.tip.backend.dto.ai.InsightRequest;
import vn.edu.usth.tip.backend.dto.ai.InsightResponse;
import vn.edu.usth.tip.backend.services.InsightService;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;

    @PostMapping("/insights")
    public ResponseEntity<InsightResponse> generateInsights(@RequestBody InsightRequest request) {
        log.info("POST /api/ai/insights — budgetAlerts={}, anomalies={}, goals={}, patterns={}",
                request.getBudgetAlerts() != null ? request.getBudgetAlerts().size() : 0,
                request.getAnomalies()    != null ? request.getAnomalies().size()    : 0,
                request.getGoalInsights() != null ? request.getGoalInsights().size() : 0,
                request.getPatterns()     != null ? request.getPatterns().size()     : 0);
        return ResponseEntity.ok(insightService.generateInsights(request));
    }
}
