package org.kie.trustyai.connectors.kserve.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.protobuf.ByteString;
import org.kie.trustyai.connectors.kserve.v2.grpc.InferTensorContents;
import org.kie.trustyai.connectors.kserve.v2.grpc.ModelInferRequest;
import org.kie.trustyai.connectors.kserve.v2.grpc.ModelInferResponse;
import org.kie.trustyai.explainability.model.*;

import static org.kie.trustyai.explainability.model.Type.*;

public class TensorConverter {

    public static KServeDatatype trustyToKserveType(Type type, Value value) throws IllegalArgumentException {
        final Object object = value.getUnderlyingObject();
        if (type == NUMBER) {
            if (object instanceof Integer) {
                return KServeDatatype.INT32;
            } else if (object instanceof Double) {
                return KServeDatatype.FP64;
            } else if (object instanceof Long) {
                return KServeDatatype.INT64;
            } else {
                throw new IllegalArgumentException("Unsupported object type: " + object.getClass().getName());
            }
        } else if (type == BOOLEAN) {
            return KServeDatatype.BOOL;
        } else if (type == CATEGORICAL) {
            return KServeDatatype.BYTES;
        } else {
            throw new IllegalArgumentException("Unsupported TrustyAI type: " + type);
        }
    }

    public static List<Feature> inputTensorToFeatures(List<ModelInferRequest.InferInputTensor> tensors) {
        if (tensors.size() == 1) { // NP codec
            final ModelInferRequest.InferInputTensor tensor = tensors.get(0);
            return inputTensorToFeatures(tensor, null);
        } else { // PD codec
            return tensors.stream().map(tensor -> {
                final InferTensorContents responseInputContents = tensor.getContents();
                final KServeDatatype type;
                try {
                    type = KServeDatatype.valueOf(tensor.getDatatype());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Currently unsupported type for Tensor input, type=" + tensor.getDatatype());
                }
                switch (type) {
                    case BOOL:
                        return FeatureFactory.newBooleanFeature(tensor.getName(), responseInputContents.getBoolContents(0));
                    case INT8:
                    case INT16:
                    case INT32:
                        return FeatureFactory.newNumericalFeature(tensor.getName(), responseInputContents.getIntContents(0));
                    case INT64:
                        return FeatureFactory.newNumericalFeature(tensor.getName(), responseInputContents.getInt64Contents(0));
                    case FP32:
                        return FeatureFactory.newNumericalFeature(tensor.getName(), responseInputContents.getFp32Contents(0));
                    case FP64:
                        return FeatureFactory.newNumericalFeature(tensor.getName(), responseInputContents.getFp64Contents(0));
                    case BYTES:
                        return FeatureFactory.newCategoricalFeature(tensor.getName(), String.valueOf(responseInputContents.getBytesContents(0)));
                    default:
                        throw new IllegalArgumentException("Currently unsupported type for Tensor input, type=" + tensor.getDatatype());
                }
            }).collect(Collectors.toList());
        }
    }

    public static List<Feature> inputTensorToFeatures(ModelInferRequest.InferInputTensor tensor,
            List<String> inputNames) throws IllegalArgumentException {
        final InferTensorContents responseInputContents = tensor.getContents();
        final KServeDatatype type;
        try {
            type = KServeDatatype.valueOf(tensor.getDatatype());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Currently unsupported type for Tensor input, type=" + tensor.getDatatype());
        }
        switch (type) {
            case BOOL:
                return inputFromContentList(responseInputContents.getBoolContentsList(), BOOLEAN, inputNames);
            case INT8:
            case INT16:
            case INT32:
                return inputFromContentList(responseInputContents.getIntContentsList(), NUMBER, inputNames);
            case INT64:
                return inputFromContentList(responseInputContents.getInt64ContentsList(), NUMBER, inputNames);
            case FP32:
                return inputFromContentList(responseInputContents.getFp32ContentsList(), NUMBER, inputNames);
            case FP64:
                return inputFromContentList(responseInputContents.getFp64ContentsList(), NUMBER, inputNames);
            case BYTES:
                return inputFromContentList(responseInputContents.getBytesContentsList(), CATEGORICAL, inputNames);
            default:
                throw new IllegalArgumentException("Currently unsupported type for Tensor input, type=" + tensor.getDatatype());
        }
    }

