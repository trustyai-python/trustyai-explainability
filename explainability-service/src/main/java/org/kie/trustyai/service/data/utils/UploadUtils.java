package org.kie.trustyai.service.data.utils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.jboss.logging.Logger;
import org.kie.trustyai.connectors.kserve.v2.grpc.InferParameter;
import org.kie.trustyai.connectors.kserve.v2.grpc.InferTensorContents;
import org.kie.trustyai.connectors.kserve.v2.grpc.ModelInferRequest;
import org.kie.trustyai.connectors.kserve.v2.grpc.ModelInferResponse;
import org.kie.trustyai.explainability.model.tensor.Tensor;
import org.kie.trustyai.service.payloads.data.upload.ModelInferBasePayload;
import org.kie.trustyai.service.payloads.data.upload.ModelInferRequestPayload;
import org.kie.trustyai.service.payloads.data.upload.ModelInferResponsePayload;
import org.kie.trustyai.service.payloads.data.upload.TensorPayload;

import com.google.protobuf.ByteString;

public class UploadUtils {
    private static final Logger LOG = Logger.getLogger(UploadUtils.class);

    public static boolean shapeReversalCheckLong(List<Long> shape) {
        return shape.get(0) != 1 && shape.get(shape.size() - 1) != 1;
    }

    public static boolean shapeReversalCheckInt(List<Integer> shape) {
        return shape.get(0) != 1 && shape.get(shape.size() - 1) != 1;
    }

    public static List<Long> shapeProcess(Number[] payloadShape) {
        // the shapes are parsed in different directions in the kserve parser depending on codec
        List<Long> shape = Arrays.stream(payloadShape).mapToLong(Number::longValue).boxed().collect(Collectors.toList());
        if (UploadUtils.shapeReversalCheckLong(shape)) {
            Collections.reverse(shape);
        }
        return shape;
    }

    // fill a tensor builder based on provided datatype
    private static InferTensorContents.Builder getTensorBuilder(String datatype, Object[] data) {

        InferTensorContents.Builder inferTensorContentsBuilder = InferTensorContents.newBuilder();

        switch (datatype) {
            case "BOOL":
                inferTensorContentsBuilder.addAllBoolContents(Arrays.stream(data).map(v -> (Boolean) v).collect(Collectors.toList()));
                break;
            case "INT8":
            case "INT16":
            case "INT32":
                inferTensorContentsBuilder.addAllIntContents(Arrays.stream(data).map(v -> (Integer) v).collect(Collectors.toList()));
                break;
            case "INT64":
                if (data[0] instanceof Integer) {
                    inferTensorContentsBuilder.addAllInt64Contents(Arrays.stream(data).map(v -> ((Integer) v).longValue()).collect(Collectors.toList()));
                } else {
                    inferTensorContentsBuilder.addAllInt64Contents(Arrays.stream(data).map(v -> (Long) v).collect(Collectors.toList()));
                }
                break;
            case "FP32":
                inferTensorContentsBuilder.addAllFp32Contents(Arrays.stream(data).map(v -> Float.valueOf(v.toString())).collect(Collectors.toList()));
                break;
            case "FP64":
                inferTensorContentsBuilder.addAllFp64Contents(Arrays.stream(data).map(v -> (Double) v).collect(Collectors.toList()));
                break;
            case "BYTES":
                inferTensorContentsBuilder.addAllBytesContents(Arrays.stream(data).map(v -> (ByteString) v).collect(Collectors.toList()));
                break;
            default:
                throw new IllegalArgumentException("Input datatype=" + datatype + " unsupported. Must be one of BOOL, INT8/16/32/64, FP32/64, or BYTES");
        }

        return inferTensorContentsBuilder;
    }

