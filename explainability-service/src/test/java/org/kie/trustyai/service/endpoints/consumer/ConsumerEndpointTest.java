package org.kie.trustyai.service.endpoints.consumer;

import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.jboss.resteasy.reactive.RestResponse;
import org.junit.jupiter.api.*;
import org.kie.trustyai.connectors.kserve.v2.grpc.ModelInferRequest;
import org.kie.trustyai.connectors.kserve.v2.grpc.ModelInferResponse;
import org.kie.trustyai.explainability.model.*;
import org.kie.trustyai.service.BaseTestProfile;
import org.kie.trustyai.service.PayloadProducer;
import org.kie.trustyai.service.data.exceptions.DataframeCreateException;
import org.kie.trustyai.service.mocks.MockDatasource;
import org.kie.trustyai.service.mocks.MockMemoryStorage;
import org.kie.trustyai.service.payloads.consumer.InferencePartialPayload;
import org.kie.trustyai.service.payloads.consumer.InferencePayload;
import org.kie.trustyai.service.payloads.consumer.PartialKind;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kie.trustyai.service.PayloadProducer.MODEL_A_ID;

@QuarkusTest
@TestProfile(BaseTestProfile.class)
@TestHTTPEndpoint(ConsumerEndpoint.class)
class ConsumerEndpointTest {

    @Inject
    Instance<MockDatasource> datasource;

    @Inject
    Instance<MockMemoryStorage> storage;

    /**
     * Empty the storage before each test.
     */
    @BeforeEach
    void emptyStorage() {
        datasource.get().empty();
        storage.get().emptyStorage();
    }

    @Test
    void consumeFullPostCorrectModelA() {
        final InferencePayload payload = PayloadProducer.getInferencePayloadA(0);

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/full")
                .then()
                .statusCode(RestResponse.StatusCode.OK)

                .body(is(""));

        final Dataframe dataframe = datasource.get().getDataframe(payload.getModelId());
        assertEquals(1, dataframe.getRowDimension());
        assertEquals(4, dataframe.getColumnDimension());
        assertEquals(3, dataframe.getInputsCount());
        assertEquals(1, dataframe.getOutputsCount());
    }

    @Test
    void consumeFullPostIncorrectModelA() {
        final InferencePayload payload = PayloadProducer.getInferencePayloadA(1);
        // Mangle inputs
        payload.setInput(payload.getInput().substring(0, 10) + "X" + payload.getInput().substring(11));
        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/full")
                .then()
                .statusCode(RestResponse.StatusCode.INTERNAL_SERVER_ERROR)

                .body(is(""));
    }

    @Test
    void consumeFullPostCorrectModelB() {
        final InferencePayload payload = PayloadProducer.getInferencePayloadB(1);

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/full")
                .then()
                .statusCode(RestResponse.StatusCode.OK)

                .body(is(""));

        final Dataframe dataframe = datasource.get().getDataframe(PayloadProducer.MODEL_B_ID);
        assertEquals(1, dataframe.getRowDimension());
        assertEquals(5, dataframe.getColumnDimension());
        assertEquals(3, dataframe.getInputsCount());
        assertEquals(2, dataframe.getOutputsCount());
    }

    @Test
    void consumeFullPostIncorrectModelB() {
        final InferencePayload payload = PayloadProducer.getInferencePayloadA(1);
        // Mangle inputs
        payload.setInput(payload.getInput().substring(0, 10) + "X" + payload.getInput().substring(11));
        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/full")
                .then()
                .statusCode(RestResponse.StatusCode.INTERNAL_SERVER_ERROR)

                .body(is(""));
    }

    @Test
    void consumePartialPostInputsOnly() {
        final String id = UUID.randomUUID().toString();
        for (int i = 0; i < 5; i++) {
            final InferencePartialPayload payload = PayloadProducer.getInferencePartialPayloadInput(id, i);
            given()
                    .contentType(ContentType.JSON)
                    .body(payload)
                    .when().post()
                    .then()
                    .statusCode(RestResponse.StatusCode.OK)
                    .body(is(""));
        }
        Exception exception = assertThrows(DataframeCreateException.class, () -> {
            final Dataframe dataframe = datasource.get().getDataframe(MODEL_A_ID);
        });
        assertEquals("Data file '" + MODEL_A_ID + "-data.csv' not found", exception.getMessage());

    }

