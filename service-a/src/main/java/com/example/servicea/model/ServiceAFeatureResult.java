package com.example.servicea.model;

public class ServiceAFeatureResult {

    private final String environment;
    private final boolean newUiEnabled;
    private final Integer dataflowBatchSize;
    private final String welcomeBanner;
    private final boolean checkoutV2;

    public ServiceAFeatureResult(
            String environment,
            boolean newUiEnabled,
            Integer dataflowBatchSize,
            String welcomeBanner,
            boolean checkoutV2) {
        this.environment = environment;
        this.newUiEnabled = newUiEnabled;
        this.dataflowBatchSize = dataflowBatchSize;
        this.welcomeBanner = welcomeBanner;
        this.checkoutV2 = checkoutV2;
    }

    public String getEnvironment() {
        return environment;
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

    public boolean isCheckoutV2() {
        return checkoutV2;
    }
}
