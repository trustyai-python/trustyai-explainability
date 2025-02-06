package org.kie.trustyai.service.endpoints.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;
import org.kie.trustyai.connectors.kserve.v2.TensorConverter;
import org.kie.trustyai.connectors.kserve.v2.grpc.ModelInferRequest;
import org.kie.trustyai.connectors.kserve.v2.grpc.ModelInferResponse;
import org.kie.trustyai.explainability.model.Feature;
import org.kie.trustyai.explainability.model.Output;
import org.kie.trustyai.explainability.model.Prediction;
import org.kie.trustyai.explainability.model.PredictionInput;
import org.kie.trustyai.explainability.model.PredictionOutput;
import org.kie.trustyai.explainability.model.SimplePrediction;
import org.kie.trustyai.explainability.model.dataframe.Dataframe;
import org.kie.trustyai.explainability.utils.IOUtils;
import org.kie.trustyai.service.data.datasources.DataSource;
import org.kie.trustyai.service.data.exceptions.DataframeCreateException;
import org.kie.trustyai.service.data.utils.UploadUtils;
import org.kie.trustyai.service.payloads.data.upload.ModelInferJointPayload;
import org.kie.trustyai.service.validators.generic.GenericValidationUtils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
@Tag(name = "Data Upload", description = "This endpoint is used to manually upload model data to TrustyAI.")
@Path("/data")
public class UploadEndpoint {

    private static final Logger LOG = Logger.getLogger(UploadEndpoint.class);
    @Inject
    Instance<DataSource> dataSource;

    // handle the ground truth logic
    // if successful, return Pair of dataframe, response message
    // if unsuccessful, return Pair of null, response message
    private Pair<Dataframe, String> handleGroundTruths(ModelInferJointPayload jointPayload, List<PredictionInput> predictionInputs, List<PredictionOutput> predictionOutputs, String[] inputIds) {
        Dataframe inferenceDataframe = dataSource.get().getDataframe(jointPayload.getModelName());
        List<Pair<String, SimplePrediction>> idToPrediction = IntStream.range(0, predictionInputs.size())
                .mapToObj(i -> Pair.of(inputIds[i], new SimplePrediction(predictionInputs.get(i), predictionOutputs.get(i), UUID.fromString(inputIds[i]))))
                .collect(Collectors.toList());

        int nOutputs = inferenceDataframe.getOutputsCount();
        List<String> rowMismatchErrors = new ArrayList<>();

        // check that uploaded inputs match recorded inputs and correlate ground truths if they do
        List<Prediction> groundTruthsToSave = new ArrayList<>();
        for (Pair<String, SimplePrediction> entry : idToPrediction) {
            if (inferenceDataframe.hasID(entry.getKey())) {
                int matchingIDX = inferenceDataframe.getRowIdxFromID(entry.getKey());
                List<Feature> matchingFeatures = inferenceDataframe.getInputRowAsFeature(matchingIDX);
                List<Output> matchingOutputs = inferenceDataframe.getOutputRowAsOutput(matchingIDX);
                List<Feature> uploadedFeatures = entry.getValue().getInput().getFeatures();
                List<Output> uploadedOutputs = entry.getValue().getOutput().getOutputs();

                // check that uploaded inputs match
                boolean inputsAligned = matchingFeatures.equals(uploadedFeatures);
                boolean outputsAligned = true;

                if (!inputsAligned) {
                    if (matchingFeatures.size() != uploadedFeatures.size()) {
                        rowMismatchErrors.add(String.format(" - ID=%s input shapes do not match. Observed inputs have length=%d while uploaded inputs have length=%d.", entry.getKey(),
                                matchingFeatures.size(), uploadedFeatures.size()));
                    } else {
                        String[] diffTable = IOUtils.featureListComparison(matchingFeatures, uploadedFeatures, "Original", "Uploaded", true).getFirst().split("\\R");
                        String subTable = String.join(System.lineSeparator(), Arrays.copyOfRange(diffTable, 1, diffTable.length - 1));
                        rowMismatchErrors.add(String.format(" - ID=%s inputs are not identical:%n%s%n", entry.getKey(), subTable));
                    }
                }

                if (nOutputs != uploadedOutputs.size()) {
                    rowMismatchErrors.add(String.format(" - ID=%s output shapes do not match. Observed outputs have length=%d while uploaded ground-truths have length=%d.",
                            entry.getKey(), nOutputs, uploadedOutputs.size()));
                    outputsAligned = false;
                } else {
                    boolean outputMismatch = false;
                    // check that all specified outputs align with expected type and name
                    for (int i = 0; i < uploadedOutputs.size(); i++) {
                        if (!uploadedOutputs.get(i).getName().equals(matchingOutputs.get(i).getName()) ||
                                !uploadedOutputs.get(i).getType().equals(matchingOutputs.get(i).getType()) ||
                                !uploadedOutputs.get(i).getValue().getUnderlyingObject().getClass().equals(matchingOutputs.get(i).getValue().getUnderlyingObject().getClass())) {
                            outputMismatch = true;
                            break;
                        }
                    }

                    // if any names or types don't match, add to errors
                    if (outputMismatch) {
                        String[] diffTable = IOUtils.outputListComparison(matchingOutputs, uploadedOutputs, "Original", "Uploaded", false).getFirst().split("\\R");
                        String subTable = String.join(System.lineSeparator(), Arrays.copyOfRange(diffTable, 1, diffTable.length - 1));
                        rowMismatchErrors.add(String.format(" - ID=%s output names, classes, or TrustyAI types do not match:%n%s%n",
                                entry.getKey(), subTable));
                        outputsAligned = false;
                    }
                }

                if (inputsAligned && outputsAligned) {
                    groundTruthsToSave.add(new SimplePrediction(new PredictionInput(new ArrayList<>()), entry.getValue().getOutput(), entry.getValue().getExecutionId()));
                }
            }
        }

        // if any specified input row does not match a recorded inference input, throw error and list all mismatches
        if (!rowMismatchErrors.isEmpty()) {
            // returning early (should) prevent the dataframe changes being saved if mismatches are found
            return Pair.of(null, "Found fatal mismatches between uploaded data and recorded inference data:\n" + String.join("\n", rowMismatchErrors));
        }

        if (groundTruthsToSave.isEmpty()) {
            return Pair.of(null, "No ground truths provided in request body.");
        }

        // if no errors, save ground truths
        Dataframe groundTruthDataframe;
        if (dataSource.get().hasGroundTruths(jointPayload.getModelName())) {
            groundTruthDataframe = dataSource.get().getGroundTruths(jointPayload.getModelName());
            groundTruthDataframe.addPredictions(groundTruthsToSave);
        } else {
            groundTruthDataframe = Dataframe.createFrom(groundTruthsToSave);
        }
        dataSource.get().saveGroundTruths(groundTruthDataframe, jointPayload.getModelName());

        return Pair.of(inferenceDataframe, idToPrediction.size() + " ground truths successfully added to " + DataSource.getGroundTruthName(jointPayload.getModelName()) + ".");
    }