    @Test
    void consumePartialPostOutputsOnly() {
        final String id = UUID.randomUUID().toString();
        for (int i = 0; i < 5; i++) {
            final InferencePartialPayload payload = PayloadProducer.getInferencePartialPayloadOutput(id, i);
            given()
                    .contentType(ContentType.JSON)
                    .body(payload)
                    .when().post()
                    .then()
                    .statusCode(RestResponse.StatusCode.OK)
                    .body(is(""));
        }
        Exception exception = assertThrows(DataframeCreateException.class, () -> {
            final Dataframe dataframe = datasource.get().getDataframe(MODEL_A_ID);
        });
        assertEquals("Data file '" + MODEL_A_ID + "-data.csv' not found", exception.getMessage());

    }

    @Test
    void consumePartialPostSome() {
        final List<String> ids = IntStream.range(0, 5).mapToObj(i -> UUID.randomUUID().toString()).collect(Collectors.toList());
        for (int i = 0; i < 5; i++) {
            final InferencePartialPayload payload = PayloadProducer.getInferencePartialPayloadInput(ids.get(i), i);
            given()
                    .contentType(ContentType.JSON)
                    .body(payload)
                    .when().post()
                    .then()
                    .statusCode(RestResponse.StatusCode.OK)
                    .body(is(""));
        }
        for (int i = 0; i < 3; i++) {
            final InferencePartialPayload payload = PayloadProducer.getInferencePartialPayloadOutput(ids.get(i), i);
            given()
                    .contentType(ContentType.JSON)
                    .body(payload)
                    .when().post()
                    .then()
                    .statusCode(RestResponse.StatusCode.OK)
                    .body(is(""));
        }

        final Dataframe dataframe = datasource.get().getDataframe(MODEL_A_ID);
        assertEquals(3, dataframe.getRowDimension());

    }

    @Test
    void consumePartialPostAll() {
        final List<String> ids = IntStream.range(0, 5).mapToObj(i -> UUID.randomUUID().toString()).collect(Collectors.toList());
        for (int i = 0; i < 5; i++) {
            final InferencePartialPayload payload = PayloadProducer.getInferencePartialPayloadInput(ids.get(i), i);
            given()
                    .contentType(ContentType.JSON)
                    .body(payload)
                    .when().post()
                    .then()
                    .statusCode(RestResponse.StatusCode.OK)
                    .body(is(""));
        }
        for (int i = 0; i < 5; i++) {
            final InferencePartialPayload payload = PayloadProducer.getInferencePartialPayloadOutput(ids.get(i), i);
            given()
                    .contentType(ContentType.JSON)
                    .body(payload)
                    .when().post()
                    .then()
                    .statusCode(RestResponse.StatusCode.OK)
                    .body(is(""));
        }

        final Dataframe dataframe = datasource.get().getDataframe(MODEL_A_ID);
        assertEquals(5, dataframe.getRowDimension());

    }

    @Test
    void consumeDifferentSchemas() {
        final InferencePayload payloadModelA = PayloadProducer.getInferencePayloadA(0);
        final InferencePayload payloadModelB = PayloadProducer.getInferencePayloadB(0);

        // Generate two partial payloads with consistent metadata (from the same model)
        final String id = "This schema is OK";
        final InferencePartialPayload partialRequestPayloadA = new InferencePartialPayload();
        partialRequestPayloadA.setId(id);
        partialRequestPayloadA.setData(payloadModelA.getInput());
        partialRequestPayloadA.setModelId(MODEL_A_ID);
        partialRequestPayloadA.setKind(PartialKind.request);
        given()
                .contentType(ContentType.JSON)
                .body(partialRequestPayloadA)
                .when().post()
                .then()
                .statusCode(RestResponse.StatusCode.OK)
                .body(is(""));
        final InferencePartialPayload partialResponsePayloadA = new InferencePartialPayload();
        partialResponsePayloadA.setId(id);
        partialResponsePayloadA.setData(payloadModelA.getOutput());
        partialResponsePayloadA.setModelId(MODEL_A_ID);
        partialResponsePayloadA.setKind(PartialKind.response);
        given()
                .contentType(ContentType.JSON)
                .body(partialResponsePayloadA)
                .when().post()
                .then()
                .statusCode(RestResponse.StatusCode.OK)
                .body(is(""));

        // Generate two partial payloads with inconsistent metadata (from different models)
        final String newId = "This schema is NOT OK";
        final InferencePartialPayload partialRequestPayloadAWrongSchema = new InferencePartialPayload();
        partialRequestPayloadAWrongSchema.setId(newId);
        partialRequestPayloadAWrongSchema.setData(payloadModelA.getInput());
        partialRequestPayloadAWrongSchema.setModelId(MODEL_A_ID);
        partialRequestPayloadAWrongSchema.setKind(PartialKind.request);
        given()
                .contentType(ContentType.JSON)
                .body(partialRequestPayloadAWrongSchema)
                .when().post()
                .then()
                .statusCode(RestResponse.StatusCode.OK)
                .body(is(""));

        final InferencePartialPayload partialResponsePayloadBWrongSchema = new InferencePartialPayload();
        partialResponsePayloadBWrongSchema.setId(newId);
        partialResponsePayloadBWrongSchema.setData(PayloadProducer.getInferencePayloadB(0).getOutput());
        partialResponsePayloadBWrongSchema.setModelId(MODEL_A_ID);
        partialResponsePayloadBWrongSchema.setKind(PartialKind.response);
        given()
                .contentType(ContentType.JSON)
                .body(partialResponsePayloadBWrongSchema)
                .when().post()
                .then()
                .statusCode(RestResponse.StatusCode.BAD_REQUEST)
                .body(is("Invalid schema for payload response id=" + newId + ", Payload schema and stored schema are not the same"));
    }