    // get a tensor/payload parameter map based on passed json
    private static Map<String, InferParameter> getParameterMap(Map<String, Object> jsonParameters) {
        Map<String, InferParameter> parameterMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : jsonParameters.entrySet()) {
            InferParameter.Builder inferParamBuilder = InferParameter.newBuilder();
            if (entry.getValue() instanceof String) {
                inferParamBuilder.setStringParam((String) entry.getValue());
            } else if (entry.getValue() instanceof Boolean) {
                inferParamBuilder.setBoolParam((Boolean) entry.getValue());
            } else if (entry.getValue() instanceof Integer) {
                inferParamBuilder.setInt64Param((Integer) entry.getValue());
            } else {
                throw new IllegalArgumentException("Inference parameter " + entry.getKey() + " has value not of type String, Bool, or Int: " + entry.getValue().toString());
            }
            parameterMap.put(entry.getKey(), inferParamBuilder.build());
        }
        return parameterMap;
    }

    private static Object[][] transpose(Object[] objectList) {
        int ncols = ((ArrayList<Object>) objectList[0]).size();
        Object[][] objectVector = new Object[ncols][objectList.length];
        for (int i = 0; i < objectList.length; i++) {
            ArrayList<Object> sublist = (ArrayList<Object>) objectList[i];
            for (int ii = 0; ii < ncols; ii++) {
                objectVector[ii][i] = sublist.get(ii);
            }
        }
        return objectVector;
    }

    // generic cases ==================================================================================================
    private static class TensorContructionHolder {
        public TensorPayload tp;
        public Object[] vector;
        public String name;

        public TensorContructionHolder(TensorPayload tp) {
            this.tp = tp;
            this.vector = tp.getData();
            this.name = tp.getName();
        }

        public TensorContructionHolder(TensorPayload tp, Object[] vector) {
            this.tp = tp;
            this.vector = vector;
            this.name = tp.getName();
        }

        public TensorContructionHolder(TensorPayload tp, Object[] vector, String name) {
            this.tp = tp;
            this.vector = vector;
            this.name = name;
        }
    }

    private static <U extends ModelInferBasePayload> void validateMultiTensors(U payload, String payloadType, String contentType) {
        // check validity of multi-tensor payloads
        Set<String> names = new HashSet<>();
        Set<String> nameDups = new HashSet<>();
        Set<Number> firstDims = new HashSet<>();
        Set<Number> laterDims = new HashSet<>();
        for (TensorPayload tp : payload.getTensorPayloads()) {
            if (names.contains(tp.getName())) {
                nameDups.add(tp.getName());
            } else {
                names.add(tp.getName());
            }
            firstDims.add(tp.getShape()[0]);
            if (tp.getShape().length > 1) {
                laterDims.add(Tensor.vectorProduct(Arrays.stream(tp.getShape()).mapToInt(Number::intValue).toArray(), 1));
            }
        }

        // make sure all column names are unique
        List<String> errors = new ArrayList<>();
        if (names.size() != payload.getTensorPayloads().length) {
            errors.add(String.format(
                    "Each %s tensor must have unique names. However, the following duplicate names were found: %s",
                    contentType, nameDups));
        }

        // make sure all columns have matched length
        if (firstDims.size() > 1) {
            errors.add(String.format(
                    "Each %s tensor must have the same sized first dimension (e.g., shape[0]). However, the following first dimensions were passed: %s",
                    contentType, firstDims));
        }

        // assert that all columns are vectors
        if (laterDims.size() != 1 && !laterDims.contains(1)) {
            errors.add(String.format(
                    "Higher dimensional %ss (e.g., shape=[m, n] where n>1) are not yet supported.", contentType));
        }

        // report all validation errors together
        if (!errors.isEmpty()) {
            StringBuilder errorMsg = new StringBuilder();
            errorMsg.append(String.format("One or more errors were found with the inbound %s payload:", payloadType));
            for (String error : errors) {
                errorMsg.append(System.lineSeparator() + " - ");
                errorMsg.append(error);
            }
            throw new IllegalArgumentException(errorMsg.toString());
        }
    }

    private static <T, U extends ModelInferBasePayload> List<T> getBuilderInputsOrOutputs(U payload, String payloadType, String contentType, Function<TensorContructionHolder, T> tensorCreator) {
        List<T> builtPayloads = new ArrayList<>();

        // if we have multiple tensors, assume each tensor describes a data column
        if (payload.getTensorPayloads().length > 1) {
            validateMultiTensors(payload, payloadType, contentType);

            for (TensorPayload tp : payload.getTensorPayloads()) {
                Object[] vector;

                // if we have data where shape=[1, x], values=[[a], [b], [c]], transpose and flatten to shape=[x], values=[a,b,c]
                if (tp.getData()[0] instanceof List) {
                    vector = transpose(tp.getData())[0];
                    Number[] reverseShape = tp.getShape().clone();
                    ArrayUtils.reverse(reverseShape);
                    tp.setShape(reverseShape);
                } else {
                    vector = tp.getData();
                }
                builtPayloads.add(tensorCreator.apply(new TensorContructionHolder(tp, vector)));

            }
        } else { //otherwise, assume that we have one tensor, holding data in row vector(s)
            TensorPayload tp = payload.getTensorPayloads()[0];

            // is the payload a matrix/tensor?
            if (tp.getData()[0] instanceof ArrayList) {
                //transpose objects
                Object[][] objectVector = transpose(tp.getData());

                for (int i = 0; i < objectVector.length; i++) {
                    builtPayloads.add(tensorCreator.apply(new TensorContructionHolder(tp, objectVector[i], tp.getName() + "-" + i)));
                }
            } else { // else, payload is a vector
                builtPayloads.add(tensorCreator.apply(new TensorContructionHolder(tp)));
            }
        }
        return builtPayloads;
    }

    // === TENSOR BUILDERS =============================================================================================
    // initialize the input tensor builder
    private static ModelInferRequest.InferInputTensor.Builder inputBuilderInitializer(TensorPayload input, String name) {
        ModelInferRequest.InferInputTensor.Builder inputBuilder = ModelInferRequest.InferInputTensor.newBuilder();
        inputBuilder.addAllShape(shapeProcess(input.getShape()));
        inputBuilder.setDatatype(input.getDatatype());
        inputBuilder.setName(name);

        if (input.getParameters() != null) {
            inputBuilder.putAllParameters(getParameterMap(input.getParameters()));
        }
        return inputBuilder;
    }

    // initialize the output tensor builder
    private static ModelInferResponse.InferOutputTensor.Builder outputBuilderInitializer(TensorPayload output, String name) {
        ModelInferResponse.InferOutputTensor.Builder outputBuilder = ModelInferResponse.InferOutputTensor.newBuilder();
        outputBuilder.addAllShape(shapeProcess(output.getShape()));
        outputBuilder.setDatatype(output.getDatatype());
        outputBuilder.setName(name);

        if (output.getParameters() != null) {
            outputBuilder.putAllParameters(getParameterMap(output.getParameters()));
        }
        return outputBuilder;
    }

    // === TENSOR PAYLOAD EXTRACTORS ===================================================================================
    public static List<ModelInferRequest.InferInputTensor> getBuilderInputs(ModelInferRequestPayload payload) {
        return getBuilderInputsOrOutputs(
                payload,
                "request",
                "input",
                (TensorContructionHolder tch) -> {
                    ModelInferRequest.InferInputTensor.Builder inputBuilder = inputBuilderInitializer(tch.tp, tch.name);
                    inputBuilder.setContents(getTensorBuilder(tch.tp.getDatatype(), tch.vector));
                    return inputBuilder.build();
                });
    }

    public static List<ModelInferResponse.InferOutputTensor> getBuilderOutputs(ModelInferResponsePayload payload) {
        return getBuilderInputsOrOutputs(
                payload,
                "response",
                "output",
                (TensorContructionHolder tch) -> {
                    ModelInferResponse.InferOutputTensor.Builder outputBuilder = outputBuilderInitializer(tch.tp, tch.name);
                    outputBuilder.setContents(getTensorBuilder(tch.tp.getDatatype(), tch.vector));
                    return outputBuilder.build();
                });
    }

    // === MODEL INFER RESPONSE/REQUEST POPULATORS =====================================================================
    // populate a model infer request builder
    public static void populateRequestBuilder(ModelInferRequest.Builder inferBuilder, ModelInferRequestPayload payload) {
        inferBuilder.addAllInputs(getBuilderInputs(payload));
    }

    // populate a model infer response builder
    public static void populateResponseBuilder(ModelInferResponse.Builder inferBuilder, ModelInferResponsePayload payload) {

        if (payload.getModelName() != null) {
            inferBuilder.setModelName(payload.getModelName());
        }

        if (payload.getModelVersion() != null) {
            inferBuilder.setModelVersion(payload.getModelVersion());
        }
        if (payload.getId() != null) {
            inferBuilder.setId(payload.getId());
        }

        if (payload.getParameters() != null) {
            inferBuilder.putAllParameters(getParameterMap(payload.getParameters()));
        }

        inferBuilder.addAllOutputs(getBuilderOutputs(payload));
    }
}
