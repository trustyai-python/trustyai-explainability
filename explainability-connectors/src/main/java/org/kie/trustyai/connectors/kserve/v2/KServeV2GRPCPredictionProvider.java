package org.kie.trustyai.connectors.kserve.v2;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import org.kie.trustyai.connectors.grpc.GrpcLoadBalancing;
import org.kie.trustyai.connectors.kserve.AbstractKServePredictionProvider;
import org.kie.trustyai.connectors.kserve.v2.grpc.GRPCInferenceServiceGrpc;
import org.kie.trustyai.connectors.kserve.v2.grpc.InferParameter;
import org.kie.trustyai.connectors.kserve.v2.grpc.InferTensorContents;
import org.kie.trustyai.connectors.kserve.v2.grpc.ModelInferRequest;
import org.kie.trustyai.connectors.kserve.v2.grpc.ModelInferResponse;
import org.kie.trustyai.connectors.utils.ListenableFutureUtils;
import org.kie.trustyai.explainability.model.PredictionInput;
import org.kie.trustyai.explainability.model.PredictionOutput;
import org.kie.trustyai.explainability.model.PredictionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

/**
 * Wraps a KServe v2-compatible gRPC model server as a TrustyAI {@link PredictionProvider}
 */
public class KServeV2GRPCPredictionProvider extends AbstractKServePredictionProvider implements PredictionProvider {

    private static final Logger logger = LoggerFactory.getLogger(KServeV2GRPCPredictionProvider.class);

    private static final GrpcLoadBalancing DEFAULT_LOAD_BALANCING = GrpcLoadBalancing.ROUND_ROBIN;

    private final KServeConfig kServeConfig;
    private final Map<String, String> optionalParameters;
    private final Semaphore semaphore;

    private KServeV2GRPCPredictionProvider(KServeConfig kServeConfig, String inputName, List<String> outputNames, Map<String, String> optionalParameters) {

        super(outputNames, inputName);
        this.kServeConfig = kServeConfig;
        this.optionalParameters = optionalParameters;
        this.semaphore = new Semaphore(kServeConfig.getMaximumConcurrentRequests());
    }

    /**
     * Create a {@link KServeV2GRPCPredictionProvider} for a model with an endpoint at {@code target} and named {@code modelName}.
     * In this case, the output names will be generated as {@code output-0}, {@code output-1}, ...
     *
     * @param kServeConfig The remote KServe v2 model server configuration
     * @return A {@link PredictionProvider}
     */
    public static KServeV2GRPCPredictionProvider forTarget(KServeConfig kServeConfig) {
        return KServeV2GRPCPredictionProvider.forTarget(kServeConfig, null, null, null);
    }

    /**
     * Create a {@link KServeV2GRPCPredictionProvider} with an endpoint at {@code target} and named {@code modelName}.
     * In this case, the output names are specified with {@code outputNames}.
     *
     * @param kServeConfig The remote KServe v2 model server configuration
     * @param outputNames A {@link List} of output names to be used
     * @param optionalParameters A {@link Map} of parameters to pass to the gRPC
     * @return A {@link PredictionProvider}
     */
    public static KServeV2GRPCPredictionProvider forTarget(KServeConfig kServeConfig, String inputName, List<String> outputNames, Map<String, String> optionalParameters) {
        return new KServeV2GRPCPredictionProvider(kServeConfig, inputName, outputNames, optionalParameters);
    }

    /**
     * Create a {@link KServeV2GRPCPredictionProvider} with an endpoint at {@code target} and named {@code modelName}.
     * In this case, the output names are specified with {@code outputNames}.
     *
     * @param kServeConfig The remote KServe v2 model server configuration
     * @param parameters A {@link Map} of parameters to pass to the gRPC method call
     * @return A {@link PredictionProvider}
     */
    public static KServeV2GRPCPredictionProvider forTarget(KServeConfig kServeConfig, Map<String, String> parameters) {
        return new KServeV2GRPCPredictionProvider(kServeConfig, null, null, parameters);
    }

    private ModelInferRequest.InferInputTensor.Builder buildTensor(InferTensorContents.Builder contents, int nSamples, int nFeatures) {

        final ModelInferRequest.InferInputTensor.Builder tensor = ModelInferRequest.InferInputTensor
                .newBuilder();
        tensor.setName(inputName != null ? inputName : DEFAULT_TENSOR_NAME)
                .addShape(nSamples)
                .addShape(nFeatures)
                .setDatatype(DEFAULT_DATATYPE.toString())
                .setContents(contents);
        if (this.optionalParameters != null) {
            Map<String, InferParameter> parameterMap = new HashMap<>();
            for (Map.Entry<String, String> entry : optionalParameters.entrySet()) {
                parameterMap.put(entry.getKey(), InferParameter.newBuilder().setStringParam(entry.getValue()).build());
            }
            tensor.putAllParameters(parameterMap);
        }
        return tensor;
    }

    private ModelInferRequest.Builder buildRequest(ModelInferRequest.InferInputTensor.Builder tensor) {
        final ModelInferRequest.Builder request = ModelInferRequest
                .newBuilder()
                .setModelName(this.kServeConfig.getModelId());

        request.addInputs(tensor);
        return request;
    }

    @Override
    public CompletableFuture<List<PredictionOutput>> predictAsync(List<PredictionInput> inputs) {
        // Guard clauses for inputs validation
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("Prediction inputs must not be empty.");
        }
        final int nFeatures = inputs.get(0).getFeatures().size();
        if (nFeatures == 0) {
            throw new IllegalArgumentException("Prediction inputs must have at least one feature.");
        }

        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            CompletableFuture<List<PredictionOutput>> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("gRPC inference request failed while waiting for concurrent connection thread", e));
            return future;
        }

        // Create a new channel for each prediction request
        final ManagedChannel localChannel = ManagedChannelBuilder
                .forTarget(kServeConfig.getTarget())
                .defaultLoadBalancingPolicy(DEFAULT_LOAD_BALANCING.getValue())
                .usePlaintext()
                .build();

        try {
            final InferTensorContents.Builder contents = PayloadParser.predictionInputToTensorContents(inputs);
            final ModelInferRequest.InferInputTensor.Builder tensor = buildTensor(contents, inputs.size(), nFeatures);
            final ModelInferRequest.Builder request = buildRequest(tensor);

            final GRPCInferenceServiceGrpc.GRPCInferenceServiceFutureStub futureStub = GRPCInferenceServiceGrpc.newFutureStub(localChannel);

            final ListenableFuture<ModelInferResponse> listenableResponse = futureStub.modelInfer(request.build());
            final CompletableFuture<ModelInferResponse> futureResponse = ListenableFutureUtils.asCompletableFuture(listenableResponse);

            return futureResponse
                    .thenApply(response -> TensorConverter.parseKserveModelInferResponse(response, inputs.size(), Objects.isNull(this.outputNames) ? Optional.empty() : Optional.of(this.outputNames)))
                    .exceptionally(ex -> {
                        logger.error("Error during model inference: " + ex.getMessage());
                        throw new RuntimeException("Failed to get inference", ex);
                    })
                    .whenComplete((response, throwable) -> {
                        // Release the semaphore permit after request is complete
                        semaphore.release();
                        if (!localChannel.isShutdown()) { // In case channel already closed in .exceptionally()
                            localChannel.shutdown();
                        }
                    });
        } catch (Exception e) {
            // Release the semaphore permit if an exception occurs
            semaphore.release();
            if (!localChannel.isShutdown()) { // In case channel already closed in .exceptionally()
                localChannel.shutdown();
            }
            throw e;
        }
    }

}
