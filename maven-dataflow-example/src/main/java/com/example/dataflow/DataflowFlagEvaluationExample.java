package com.example.dataflow;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal Maven/Dataflow-style example for OpenFeature + flagd FILE mode.
 * This can be called from pipeline setup code or inside a DoFn helper.
 */
public class DataflowFlagEvaluationExample {

    public static void main(String[] args) throws Exception {
        String appEnv = getenv("APP_ENV", "dev");
        String explicitPath = System.getenv("FLAGD_OFFLINE_FLAG_SOURCE_PATH");
        String basePath = getenv("FLAG_BASE_PATH", "flags");
        Path flagFile = resolveFlagFile(explicitPath, basePath, appEnv);

        if (!Files.exists(flagFile)) {
            throw new IllegalStateException("Flag file not found: " + flagFile);
        }

        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        FlagdProvider provider =
                new FlagdProvider(
                        FlagdOptions.builder()
                                .resolverType(Config.Resolver.FILE)
                                .offlineFlagSourcePath(flagFile.toString())
                                .deadline(500)
                                .build());

        api.setProviderAndWait(provider);

        try {
            Client client = api.getClient("dataflow-file-mode-demo");
            EvaluationContext context = buildContext(appEnv, "pipeline-worker-1");

            boolean newUiEnabled = client.getBooleanValue("new-ui", false, context);
            Integer batchSize = client.getIntegerValue("dataflow-batch-size", 100, context);
            String banner = client.getStringValue("welcome-banner", "fallback-banner", context);

            System.out.println("environment=" + appEnv);
            System.out.println("new-ui=" + newUiEnabled);
            System.out.println("dataflow-batch-size=" + batchSize);
            System.out.println("welcome-banner=" + banner);
        } finally {
            api.shutdown();
        }
    }

    private static Path resolveFlagFile(String explicitPath, String basePath, String appEnv) {
        if (hasText(explicitPath)) {
            return Paths.get(explicitPath).toAbsolutePath().normalize();
        }
        return Paths.get(basePath, "flags-" + appEnv + ".json").toAbsolutePath().normalize();
    }

    private static EvaluationContext buildContext(String environment, String workerId) {
        Map<String, Value> contextValues = new HashMap<>();
        contextValues.put("environment", new Value(environment));
        contextValues.put("workerId", new Value(workerId));
        return new ImmutableContext(contextValues);
    }

    private static String getenv(String key, String defaultValue) {
        String value = System.getenv(key);
        return hasText(value) ? value : defaultValue;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
