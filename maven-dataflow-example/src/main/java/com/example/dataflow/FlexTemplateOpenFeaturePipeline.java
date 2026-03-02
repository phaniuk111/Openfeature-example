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
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;

/**
 * Flex Template entrypoint.
 *
 * Expected runtime parameters:
 * --appEnv=dev|staging|prod
 * --flagFilePath=/opt/flags/flags-prod.json
 * --output=gs://bucket/path/output
 * [--input=gs://bucket/path/input.txt]
 */
public class FlexTemplateOpenFeaturePipeline {

    public static void main(String[] args) {
        FlexTemplateOptions options =
                PipelineOptionsFactory.fromArgs(args).withValidation().as(FlexTemplateOptions.class);
        options.setStreaming(false);

        Pipeline pipeline = Pipeline.create(options);

        PCollection<String> records;
        if (options.getInput() == null || options.getInput().trim().isEmpty()) {
            records = pipeline.apply("CreateSampleInput", Create.of(Arrays.asList("alpha", "beta", "gamma")));
        } else {
            records = pipeline.apply("ReadInput", TextIO.read().from(options.getInput()));
        }

        records.apply(
                        "EvaluateFlags",
                        ParDo.of(
                                new EvaluateFlagsFn(
                                        options.getAppEnv(), options.getFlagFilePath())))
                .apply("WriteOutput", TextIO.write().to(options.getOutput()).withoutSharding());

        pipeline.run().waitUntilFinish();
    }

    private static final class EvaluateFlagsFn extends DoFn<String, String> {

        private static final AtomicInteger ACTIVE_INSTANCE_COUNT = new AtomicInteger(0);

        private final String appEnv;
        private final String flagFilePath;

        private transient OpenFeatureAPI api;
        private transient Client client;
        private transient EvaluationContext evaluationContext;
        private transient boolean initialized;

        private EvaluateFlagsFn(String appEnv, String flagFilePath) {
            this.appEnv = appEnv;
            this.flagFilePath = flagFilePath;
        }

        @DoFn.Setup
        public void setup() throws Exception {
            if (flagFilePath == null || flagFilePath.trim().isEmpty()) {
                throw new IllegalArgumentException("flagFilePath is required for FILE mode.");
            }
            String normalizedPath = flagFilePath.trim();
            if (normalizedPath.startsWith("gs://")) {
                throw new IllegalArgumentException(
                        "flagFilePath must be a worker-local file path for FILE mode, not a GCS URI: "
                                + normalizedPath);
            }

            Path flagPath = Paths.get(normalizedPath).toAbsolutePath().normalize();
            if (!Files.exists(flagPath)) {
                throw new IllegalStateException("Flag file not found on worker: " + flagPath);
            }

            try {
                api = OpenFeatureAPI.getInstance();
                api.setProviderAndWait(
                        new FlagdProvider(
                                FlagdOptions.builder()
                                        .resolverType(Config.Resolver.FILE)
                                        .offlineFlagSourcePath(flagPath.toString())
                                        .deadline(500)
                                        .build()));
                client = api.getClient("dataflow-flex-template");
                evaluationContext = new ImmutableContext(Map.of("environment", new Value(appEnv)));
                initialized = true;
                ACTIVE_INSTANCE_COUNT.incrementAndGet();
            } catch (Exception ex) {
                // Ensure singleton state is cleaned up so a worker retry does not reuse a broken provider.
                if (ACTIVE_INSTANCE_COUNT.get() == 0) {
                    OpenFeatureAPI.getInstance().shutdown();
                }
                client = null;
                evaluationContext = null;
                initialized = false;
                throw ex;
            }
        }

        @DoFn.ProcessElement
        public void processElement(ProcessContext context) {
            boolean newUi = client.getBooleanValue("new-ui", false, evaluationContext);
            Integer batchSize = client.getIntegerValue("dataflow-batch-size", 100, evaluationContext);
            String banner = client.getStringValue("welcome-banner", "fallback-banner", evaluationContext);

            String line =
                    "input="
                            + context.element()
                            + ", env="
                            + appEnv
                            + ", new-ui="
                            + newUi
                            + ", dataflow-batch-size="
                            + batchSize
                            + ", welcome-banner="
                            + banner;
            context.output(line);
        }

        @DoFn.Teardown
        public void teardown() {
            if (initialized) {
                int remaining = ACTIVE_INSTANCE_COUNT.decrementAndGet();
                if (remaining <= 0) {
                    OpenFeatureAPI.getInstance().shutdown();
                    ACTIVE_INSTANCE_COUNT.set(0);
                }
            }

            client = null;
            evaluationContext = null;
            api = null;
            initialized = false;
        }
    }
}
