package com.example.serviceb.web;

import com.example.serviceb.model.ServiceBFeatureResult;
import com.example.serviceb.service.ServiceBFeatureEvaluationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flags")
public class FeatureController {

    private final ServiceBFeatureEvaluationService featureEvaluationService;

    public FeatureController(ServiceBFeatureEvaluationService featureEvaluationService) {
        this.featureEvaluationService = featureEvaluationService;
    }

    @GetMapping
    public Map<String, Object> getFlags(
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String userId) {
        ServiceBFeatureResult result = featureEvaluationService.evaluate(environment, userId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("service", "service-b");
        payload.put("environment", result.getEnvironment());
        payload.put("newUiEnabled", result.isNewUiEnabled());
        payload.put("dataflowBatchSize", result.getDataflowBatchSize());
        payload.put("welcomeBanner", result.getWelcomeBanner());
        payload.put("recommendationsEngine", result.isRecommendationsEngine());
        return payload;
    }
}
