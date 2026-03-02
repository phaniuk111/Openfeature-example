package com.example.servicea.web;

import com.example.servicea.model.ServiceAFeatureResult;
import com.example.servicea.service.ServiceAFeatureEvaluationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flags")
public class FeatureController {

    private final ServiceAFeatureEvaluationService featureEvaluationService;

    public FeatureController(ServiceAFeatureEvaluationService featureEvaluationService) {
        this.featureEvaluationService = featureEvaluationService;
    }

    @GetMapping
    public Map<String, Object> getFlags(
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String userId) {
        ServiceAFeatureResult result =
                featureEvaluationService.evaluate(environment, namespace, userId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("service", "service-a");
        payload.put("environment", result.getEnvironment());
        payload.put("namespace", result.getNamespace());
        payload.put("newUiEnabled", result.isNewUiEnabled());
        payload.put("dataflowBatchSize", result.getDataflowBatchSize());
        payload.put("welcomeBanner", result.getWelcomeBanner());
        payload.put("checkoutV2", result.isCheckoutV2());
        return payload;
    }
}
