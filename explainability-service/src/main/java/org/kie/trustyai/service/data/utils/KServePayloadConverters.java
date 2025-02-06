package org.kie.trustyai.service.data.utils;

import java.util.Collection;
import java.util.List;

import org.jboss.logging.Logger;
import org.kie.trustyai.connectors.kserve.v2.TensorConverterUtils;
import org.kie.trustyai.explainability.model.SerializableObject;
import org.kie.trustyai.service.payloads.consumer.InferenceLoggerGeneral;
import org.kie.trustyai.service.payloads.consumer.InferenceLoggerInput;
import org.kie.trustyai.service.payloads.consumer.InferenceLoggerOutput;
import org.kie.trustyai.service.payloads.consumer.partial.KServeInputPayload;
import org.kie.trustyai.service.payloads.consumer.partial.KServeOutputPayload;
import org.kie.trustyai.service.payloads.data.upload.ModelInferRequestPayload;
import org.kie.trustyai.service.payloads.data.upload.ModelInferResponsePayload;
import org.kie.trustyai.service.payloads.data.upload.TensorPayload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class KServePayloadConverters {

    private static final Logger LOG = Logger.getLogger(KServePayloadConverters.class);

    public static boolean isV1(InferenceLoggerOutput inferenceLoggerOutput) {
        boolean predExist = inferenceLoggerOutput.getRawPredictions() != null;
        boolean outputExist = inferenceLoggerOutput.getOutputs() != null;
        boolean predContents = predExist && !inferenceLoggerOutput.getRawPredictions().isEmpty();
        boolean outputContents = outputExist && !inferenceLoggerOutput.getOutputs().isEmpty();

        // definitely V1 if we have non-zero number of predictions
        if (predExist && predContents) {
            return true;
        }

        // definitely V2 if we have non-zero number of outputs
        if (outputExist && outputContents) {
            return false;
        }

        // definitely V1 if prediction is non-null and outputs is null
        if (predExist && !outputExist) {
            return true;
        }

        // definitely V2 if outputs is non-null and prediction is null
        if (outputExist && !predExist) {
            return false;
        }

        // assume V2 otherwise
        return false;
    }

    private static TensorPayload[] toTensorPayload(List<List<Object>> objects, String prefix) {
        TensorPayload[] tensorPayloads = new TensorPayload[objects.size()];
        String dtype = TensorConverterUtils.inferKServeType(objects.get(0).get(0)).toString();
        for (int i = 0; i < objects.size(); i++) {
            TensorPayload tp = new TensorPayload();
            tp.setData(objects.get(i).toArray());
            tp.setShape(new Number[] { 1, objects.get(i).size() });
            tp.setDatatype(dtype);
            tp.setName(prefix + "-" + i);
            tensorPayloads[i] = tp;
        }
        return tensorPayloads;
    }

    private static Number[] processShape(List<Integer> shape) {
        Number[] procShape = new Number[shape.size()];
        boolean reverse = UploadUtils.shapeReversalCheckInt(shape);

        int listIdx = shape.size() - 1;
        for (int i = 0; i < shape.size(); i++) {
            procShape[i] = reverse ? shape.get(listIdx) : shape.get(i);
            listIdx--;
        }
        return procShape;
    }

    public static ModelInferRequestPayload toRequestPayload(KServeInputPayload payload) throws JsonProcessingException {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        JsonNode rootNode = objectMapper.readTree(payload.getData());

        ModelInferRequestPayload payloadInfer = new ModelInferRequestPayload();
        payloadInfer.setId(payload.getId());
        if (rootNode.has("instances")) {
            InferenceLoggerInput originalFormat = objectMapper.treeToValue(rootNode, InferenceLoggerInput.class);
            TensorPayload[] tps = toTensorPayload(originalFormat.getInstances(), "feature");
            payloadInfer.setTensorPayloads(tps);
        } else {
            InferenceLoggerGeneral newFormat = objectMapper.treeToValue(rootNode, InferenceLoggerGeneral.class);
            TensorPayload[] tensorPayloads = newFormat.getInputs().stream().map(i -> {
                TensorPayload tp = new TensorPayload();
                tp.setName(i.getName());
                tp.setData(i.getInputData().stream().flatMap(Collection::stream).toArray());
                tp.setDatatype(i.getDatatype());
                tp.setShape(processShape(i.getShape()));
                return tp;
            }).toArray(TensorPayload[]::new);

            payloadInfer.setTensorPayloads(tensorPayloads);
        }
        return payloadInfer;
    }

    public static ModelInferResponsePayload toResponsePayload(KServeOutputPayload payload) throws JsonProcessingException {
        InferenceLoggerOutput ilo = payload.getData();
        ModelInferResponsePayload payloadInfer = new ModelInferResponsePayload();
        payloadInfer.setId(payload.getId());
        payloadInfer.setModelName(payload.getModelId());

        if (isV1(ilo)) {
            TensorPayload tp = new TensorPayload();
            tp.setData(ilo.getRawPredictions().stream().map(SerializableObject::getObject).toArray());
            tp.setShape(new Number[] { ilo.getRawPredictions().size() });
            tp.setDatatype(TensorConverterUtils.inferKServeType(ilo.getRawPredictions().get(0).getObject()).toString());
            tp.setName("output");
            payloadInfer.setTensorPayloads(new TensorPayload[] { tp });
        } else {
            TensorPayload[] tensorPayloads = ilo.getOutputs().stream().map(i -> {
                TensorPayload tp = new TensorPayload();
                tp.setName(i.getName());
                tp.setData(i.getData().stream().map(SerializableObject::getObject).toArray());
                tp.setDatatype(i.getDatatype());
                tp.setShape(processShape(i.getShape()));
                return tp;
            }).toArray(TensorPayload[]::new);
            payloadInfer.setTensorPayloads(tensorPayloads);
        }
        return payloadInfer;
    }
}
