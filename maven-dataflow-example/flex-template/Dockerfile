# Choose the currently supported Dataflow Flex Template Java base image in your org.
FROM gcr.io/dataflow-templates-base/java11-template-launcher-base:latest

WORKDIR /template

# Shaded JAR built by: mvn -DskipTests package
COPY target/maven-dataflow-example-0.0.1-SNAPSHOT-all.jar /template/pipeline.jar

# Example static flag files baked into image. You can replace this with your own path/process.
COPY flags /opt/flags

ENV FLEX_TEMPLATE_JAVA_MAIN_CLASS="com.example.dataflow.FlexTemplateOpenFeaturePipeline"
ENV FLEX_TEMPLATE_JAVA_CLASSPATH="/template/pipeline.jar"