    // handle the prediction putput parsing of the upload endpoint
    // return List, null if successful
    // return null, Response.SERVER_ERROR if unsuccessful
    private Pair<List<PredictionOutput>, Response> parsePredictionOutputs(ModelInferJointPayload jointPayload, int nPredictionInputs) {
        List<PredictionOutput> predictionOutputs;
        if (jointPayload.getResponse() != null && jointPayload.getResponse().getTensorPayloads().length > 0) {
            // parse outputs
            ModelInferResponse.Builder inferResponseBuilder = ModelInferResponse.newBuilder();
            inferResponseBuilder.setModelName(jointPayload.getModelName());
            try {
                UploadUtils.populateResponseBuilder(inferResponseBuilder, jointPayload.getResponse());
            } catch (IllegalArgumentException e) {
                //thrown in case of mishapen output
                return Pair.of(null, Response.serverError().entity(e.getMessage()).status(Response.Status.BAD_REQUEST).build());
            }

            try {
                predictionOutputs = TensorConverter.parseKserveModelInferResponse(
                        inferResponseBuilder.build(),
                        nPredictionInputs);
            } catch (IllegalArgumentException e) {
                return Pair.of(null, Response.serverError()
                        .entity("Error parsing output payload: " + e.getMessage())
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .build());
            }
        } else {
            //todo, grab automatically from model
            return Pair.of(null, Response.serverError()
                    .entity("No output payload specified in request")
                    .status(Response.Status.BAD_REQUEST)
                    .build());
        }
        return Pair.of(predictionOutputs, null);
    }

    // handle tagging logic
    // if successful, return empty
    // if failure, return the failure response
    private Optional<Response> handleTagging(ModelInferJointPayload jointPayload, Dataframe dataframe) {
        // tag the points accordingly
        String dpSource;
        if (jointPayload.getDataTag() == null) {
            dpSource = "";
        } else {
            dpSource = jointPayload.getDataTag();
            Optional<String> tagValidationErrorMessage = GenericValidationUtils.validateNewDataTag(dpSource);
            if (tagValidationErrorMessage.isPresent()) {
                return Optional.of(Response.serverError().entity(tagValidationErrorMessage.get()).status(Response.Status.BAD_REQUEST).build());
            }
        }

        HashMap<String, List<List<Integer>>> taggingMap = new HashMap<>();
        taggingMap.put(dpSource, List.of(List.of(0, dataframe.getRowDimension())));
        dataframe.tagDataPoints(taggingMap);
        return Optional.empty();
    }

