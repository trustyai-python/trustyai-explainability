package org.kie.trustyai.service.utils;

import java.util.stream.IntStream;

import org.kie.trustyai.service.payloads.data.upload.ModelInferJointPayload;
import org.kie.trustyai.service.payloads.data.upload.ModelInferRequestPayload;
import org.kie.trustyai.service.payloads.data.upload.ModelInferResponsePayload;
import org.kie.trustyai.service.payloads.data.upload.TensorPayload;

import com.google.protobuf.ByteString;

public class KserveRestPayloads {
    private static Object[][] generateDataRow(int nRows, int nCols, String datatype, int offset) {
        Object[][] generatedData = new Object[nRows][nCols];
        for (int r = 0; r < nRows; r++) {
            IntStream stream = IntStream.range(nCols * r, nCols + (nCols * r)).map(i -> i + offset);
            switch (datatype) {
                case "BOOL":
                    generatedData[r] = stream.mapToObj(i -> i % 2 == 0).toArray();
                    break;
                case "INT8":
                case "INT16":
                case "INT32":
                    generatedData[r] = stream.boxed().toArray();
                    break;
                case "INT64":
                    generatedData[r] = stream.mapToObj(i -> Long.valueOf(Integer.toString(i))).toArray();
                    break;
                case "FP32":
                    generatedData[r] = stream.mapToObj(Float::valueOf).toArray();
                    break;
                case "FP64":
                    generatedData[r] = stream.mapToObj(i -> (double) i / 2).toArray();
                    break;
                case "BYTES":
                    generatedData[r] = stream.mapToObj(i -> ByteString.copyFromUtf8(Integer.toString(i))).toArray();
                    break;
                default: // error case
                    generatedData[r] = stream.mapToObj(i -> null).toArray();
                    break;
            }
        }
        return generatedData;
    }

    public static TensorPayload generateTensor(int nRows, int nCols, String name, String datatype, int offset) {
        TensorPayload tensorPayload = new TensorPayload();
        tensorPayload.setName(name);
        tensorPayload.setShape(new Number[] { nRows, nCols });
        tensorPayload.setDatatype(datatype);
        Object[][] generatedDataRow = generateDataRow(nRows, nCols, datatype, offset);
        if (nRows == 1) {
            tensorPayload.setData(generatedDataRow[0]);
        } else {
            tensorPayload.setData(generatedDataRow);
        }
        return tensorPayload;
    }

    public static ModelInferJointPayload generatePayload(int nInputRows, int nInputCols, int nOutputCols, String datatype, String dataTag) {
        return generatePayload(nInputRows, nInputCols, nOutputCols, datatype, dataTag, 0, 0);
    }

    public static ModelInferJointPayload generatePayload(int nInputRows, int nInputCols, int nOutputCols, String datatype, String dataTag, int requestOffset, int responseOffset) {
        ModelInferJointPayload payload = new ModelInferJointPayload();
        payload.setModelName("test-model");
        payload.setDataTag(dataTag);

        ModelInferRequestPayload requestPayload = new ModelInferRequestPayload();
        requestPayload.setId("requestID");

        ModelInferResponsePayload responsePayload = new ModelInferResponsePayload();
        responsePayload.setId("requestID");
        responsePayload.setModelName("test-model__isvc-120380123");
        responsePayload.setModelVersion("1");

        requestPayload.setTensorPayloads(new TensorPayload[] { generateTensor(nInputRows, nInputCols, "input", datatype, requestOffset) });
        responsePayload.setTensorPayloads(new TensorPayload[] { generateTensor(nInputRows, nOutputCols, "output", datatype, responseOffset) });

        payload.setRequest(requestPayload);
        payload.setResponse(responsePayload);

        return payload;
    }

    public static ModelInferJointPayload generateMultiInputPayload(int nRows, int nInputCols, int nOutputCols, String datatype, String dataTag) {
        ModelInferJointPayload payload = new ModelInferJointPayload();
        payload.setModelName("test-model");
        payload.setDataTag(dataTag);

        ModelInferRequestPayload requestPayload = new ModelInferRequestPayload();
        requestPayload.setId("requestID");

        ModelInferResponsePayload responsePayload = new ModelInferResponsePayload();
        responsePayload.setId("requestID");
        responsePayload.setModelName("test-model__isvc-120380123");
        responsePayload.setModelVersion("1");

        TensorPayload[] inputs = new TensorPayload[nInputCols];
        TensorPayload[] outputs = new TensorPayload[nOutputCols];

        for (int i = 0; i < nInputCols; i++) {
            inputs[i] = generateTensor(nRows, 1, "input-" + i, datatype, 10 * i);
        }
        for (int i = 0; i < nOutputCols; i++) {
            outputs[i] = generateTensor(nRows, 1, "output-" + i, datatype, 10 * i);
        }

        requestPayload.setTensorPayloads(inputs);
        responsePayload.setTensorPayloads(outputs);

        payload.setRequest(requestPayload);
        payload.setResponse(responsePayload);

        return payload;
    }

    public static ModelInferJointPayload generateMismatchedShapeNoUniqueNameMultiInputPayload(int nRows, int nInputCols, int nOutputCols, String datatype, String dataTag) {
        ModelInferJointPayload payload = new ModelInferJointPayload();
        payload.setModelName("test-model");
        payload.setDataTag(dataTag);

        ModelInferRequestPayload requestPayload = new ModelInferRequestPayload();
        requestPayload.setId("requestID");

        ModelInferResponsePayload responsePayload = new ModelInferResponsePayload();
        responsePayload.setId("requestID");
        responsePayload.setModelName("test-model__isvc-120380123");
        responsePayload.setModelVersion("1");

        TensorPayload[] inputs = new TensorPayload[nInputCols];
        TensorPayload[] outputs = new TensorPayload[nOutputCols];

        for (int i = 0; i < nInputCols; i++) {
            inputs[i] = generateTensor(nRows + i, 1, "input", datatype, 10 * i);
        }
        for (int i = 0; i < nOutputCols; i++) {
            outputs[i] = generateTensor(nRows, 1, "output", datatype, 10 * i);
        }

        requestPayload.setTensorPayloads(inputs);
        responsePayload.setTensorPayloads(outputs);

        payload.setRequest(requestPayload);
        payload.setResponse(responsePayload);

        return payload;
    }
}