    @Test
    void consumeSingleInputMultiOutput() {
        final Prediction prediction = new SimplePrediction(
                new PredictionInput(
                        List.of(FeatureFactory.newNumericalFeature("f-1", 10.0))
                ),
                new PredictionOutput(
                        List.of(
                                new Output("output-1", Type.NUMBER, new Value(1.0), 1.0),
                                new Output("output-2", Type.NUMBER, new Value(2.0), 1.0)
                        )
                )
        );

        final TensorDataframe df = TensorDataframe.createFrom(List.of(prediction));
        final String modelId = "example-1";

        ModelInferRequest.InferInputTensor.Builder requestTensor = df.rowAsSingleArrayInputTensor(0, "input");
        final ModelInferRequest.Builder request = ModelInferRequest.newBuilder();
        request.addInputs(requestTensor);
        request.setModelName(modelId);
        request.setModelVersion("0.0.1");

        final String id = UUID.randomUUID().toString();

        InferencePartialPayload requestPayload = new InferencePartialPayload();
        requestPayload.setData(Base64.getEncoder().encodeToString(request.build().toByteArray()));
        requestPayload.setId(id);
        requestPayload.setKind(PartialKind.request);
        requestPayload.setModelId("");

        given()
                .contentType(ContentType.JSON)
                .body(requestPayload)
                .when().post()
                .then()
                .statusCode(RestResponse.StatusCode.OK)
                .body(is(""));

        final ModelInferResponse.InferOutputTensor.Builder responseTensor = df.rowAsSingleArrayOutputTensor(0, "input");
        final ModelInferResponse.Builder response = ModelInferResponse.newBuilder();
        response.addOutputs(responseTensor);
        response.setModelName(modelId);
        response.setModelVersion("0.0.1");

        InferencePartialPayload responsePayload = new InferencePartialPayload();
        responsePayload.setData(Base64.getEncoder().encodeToString(response.build().toByteArray()));
        responsePayload.setId(id);
        responsePayload.setKind(PartialKind.response);
        responsePayload.setModelId(modelId);

        given()
                .contentType(ContentType.JSON)
                .body(responsePayload)
                .when().post()
                .then()
                .statusCode(RestResponse.StatusCode.OK)
                .body(is(""));


        final Dataframe storedDf = datasource.get().getDataframe(modelId);
        assertEquals(prediction.getInput().getFeatures().size(), storedDf.getInputsCount());
        assertEquals(prediction.getOutput().getOutputs().size(), storedDf.getOutputsCount());
        assertEquals(1, storedDf.getRowDimension());
        assertEquals(prediction.getInput().getFeatures().get(0).getValue().asNumber(), storedDf.getValue(0, 0).asNumber());
        assertEquals(prediction.getOutput().getOutputs().get(0).getValue().asNumber(), storedDf.getValue(0, 1).asNumber());
        assertEquals(prediction.getOutput().getOutputs().get(1).getValue().asNumber(), storedDf.getValue(0, 2).asNumber());
    }

}