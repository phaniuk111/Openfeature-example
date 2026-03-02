package com.example.shared.openfeature.config;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenFeatureConfig {

    private static final Logger logger = LoggerFactory.getLogger(OpenFeatureConfig.class);

    @Value("${app.env:dev}")
    private String appEnv;

    @Value("${app.flags.mode:per-env}")
    private String flagMode;

    @Value("${app.flags.base-path:}")
    private String flagsBasePath;

    @Value("${app.flags.base-paths:}")
    private String flagsBasePaths;

    @Value("${app.flags.explicit-file:}")
    private String explicitFlagFile;

    @Value("${app.flags.deadline-ms:500}")
    private int deadlineMs;

    @Bean(destroyMethod = "shutdown")
    public OpenFeatureAPI openFeatureApi() {
        Path flagFile = resolveFlagFile();

        FlagdProvider provider =
                new FlagdProvider(
                        FlagdOptions.builder()
                                .resolverType(Config.Resolver.FILE)
                                .offlineFlagSourcePath(flagFile.toString())
                                .deadline(deadlineMs)
                                .build());

        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        try {
            api.setProviderAndWait(provider);
            logger.info(
                    "OpenFeature initialized with flagd FILE mode. mode={}, appEnv={}, file={}",
                    flagMode,
                    appEnv,
                    flagFile);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not initialize OpenFeature provider", ex);
        }
        return api;
    }

    @Bean
    public Client openFeatureClient(OpenFeatureAPI openFeatureApi) {
        return openFeatureApi.getClient("file-mode-demo");
    }

    private Path resolveFlagFile() {
        if (hasText(explicitFlagFile)) {
            Path explicitPath = Paths.get(explicitFlagFile).toAbsolutePath().normalize();
            if (!Files.exists(explicitPath)) {
                throw new IllegalStateException("Flag file not found: " + explicitPath);
            }
            return explicitPath;
        }

        String fileName;
        if ("targeting".equalsIgnoreCase(flagMode)) {
            fileName = "flags-targeting.json";
        } else {
            fileName = "flags-" + appEnv + ".json";
        }

        List<String> basePaths = resolveBasePaths();
        List<Path> attempted = new ArrayList<>();
        for (String basePath : basePaths) {
            Path candidate = Paths.get(basePath, fileName).toAbsolutePath().normalize();
            attempted.add(candidate);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException("Flag file not found. Checked paths: " + attempted);
    }

    private List<String> resolveBasePaths() {
        if (hasText(flagsBasePath)) {
            return List.of(flagsBasePath.trim());
        }
        if (hasText(flagsBasePaths)) {
            return Arrays.stream(flagsBasePaths.split(","))
                    .map(String::trim)
                    .filter(this::hasText)
                    .collect(Collectors.toList());
        }
        return List.of("flags");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