    @POST
    @Operation(summary = "Upload a batch of model data to TrustyAI.")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/upload")
    public Response upload(ModelInferJointPayload jointPayload) throws DataframeCreateException {
        if (jointPayload.getRequest() == null || jointPayload.getRequest().getTensorPayloads().length < 1) {
            return Response.serverError().entity("Directly uploaded datapoints must specify at least one `inputs` field.").status(Response.Status.BAD_REQUEST).build();
        }

        // parse inputs ================================================================================================
        ModelInferRequest.Builder inferRequestBuilder = ModelInferRequest.newBuilder();
        inferRequestBuilder.setModelName(jointPayload.getModelName());
        try {
            UploadUtils.populateRequestBuilder(inferRequestBuilder, jointPayload.getRequest());
        } catch (IllegalArgumentException e) {
            return Response.serverError().entity(e.getMessage()).status(Response.Status.BAD_REQUEST).build();
        }

        List<PredictionInput> predictionInputs;
        String[] providedIDs = jointPayload.getRequest().getTensorPayloads()[0].getExecutionIDs();
        try {
            predictionInputs = TensorConverter.parseKserveModelInferRequest(inferRequestBuilder.build());
            if (providedIDs != null && providedIDs.length != predictionInputs.size()) {
                return Response.serverError().entity(
                        String.format(
                                "Mismatching number of inputs and execution IDs: %d inputs were provided versus %d execution IDs.",
                                predictionInputs.size(), providedIDs.length))
                        .build();
            }
        } catch (IllegalArgumentException e) {
            return Response.serverError()
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error parsing input payload: " + e.getMessage())
                    .build();
        }

        // parse outputs ===============================================================================================
        Pair<List<PredictionOutput>, Response> predictionOutputsOrResponse = parsePredictionOutputs(jointPayload, predictionInputs.size());
        if (predictionOutputsOrResponse.getRight() != null) {
            return predictionOutputsOrResponse.getRight();
        }
        List<PredictionOutput> predictionOutputs = predictionOutputsOrResponse.getLeft();

        // combine inputs and outputs ==================================================================================
        final Dataframe dataframe;
        String responseMessage;
        if (jointPayload.isGroundTruth()) {
            if (!dataSource.get().hasMetadata(jointPayload.getModelName())) {
                return Response.serverError()
                        .entity("No TrustyAI dataframe named " + jointPayload.getModelName() + ". Ground truths can only be uploaded for extant dataframes.")
                        .build();
            }

            if (providedIDs == null) {
                return Response.serverError()
                        .entity("No execution IDs were provided. When uploading ground truths, all inputs must have a correspond TrustyAI Execution ID to" +
                                " correlate them with existing inferences. The available execution IDs can be found within the /inputs/ directory of the TrustyAI service instance.")
                        .status(Response.Status.BAD_REQUEST)
                        .build();
            }
            Pair<Dataframe, String> groundTruthProcessResult = handleGroundTruths(
                    jointPayload,
                    predictionInputs,
                    predictionOutputs,
                    providedIDs);
            // if the returned dataframe is null, we have an error, the message of which is in the pair string
            if (groundTruthProcessResult.getLeft() == null) {
                return Response.serverError().status(Response.Status.BAD_REQUEST).entity(groundTruthProcessResult.getRight()).build();
            }
            dataframe = groundTruthProcessResult.getLeft();
            responseMessage = groundTruthProcessResult.getRight();
        } else {
            if (providedIDs != null) {
                LOG.warn("The provided inputs specify execution IDs, but these are only used for uploading ground truths and ignored otherwise.");
            }

            final List<Prediction> predictions =
                    IntStream.range(0, predictionInputs.size())
                            .mapToObj(i -> new SimplePrediction(predictionInputs.get(i), predictionOutputs.get(i))).collect(Collectors.toList());
            dataframe = Dataframe.createFrom(predictions);
            responseMessage = predictions.size() +
                    " datapoints successfully added to " +
                    jointPayload.getModelName() +
                    " data.";
        }

        // tag the points accordingly ==================================================================================
        Optional<Response> tagResponse = handleTagging(jointPayload, dataframe);
        if (tagResponse.isPresent()) {
            return tagResponse.get();
        }

        // save ========================================================================================================
        dataSource.get().saveDataframe(dataframe, jointPayload.getModelName());
        return Response.ok().entity(responseMessage).build();
    }
}