    public static List<Output> outputTensorToOutputs(List<ModelInferResponse.InferOutputTensor> tensors,  List<ByteString> rawContents) {
        if (tensors.size() == 1) { // NP codec
            final ModelInferResponse.InferOutputTensor tensor = tensors.get(0);
            return outputTensorToOutputs(tensor, null, rawContents.get(0));
        } else { // PD codec
            List<Output> outputs = new ArrayList<>();

            for (int idx=0; idx<tensors.size(); idx++){
                ModelInferResponse.InferOutputTensor tensor = tensors.get(idx);
                final InferTensorContents contents = tensor.getContents();
                final KServeDatatype type;
                try {
                    type = KServeDatatype.valueOf(tensor.getDatatype());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Currently unsupported type for Tensor output, type=" + tensor.getDatatype());
                }
//                System.out.println("OUTPUT_TENSOR_TO_OUTPUTS: Tensor Type: "+type);
                if (tensor.getSerializedSize() == 0 && ! rawContents.isEmpty()) {
                    switch (type) {
                        case INT8:
                        case INT16:
                        case INT32:
                            outputs.add(new Output(tensor.getName(), NUMBER, new Value(FallbackConversions.int32Parser(rawContents.get(idx))), 1.0));
                        case INT64:
                            outputs.add(new Output(tensor.getName(), NUMBER, new Value(FallbackConversions.int64Parser(rawContents.get(idx))), 1.0));
                        case FP32:
                            outputs.add(new Output(tensor.getName(), NUMBER, new Value(FallbackConversions.fp32Parser(rawContents.get(idx))), 1.0));
                        case FP64:
                            outputs.add(new Output(tensor.getName(), NUMBER, new Value(FallbackConversions.fp64Parser(rawContents.get(idx))), 1.0));
                        default:
                            throw new IllegalArgumentException("Currently unsupported type for Tensor output, type=" + tensor.getDatatype());
                    }
                } else {
                    switch (type) {
                        case BOOL:
                            outputs.add(new Output(tensor.getName(), Type.BOOLEAN, new Value(contents.getBoolContents(0)), 1.0));
                        case INT8:
                        case INT16:
                        case INT32:
                            outputs.add(new Output(tensor.getName(), NUMBER, new Value(contents.getIntContents(0)), 1.0));
                        case INT64:
                            outputs.add(new Output(tensor.getName(), NUMBER, new Value(contents.getInt64Contents(0)), 1.0));
                        case FP32:
                            outputs.add(new Output(tensor.getName(), NUMBER, new Value(contents.getFp32Contents(0)), 1.0));
                        case FP64:
                            outputs.add(new Output(tensor.getName(), NUMBER, new Value(contents.getFp64Contents(0)), 1.0));
                        case BYTES:
                            outputs.add(new Output(tensor.getName(), CATEGORICAL, new Value(contents.getBytesContents(0)), 1.0));
                        default:
                            throw new IllegalArgumentException("Currently unsupported type for Tensor input, type=" + tensor.getDatatype());
                    }
                }
            }

            return outputs;
        }
    }

    public static List<Output> outputTensorToOutputs(ModelInferResponse.InferOutputTensor tensor,
                                                     List<String> outputNames, ByteString rawContents) throws IllegalArgumentException {
        final InferTensorContents responseOutputContents = tensor.getContents();
        final KServeDatatype type;
        try {
            type = KServeDatatype.valueOf(tensor.getDatatype());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Currently unsupported type for Tensor output, type=" + tensor.getDatatype());
        }
//        System.out.println("OUTPUT_TENSOR_TO_OUTPUTS + NAME: Tensor Type: "+type);
//        System.out.println("OUTPUT_TENSOR_TO_OUTPUTS + NAME: Tensor: "+tensor);
//        System.out.println("OUTPUT_TENSOR_TO_OUTPUTS + NAME: Tensor Contents: "+responseOutputContents);
//        System.out.println("OUTPUT_TENSOR_TO_OUTPUTS + NAME: Tensor Content Serialized Size: "+responseOutputContents.getSerializedSize());
//        System.out.println("OUTPUT_TENSOR_TO_OUTPUTS + NAME: Raw Contents: "+rawContents);

        if (responseOutputContents.getSerializedSize() == 0 && ! rawContents.isEmpty()) {
            switch (type) {
                case INT8:
                case INT16:
                case INT32:
                    return outputFromContentList(List.of(FallbackConversions.int32Parser(rawContents)), NUMBER, outputNames);
                case INT64:
                    return outputFromContentList(List.of(FallbackConversions.int64Parser(rawContents)), NUMBER, outputNames);
                case FP32:
                    return outputFromContentList(List.of(FallbackConversions.fp32Parser(rawContents)), NUMBER, outputNames);
                case FP64:
                    return outputFromContentList(List.of(FallbackConversions.fp64Parser(rawContents)), NUMBER, outputNames);
                default:
                    throw new IllegalArgumentException("Currently unsupported type for Tensor output, type=" + tensor.getDatatype());
            }
        } else {
            switch (type) {
                case BOOL:
                    return outputFromContentList(responseOutputContents.getBoolContentsList(), BOOLEAN, outputNames);
                case INT8:
                case INT16:
                case INT32:
                    return outputFromContentList(responseOutputContents.getIntContentsList(), NUMBER, outputNames);
                case INT64:
                    return outputFromContentList(responseOutputContents.getInt64ContentsList(), NUMBER, outputNames);
                case FP32:
                    return outputFromContentList(responseOutputContents.getFp32ContentsList(), NUMBER, outputNames);
                case FP64:
                    return outputFromContentList(responseOutputContents.getFp64ContentsList(), NUMBER, outputNames);
                case BYTES:
                    return outputFromContentList(responseOutputContents.getBytesContentsList(), CATEGORICAL, outputNames);
                default:
                    throw new IllegalArgumentException("Currently unsupported type for Tensor output, type=" + tensor.getDatatype());
            }
        }
    }

    public static List<Output> outputFromContentList(List<?> values, Type type, List<String> outputNames) {
        final int size = values.size();
        List<String> names = outputNames == null ? IntStream.range(0, size).mapToObj(i -> "output-" + i).collect(Collectors.toList()) : outputNames;
        if (names.size() != size) {
            throw new IllegalArgumentException("Output names list has an incorrect size (" + names.size() + ", when it should be " + size + ")");
        }

        return IntStream.range(0, size)
                .mapToObj(i -> new Output(names.get(i), type, new Value(values.get(i)), 1.0))
                .collect(Collectors.toUnmodifiableList());
    }

    public static List<Feature> inputFromContentList(List<?> values, Type type, List<String> outputNames) {
        final int size = values.size();
        List<String> names = outputNames == null ? IntStream.range(0, size).mapToObj(i -> "input-" + i).collect(Collectors.toList()) : outputNames;
        if (names.size() != size) {
            throw new IllegalArgumentException("Input names list has an incorrect size (" + names.size() + ", when it should be " + size + ")");
        }
        return IntStream
                .range(0, size)
                .mapToObj(i -> new Feature(names.get(i), type, new Value(values.get(i))))
                .collect(Collectors.toUnmodifiableList());
    }

}
