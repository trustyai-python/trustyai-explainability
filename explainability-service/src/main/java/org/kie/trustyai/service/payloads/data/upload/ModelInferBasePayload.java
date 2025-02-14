package org.kie.trustyai.service.payloads.data.upload;

public abstract class ModelInferBasePayload {
    private String id;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public abstract TensorPayload[] getTensorPayloads();

    public abstract void setTensorPayloads(TensorPayload[] inputs);

    @Override
    public abstract String toString();
}
