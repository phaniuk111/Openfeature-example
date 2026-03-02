package com.example.servicea.service;

import com.example.servicea.model.ServiceAFeatureResult;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.Value;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ServiceAFeatureEvaluationService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceAFeatureEvaluationService.class);

    private final Client client;
    private final String defaultEnvironment;

    public ServiceAFeatureEvaluationService(
            Client client,
            @org.springframework.beans.factory.annotation.Value("${app.env:dev}")
                    String defaultEnvironment) {
        this.client = client;
        this.defaultEnvironment = defaultEnvironment;
    }

    public ServiceAFeatureResult evaluate(String requestedEnvironment, String userId) {
        String effectiveEnvironment = hasText(requestedEnvironment) ? requestedEnvironment : defaultEnvironment;
        EvaluationContext context = buildContext(effectiveEnvironment, userId);
        boolean userIdPresent = hasText(userId);

        boolean newUiEnabled = safeBoolean("new-ui", false, context, effectiveEnvironment, userIdPresent);
        Integer batchSize =
                safeInteger(
                        "dataflow-batch-size",
                        100,
                        context,
                        effectiveEnvironment,
                        userIdPresent);
        String banner =
                safeString(
                        "welcome-banner",
                        "fallback-banner",
                        context,
                        effectiveEnvironment,
                        userIdPresent);
        boolean checkoutV2 =
                safeBoolean("checkout-v2", false, context, effectiveEnvironment, userIdPresent);

        return new ServiceAFeatureResult(
                effectiveEnvironment, newUiEnabled, batchSize, banner, checkoutV2);
    }

    private EvaluationContext buildContext(String environment, String userId) {
        Map<String, Value> contextValues = new HashMap<>();
        contextValues.put("environment", new Value(environment));

        if (hasText(userId)) {
            contextValues.put("userId", new Value(userId));
        }

        return new ImmutableContext(contextValues);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean safeBoolean(
            String flagKey,
            boolean defaultValue,
            EvaluationContext context,
            String environment,
            boolean userIdPresent) {
        long startNanos = System.nanoTime();
        try {
            boolean result = client.getBooleanValue(flagKey, defaultValue, context);
            logSuccess(flagKey, result, environment, userIdPresent, startNanos);
            return result;
        } catch (RuntimeException ex) {
            logError(flagKey, defaultValue, environment, userIdPresent, startNanos, ex);
            return defaultValue;
        }
    }

    private Integer safeInteger(
            String flagKey,
            Integer defaultValue,
            EvaluationContext context,
            String environment,
            boolean userIdPresent) {
        long startNanos = System.nanoTime();
        try {
            Integer result = client.getIntegerValue(flagKey, defaultValue, context);
            logSuccess(flagKey, result, environment, userIdPresent, startNanos);
            return result;
        } catch (RuntimeException ex) {
            logError(flagKey, defaultValue, environment, userIdPresent, startNanos, ex);
            return defaultValue;
        }
    }

    private String safeString(
            String flagKey,
            String defaultValue,
            EvaluationContext context,
            String environment,
            boolean userIdPresent) {
        long startNanos = System.nanoTime();
        try {
            String result = client.getStringValue(flagKey, defaultValue, context);
            logSuccess(flagKey, result, environment, userIdPresent, startNanos);
            return result;
        } catch (RuntimeException ex) {
            logError(flagKey, defaultValue, environment, userIdPresent, startNanos, ex);
            return defaultValue;
        }
    }

    private void logSuccess(
            String flagKey,
            Object result,
            String environment,
            boolean userIdPresent,
            long startNanos) {
        double durationMs = (System.nanoTime() - startNanos) / 1_000_000.0;
        logger.info(
                "flag_eval_success flagKey={} result={} env={} userIdPresent={} durationMs={}",
                flagKey,
                result,
                environment,
                userIdPresent,
                String.format("%.3f", durationMs));
    }

    private void logError(
            String flagKey,
            Object defaultValue,
            String environment,
            boolean userIdPresent,
            long startNanos,
            RuntimeException ex) {
        double durationMs = (System.nanoTime() - startNanos) / 1_000_000.0;
        logger.warn(
                "flag_eval_error flagKey={} defaultUsed={} env={} userIdPresent={} durationMs={} error={}",
                flagKey,
                defaultValue,
                environment,
                userIdPresent,
                String.format("%.3f", durationMs),
                ex.toString());
    }
}
