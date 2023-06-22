package org.kie.trustyai.connectors.kserve.v2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.kie.trustyai.connectors.kserve.v2.grpc.InferTensorContents;
import org.kie.trustyai.connectors.kserve.v2.grpc.ModelInferRequest;
import org.kie.trustyai.connectors.kserve.v2.grpc.ModelInferResponse;
import org.kie.trustyai.explainability.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.kie.trustyai.explainability.model.Type.*;

public class TensorConverter {

    private static final Logger logger = LoggerFactory.getLogger(TensorConverter.class);

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

    static Feature contentsToFeature(InferTensorContents tensorContents, String kserveDatatype, String name, int index) {
        final KServeDatatype type;
        try {
            type = KServeDatatype.valueOf(kserveDatatype);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Currently unsupported type for Tensor input, type=" + kserveDatatype);
        }

        switch (type) {
            case BOOL:
                return FeatureFactory.newBooleanFeature(name, tensorContents.getBoolContents(index));
            case INT8:
            case INT16:
            case INT32:
                return FeatureFactory.newNumericalFeature(name, tensorContents.getIntContents(index));
            case INT64:
                return FeatureFactory.newNumericalFeature(name, tensorContents.getInt64Contents(index));
            case FP32:
                return FeatureFactory.newNumericalFeature(name, tensorContents.getFp32Contents(index));
            case FP64:
                return FeatureFactory.newNumericalFeature(name, tensorContents.getFp64Contents(index));
            case BYTES:
                return FeatureFactory.newCategoricalFeature(name, String.valueOf(tensorContents.getBytesContents(index)));
            default:
                throw new IllegalArgumentException("Currently unsupported type for Tensor input, type=" + kserveDatatype);
        }
    }

    static Output contentsToOutput(InferTensorContents tensorContents, String kserveDatatype, String name, int index) {
        final KServeDatatype type;

        try {
            type = KServeDatatype.valueOf(kserveDatatype);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Currently unsupported type for Tensor input, type=" + kserveDatatype);
        }
        switch (type) {
            case BOOL:
                return new Output(name, Type.BOOLEAN, new Value(tensorContents.getBoolContents(index)), 1.0);
            case INT8:
            case INT16:
            case INT32:
                return new Output(name, Type.NUMBER, new Value(tensorContents.getIntContents(index)), 1.0);
            case INT64:
                return new Output(name, Type.NUMBER, new Value(tensorContents.getInt64Contents(index)), 1.0);
            case FP32:
                return new Output(name, Type.NUMBER, new Value(tensorContents.getFp32Contents(index)), 1.0);
            case FP64:
                return new Output(name, Type.NUMBER, new Value(tensorContents.getFp64Contents(index)), 1.0);
            case BYTES:
                return new Output(name, Type.CATEGORICAL, new Value(String.valueOf(tensorContents.getBytesContents(index))), 1.0);
            default:
                throw new IllegalArgumentException("Currently unsupported type for Tensor input, type=" + kserveDatatype);
        }
    }

    public static List<PredictionInput> parseKserveModelInferRequest(ModelInferRequest data) {
        return parseKserveModelInferRequest(data, Optional.empty(), false);
    }

    public static List<PredictionInput> parseKserveModelInferRequest(ModelInferRequest data, Optional<List<String>> featureNames) {
        return parseKserveModelInferRequest(data, featureNames, false);
    }

    // converting an entire batch of raw contents is faster, so prefer this function when possible
    private static List<PredictionInput> rawHandlerMulti(ModelInferRequest data, ModelInferRequest.InferInputTensor tensor, List<String> names, int secondShape, boolean raw) {
        if (raw) {
            return new ArrayList<>(List.of(PayloadParser.rawContentToPredictionInput(data, names)));
        }
        final List<Feature> feature = IntStream.range(0, secondShape)
                .mapToObj(i -> contentsToFeature(tensor.getContents(), tensor.getDatatype(), names.get(i), i))
                .collect(Collectors.toCollection(ArrayList::new));
        return List.of(new PredictionInput(feature));
    }

