package org.kie.trustyai.service.payloads.data.upload;

import java.util.Arrays;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ModelInferResponsePayload extends ModelInferBasePayload {
    @JsonProperty("model_name")
    private String modelName;

    @JsonProperty("model_version")
    private String modelVersion;

    private String id;
    private Map<String, Object> parameters;
    private TensorPayload[] outputs;

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public TensorPayload[] getTensorPayloads() {
        return outputs;
    }

    public void setTensorPayloads(TensorPayload[] outputs) {
        this.outputs = outputs;
    }

    @Override
    public String toString() {
        return "ModelInferResponsePayload{" +
                "modelName='" + modelName + '\'' +
                ", modelVersion='" + modelVersion + '\'' +
                ", id='" + id + '\'' +
                ", parameters=" + parameters +
                ", outputs=" + Arrays.toString(outputs) +
                '}';
    }
}
