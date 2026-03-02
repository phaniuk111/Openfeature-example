package com.example.serviceb.model;

public class ServiceBFeatureResult {

    private final String environment;
    private final String namespace;
    private final boolean newUiEnabled;
    private final Integer dataflowBatchSize;
    private final String welcomeBanner;
    private final boolean recommendationsEngine;

    public ServiceBFeatureResult(
            String environment,
            String namespace,
            boolean newUiEnabled,
            Integer dataflowBatchSize,
            String welcomeBanner,
            boolean recommendationsEngine) {
        this.environment = environment;
        this.namespace = namespace;
        this.newUiEnabled = newUiEnabled;
        this.dataflowBatchSize = dataflowBatchSize;
        this.welcomeBanner = welcomeBanner;
        this.recommendationsEngine = recommendationsEngine;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getNamespace() {
        return namespace;
    }

    public boolean isNewUiEnabled() {
        return newUiEnabled;
    }

    public Integer getDataflowBatchSize() {
        return dataflowBatchSize;
    }

    public String getWelcomeBanner() {
        return welcomeBanner;
    }

    public boolean isRecommendationsEngine() {
        return recommendationsEngine;
    }
}