    // converting an entire batch of raw contents is faster, so prefer this function when possible
    private static List<Feature> rawHandlerMultiFeature(ModelInferRequest data, ModelInferRequest.InferInputTensor tensor, List<String> names, int secondShape, boolean raw) {
        if (raw) {
            return PayloadParser.rawContentToPredictionInput(data, names).getFeatures();
        }
        return IntStream.range(0, secondShape)
                .mapToObj(i -> contentsToFeature(tensor.getContents(), tensor.getDatatype(), names.get(i), i))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    // if only a single item from raw contents is needed, use this
    private static Feature rawHandlerSingle(ModelInferRequest data, ModelInferRequest.InferInputTensor tensor, String name, int idx, boolean raw) {
        if (raw) {
            return PayloadParser.rawContentToFeature(data, name, 0, idx);
        } else {
            return contentsToFeature(tensor.getContents(), tensor.getDatatype(), name, idx);
        }
    }

    private static List<String> labelTensors(String name, int idxs) {
        return IntStream.range(0, idxs).mapToObj(i -> name + "-" + i).collect(Collectors.toList());
    }

    public static List<PredictionInput> parseKserveModelInferRequest(ModelInferRequest data, Optional<List<String>> featureNames, boolean isBatch) {
        final int count = data.getInputsCount();
        boolean raw = data.getRawInputContentsCount() > 0;

        if (count == 1) { // The NP codec case
            final ModelInferRequest.InferInputTensor tensor = data.getInputs(0);
            final List<Long> shape = tensor.getShapeList();
            final int firstShape = shape.get(0).intValue();

            if (firstShape < 2) {
                if (shape.size() >= 2) {
                    int secondShape = 1;
                    for (int i = 1; i < shape.size(); i++) {
                        secondShape *= shape.get(i).intValue();
                    }
                    // NP features, no batch
                    List<String> names;
                    if (isBatch) {
                        System.out.println("in A");
                        List<Feature> fs = rawHandlerMultiFeature(
                                data,
                                tensor,
                                Collections.nCopies(secondShape, tensor.getName()),
                                secondShape,
                                raw);
                        return fs.stream().map(f -> new PredictionInput(List.of(f))).collect(Collectors.toList());
                    } else {
                        if (featureNames.isPresent()) {
                            System.out.println("in B");
                            names = featureNames.get();
                        } else {
                            System.out.println("in C");
                            names = labelTensors(tensor.getName(), secondShape);
                        }
                        return rawHandlerMulti(data, tensor, names, secondShape, raw);
                    }
                } else if (shape.size() == 1) {
                    // A single element feature, no batch. PD or NP irrelevant
                    System.out.println("in D");
                    List<Feature> fs = new ArrayList<>();
                    fs.add(rawHandlerSingle(data, tensor, tensor.getName(), 0, raw));
                    return List.of(new PredictionInput(fs));
                } else {
                    throw new IllegalArgumentException("Shape size not supported for tabular data");
                }
            } else {
                // NP-batch
                System.out.println("in E");
                final int secondShape = shape.get(1).intValue();
                final List<PredictionInput> predictionInputs = new ArrayList<>();

                for (int batch = 0; batch < firstShape; batch++) {
                    final List<Feature> features = new ArrayList<>();
                    for (int featureIndex = 0; featureIndex < secondShape; featureIndex++) {
                        String name = featureNames.isPresent() ? featureNames.get().get(featureIndex) : tensor.getName() + "-" + featureIndex;
                        features.add(rawHandlerSingle(data, tensor, name, secondShape * batch + featureIndex, raw));
                    }
                    predictionInputs.add(new PredictionInput(features));
                }
                logger.debug("Using NP codec (batch)");

                return predictionInputs;
            }

        } else if (count > 1) { // The PD codec case
            final List<Long> shape = data.getInputs(0).getShapeList();
            if (shape.size() < 2) {
                // Multi-feature PD, no batch
                System.out.println("in F");
                logger.debug("Using PD codec (no batch)");
                final List<ModelInferRequest.InferInputTensor> tensors = data.getInputsList();
                final List<Feature> features = tensors.stream().map(tensor -> {
                    final InferTensorContents contents = tensor.getContents();
                    return rawHandlerSingle(data, tensor, tensor.getName(), 0, raw);
                }).collect(Collectors.toCollection(ArrayList::new));

                return new ArrayList<>(List.of(new PredictionInput(features)));
            } else {
                // given some shape (ntensors, a, b, ... n)
                // return ntensors of PredictionOutputs, each with a*b*c*...*n outputs

                System.out.println("in G");
                // Multi-feature PD, batch
                logger.debug("Using NP codec (batch)");
                final int secondShape = shape.get(1).intValue();
                final List<ModelInferRequest.InferInputTensor> tensors = data.getInputsList();
                final List<List<Feature>> features = tensors.stream().map(tensor -> {
                    return IntStream.range(0, secondShape)
                            .mapToObj(i -> rawHandlerSingle(data, tensor, tensor.getName(), i, raw))
                            .collect(Collectors.toCollection(ArrayList::new));
                }).collect(Collectors.toCollection(ArrayList::new));
                // Transpose the features
                final List<PredictionInput> predictionInputs = new ArrayList<>();
                for (int batch = 0; batch < secondShape; batch++) {
                    final List<Feature> batchFeatures = new ArrayList<>();
                    for (int featureIndex = 0; featureIndex < tensors.size(); featureIndex++) {
                        batchFeatures.add(features.get(featureIndex).get(batch));
                    }
                    predictionInputs.add(new PredictionInput(batchFeatures));
                }
                return predictionInputs;
            }
        } else {
            throw new IllegalArgumentException("Data inputs count not supported: " + count);
        }
    }

    // if only a single item from raw contents is needed, use this
    private static Output rawHandlerSingle(ModelInferResponse data, ModelInferResponse.InferOutputTensor tensor, String name, int idx, boolean raw) {
        if (raw) {
            return PayloadParser.rawContentToOutput(data, name, 0, idx);
        } else {
            return contentsToOutput(tensor.getContents(), tensor.getDatatype(), name, idx);
        }
    }

    // converting an entire batch of raw contents is faster, so prefer this function when possible
    private static List<Output> rawHandlerMultiOutput(ModelInferResponse data, ModelInferResponse.InferOutputTensor tensor, List<String> names, int secondShape, boolean raw) {
        if (raw) {
            return PayloadParser.rawContentToPredictionOutput(data, names).getOutputs();
        }
        return IntStream.range(0, secondShape)
                .mapToObj(i -> contentsToOutput(tensor.getContents(), tensor.getDatatype(), names.get(i), i))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    // converting an entire batch of raw contents is faster, so prefer this function when possible
    private static List<PredictionOutput> rawHandlerMulti(ModelInferResponse data, ModelInferResponse.InferOutputTensor tensor, List<String> names, int secondShape, boolean raw) {
        if (raw) {
            return new ArrayList<>(List.of(PayloadParser.rawContentToPredictionOutput(data, names)));
        }
        System.out.println(names + ": " + secondShape);
        final List<Output> output = IntStream.range(0, secondShape)
                .mapToObj(i -> contentsToOutput(tensor.getContents(), tensor.getDatatype(), names.get(i), i))
                .collect(Collectors.toCollection(ArrayList::new));
        return List.of(new PredictionOutput(output));
    }

    public static List<PredictionOutput> parseKserveModelInferResponse(ModelInferResponse data, int enforcedFirstDimension) {
        return parseKserveModelInferResponse(data, enforcedFirstDimension, Optional.empty(), false);
    }

    public static List<PredictionOutput> parseKserveModelInferResponse(ModelInferResponse data, int enforcedFirstDimension, Optional<List<String>> featureNames) {
        return parseKserveModelInferResponse(data, enforcedFirstDimension, featureNames, false);
    }

    public static List<PredictionOutput> parseKserveModelInferResponse(ModelInferResponse data, int enforcedFirstDimension, Optional<List<String>> featureNames, boolean isBatch) {
        final int count = data.getOutputsCount();
        boolean raw = data.getRawOutputContentsCount() > 0;

        if (count == 1) { // The NP codec case
            final ModelInferResponse.InferOutputTensor tensor = data.getOutputs(0);
            final List<Long> shape = tensor.getShapeList();
            final int firstShape = shape.get(0).intValue();

            if (firstShape < 2) {
                if (shape.size() >= 2) {
                    int secondShape = 1;
                    for (int i = 1; i < shape.size(); i++) {
                        secondShape *= shape.get(i).intValue();
                    }

                    List<String> names;
                    if (isBatch) {
                        logger.debug("Using NP codec (batch)");
                        System.out.println("out A");
                        names = Collections.nCopies(secondShape, tensor.getName());
                        List<Output> os = rawHandlerMultiOutput(data, tensor, names, secondShape, raw);
                        return os.stream().map(o -> new PredictionOutput(List.of(o))).collect(Collectors.toList());
                    } else {
                        if (featureNames.isPresent()) {
                            names = featureNames.get();
                        } else {
                            names = IntStream.range(0, secondShape).mapToObj(i -> tensor.getName() + "-" + i).collect(Collectors.toCollection(ArrayList::new));
                        }
                        System.out.println("out B");
                        return rawHandlerMulti(data, tensor, names, secondShape, raw);
                    }

                } else if (shape.size() == 1) {
                    System.out.println("out C");
                    // A single element feature, no batch. PD or NP irrelevant
                    return List.of(new PredictionOutput(List.of(rawHandlerSingle(data, tensor, tensor.getName(), 0, raw))));
                } else {
                    throw new IllegalArgumentException("Shape size not supported for tabular data");
                }
            } else {
                // NP-batch
                System.out.println("out D");
                final int secondShape = shape.get(1).intValue();
                final List<PredictionOutput> predictionOutputs = new ArrayList<>();

                for (int batch = 0; batch < firstShape; batch++) {
                    final List<Output> outputs = new ArrayList<>();
                    for (int featureIndex = 0; featureIndex < secondShape; featureIndex++) {
                        String name = featureNames.isPresent() ? featureNames.get().get(featureIndex) : tensor.getName() + "-" + featureIndex;
                        outputs.add(rawHandlerSingle(data, tensor, name, secondShape * batch + featureIndex, raw));
                    }
                    predictionOutputs.add(new PredictionOutput(outputs));
                }
                logger.debug("Using NP codec (batch)");
                return predictionOutputs;
            }

        } else if (count > 1) { // The PD codec case
            final List<Long> shape = data.getOutputs(0).getShapeList();
            if (shape.size() < 2) {
                System.out.println("out E");
                // Multi-feature PD, no batch
                logger.debug("Using PD codec (no batch)");
                final List<ModelInferResponse.InferOutputTensor> tensors = data.getOutputsList();
                final List<Output> outputs = tensors.stream().map(tensor -> {
                    final InferTensorContents contents = tensor.getContents();
                    return rawHandlerSingle(data, tensor, tensor.getName(), 0, raw);
                }).collect(Collectors.toCollection(ArrayList::new));
                return List.of(new PredictionOutput(outputs));
            } else {
                System.out.println("out F");
                // Multi-feature PD, batch
                logger.debug("Using NP codec (batch)");

                final List<ModelInferResponse.InferOutputTensor> tensors = data.getOutputsList();
                List<Integer> perTensorSecondShape = new ArrayList<>();
                List<List<Long>> perTensorShapes = new ArrayList<>();

                for (int tensorIDX = 0; tensorIDX < tensors.size(); tensorIDX++) {
                    List<Long> perTensorShape = tensors.get(tensorIDX).getShapeList();
                    perTensorSecondShape.add(1);
                    perTensorShapes.add(perTensorShape);
                    for (int i = 1; i < perTensorShape.size(); i++) {
                        perTensorSecondShape.set(tensorIDX, perTensorSecondShape.get(tensorIDX) * perTensorShape.get(i).intValue());
                    }
                }

                // given some shape (ntensors, a, b, ... n)
                // return ntensors of PredictionOutputs, each with a*b*c*...*n outputs
                System.out.printf("%d : %s %n", enforcedFirstDimension, perTensorShapes);
                if (enforcedFirstDimension == 1) {
                    System.out.println("out G");
                    final List<Output> outputs = IntStream.range(0, tensors.size())
                            .mapToObj(tensorIDX -> {
                                List<String> names = labelTensors(tensors.get(tensorIDX).getName(), perTensorSecondShape.get(tensorIDX));
                                System.out.println(tensorIDX);
                                return rawHandlerMulti(
                                        data,
                                        tensors.get(tensorIDX),
                                        names,
                                        perTensorSecondShape.get(tensorIDX),
                                        raw).get(0).getOutputs();
                            }).flatMap(Collection::stream).collect(Collectors.toList());
                    return List.of(new PredictionOutput(outputs));
                } else if (perTensorSecondShape.stream().allMatch(i -> i == enforcedFirstDimension)) {
                    System.out.println("out H");
                    List<List<Output>> outputs = tensors.stream()
                            .map(tensor -> IntStream.range(0, perTensorSecondShape.get(0))
                                    .mapToObj(i -> rawHandlerSingle(data, tensor, tensor.getName(), i, raw))
                                    .collect(Collectors.toCollection(ArrayList::new)))
                            .collect(Collectors.toCollection(ArrayList::new));

                    // Transpose the features
                    final List<PredictionOutput> predictionOutputs = new ArrayList<>();
                    for (int batch = 0; batch < perTensorSecondShape.get(0); batch++) {
                        final List<Output> batchOutputs = new ArrayList<>();
                        for (int outputIndex = 0; outputIndex < tensors.size(); outputIndex++) {
                            batchOutputs.add(outputs.get(outputIndex).get(batch));
                        }
                        predictionOutputs.add(new PredictionOutput(batchOutputs));
                    }
                    return predictionOutputs;
                } else {
                    throw new IllegalArgumentException("Tensor shapes: " + perTensorSecondShape + " do not match number of inputs " + enforcedFirstDimension);
                }
            }
        } else {
            throw new IllegalArgumentException("Data inputs count not supported: " + count);
        }
    }

}
