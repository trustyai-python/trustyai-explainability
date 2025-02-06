package org.kie.trustyai.service.payloads.data.upload;

import java.util.Arrays;

public class ModelInferRequestPayload extends ModelInferBasePayload {
    private String id;
    private TensorPayload[] inputs;

    public TensorPayload[] getTensorPayloads() {
        return inputs;
    }

    public void setTensorPayloads(TensorPayload[] inputs) {
        this.inputs = inputs;
    }

    @Override
    public String toString() {
        return "ModelJsonPayload{" +
                "id='" + id + '\'' +
                ", inputs=" + Arrays.toString(inputs) +
                '}';
    }
}
