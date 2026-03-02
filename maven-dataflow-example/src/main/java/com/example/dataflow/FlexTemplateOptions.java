package com.example.dataflow;

import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.Validation;

public interface FlexTemplateOptions extends DataflowPipelineOptions {

    @Description("Application environment, for example: dev, staging, prod")
    @Default.String("dev")
    String getAppEnv();

    void setAppEnv(String value);

    @Description("Local path to the flag JSON file inside each worker container")
    @Validation.Required
    String getFlagFilePath();

    void setFlagFilePath(String value);

    @Description("Optional input text file. If empty, demo records are generated.")
    @Default.String("")
    String getInput();

    void setInput(String value);

    @Description("Output path (for example, gs://bucket/path/output-prefix)")
    @Validation.Required
    String getOutput();

    void setOutput(String value);
}
