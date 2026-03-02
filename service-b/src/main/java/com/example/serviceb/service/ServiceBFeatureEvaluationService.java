package com.example.serviceb.service;

import com.example.serviceb.model.ServiceBFeatureResult;
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
public class ServiceBFeatureEvaluationService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceBFeatureEvaluationService.class);

    private final Client client;
    private final String defaultEnvironment;
    private final String defaultNamespace;

    public ServiceBFeatureEvaluationService(
            Client client,
            @org.springframework.beans.factory.annotation.Value("${app.env:dev}")
                    String defaultEnvironment,
            @org.springframework.beans.factory.annotation.Value("${app.namespace:dev}")
                    String defaultNamespace) {
        this.client = client;
        this.defaultEnvironment = defaultEnvironment;
        this.defaultNamespace = defaultNamespace;
    }

    public ServiceBFeatureResult evaluate(
            String requestedEnvironment, String requestedNamespace, String userId) {
        String effectiveEnvironment = hasText(requestedEnvironment) ? requestedEnvironment : defaultEnvironment;
        String effectiveNamespace = hasText(requestedNamespace) ? requestedNamespace : defaultNamespace;
        EvaluationContext context = buildContext(effectiveEnvironment, effectiveNamespace, userId);
        boolean userIdPresent = hasText(userId);

        boolean newUiEnabled =
                safeBoolean(
                        "new-ui",
                        false,
                        context,
                        effectiveEnvironment,
                        effectiveNamespace,
                        userIdPresent);
        Integer batchSize =
                safeInteger(
                        "dataflow-batch-size",
                        100,
                        context,
                        effectiveEnvironment,
                        effectiveNamespace,
                        userIdPresent);
        String banner =
                safeString(
                        "welcome-banner",
                        "fallback-banner",
                        context,
                        effectiveEnvironment,
                        effectiveNamespace,
                        userIdPresent);
        boolean recommendationsEngine =
                safeBoolean(
                        "recommendations-engine",
                        false,
                        context,
                        effectiveEnvironment,
                        effectiveNamespace,
                        userIdPresent);

        return new ServiceBFeatureResult(
                effectiveEnvironment,
                effectiveNamespace,
                newUiEnabled,
                batchSize,
                banner,
                recommendationsEngine);
    }

    private EvaluationContext buildContext(String environment, String namespace, String userId) {
        Map<String, Value> contextValues = new HashMap<>();
        contextValues.put("environment", new Value(environment));
        contextValues.put("namespace", new Value(namespace));

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
            String namespace,
            boolean userIdPresent) {
        long startNanos = System.nanoTime();
        try {
            boolean result = client.getBooleanValue(flagKey, defaultValue, context);
            logSuccess(flagKey, result, environment, namespace, userIdPresent, startNanos);
            return result;
        } catch (RuntimeException ex) {
            logError(flagKey, defaultValue, environment, namespace, userIdPresent, startNanos, ex);
            return defaultValue;
        }
    }

    private Integer safeInteger(
            String flagKey,
            Integer defaultValue,
            EvaluationContext context,
            String environment,
            String namespace,
            boolean userIdPresent) {
        long startNanos = System.nanoTime();
        try {
            Integer result = client.getIntegerValue(flagKey, defaultValue, context);
            logSuccess(flagKey, result, environment, namespace, userIdPresent, startNanos);
            return result;
        } catch (RuntimeException ex) {
            logError(flagKey, defaultValue, environment, namespace, userIdPresent, startNanos, ex);
            return defaultValue;
        }
    }

    private String safeString(
            String flagKey,
            String defaultValue,
            EvaluationContext context,
            String environment,
            String namespace,
            boolean userIdPresent) {
        long startNanos = System.nanoTime();
        try {
            String result = client.getStringValue(flagKey, defaultValue, context);
            logSuccess(flagKey, result, environment, namespace, userIdPresent, startNanos);
            return result;
        } catch (RuntimeException ex) {
            logError(flagKey, defaultValue, environment, namespace, userIdPresent, startNanos, ex);
            return defaultValue;
        }
    }

    private void logSuccess(
            String flagKey,
            Object result,
            String environment,
            String namespace,
            boolean userIdPresent,
            long startNanos) {
        double durationMs = (System.nanoTime() - startNanos) / 1_000_000.0;
        logger.info(
                "flag_eval_success flagKey={} result={} env={} namespace={} userIdPresent={} durationMs={}",
                flagKey,
                result,
                environment,
                namespace,
                userIdPresent,
                String.format("%.3f", durationMs));
    }

    private void logError(
            String flagKey,
            Object defaultValue,
            String environment,
            String namespace,
            boolean userIdPresent,
            long startNanos,
            RuntimeException ex) {
        double durationMs = (System.nanoTime() - startNanos) / 1_000_000.0;
        logger.warn(
                "flag_eval_error flagKey={} defaultUsed={} env={} namespace={} userIdPresent={} durationMs={} error={}",
                flagKey,
                defaultValue,
                environment,
                namespace,
                userIdPresent,
                String.format("%.3f", durationMs),
                ex.toString());
    }
}
