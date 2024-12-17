package org.kie.trustyai.service.endpoints.metrics.fairness.group;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.jboss.resteasy.reactive.RestResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kie.trustyai.explainability.model.Prediction;
import org.kie.trustyai.explainability.model.PredictionMetadata;
import org.kie.trustyai.explainability.model.SimplePrediction;
import org.kie.trustyai.explainability.model.dataframe.Dataframe;
import org.kie.trustyai.service.data.datasources.DataSource;
import org.kie.trustyai.service.endpoints.metrics.BaseEndpoint;
import org.kie.trustyai.service.endpoints.metrics.RequestPayloadGenerator;
import org.kie.trustyai.service.mocks.MockPrometheusScheduler;
import org.kie.trustyai.service.payloads.BaseScheduledResponse;
import org.kie.trustyai.service.payloads.metrics.BaseMetricResponse;
import org.kie.trustyai.service.payloads.metrics.fairness.group.AdvancedGroupMetricRequest;
import org.kie.trustyai.service.payloads.metrics.fairness.group.GroupMetricRequest;
import org.kie.trustyai.service.payloads.scheduler.ScheduleId;
import org.kie.trustyai.service.payloads.scheduler.ScheduleList;
import org.kie.trustyai.service.payloads.service.NameMapping;
import org.kie.trustyai.service.utils.DataframeGenerators;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.restassured.http.ContentType;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

abstract class GroupStatisticalParityDifferenceEndpointBaseTest {

    protected static final String MODEL_ID = "example1";
    protected static final int N_SAMPLES = 100;
    @Inject
    Instance<DataSource> datasource;

    @Inject
    Instance<MockPrometheusScheduler> scheduler;

    abstract void populate();

    @Test
    void get() {
        when().get()
                .then()
                .statusCode(405)
                .body(is(""));
    }

    @Test
    void postCorrect() {
        populate();

        final GroupMetricRequest payload = RequestPayloadGenerator.correct();

        final BaseMetricResponse response = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post()
                .then()
                .statusCode(200)
                .extract()
                .body().as(BaseMetricResponse.class);

        assertEquals("metric", response.getType());
        assertEquals("SPD", response.getName());
        assertFalse(Double.isNaN(response.getValue()));
    }

    @Test
    void postThresh() throws JsonProcessingException {
        populate();

        // with large threshold, the DIR is inside bounds
        GroupMetricRequest payload = RequestPayloadGenerator.correct();
        payload.setThresholdDelta(.5);
        BaseMetricResponse response = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post()
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .body().as(BaseMetricResponse.class);

        assertEquals("metric", response.getType());
        assertEquals("SPD", response.getName());
        assertFalse(response.getThresholds().outsideBounds);

        // with negative threshold, every SPD is outside bounds
        payload = RequestPayloadGenerator.correct();
        payload.setThresholdDelta(-1.);
        response = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post()
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .body().as(BaseMetricResponse.class);

        assertEquals("metric", response.getType());
        assertEquals("SPD", response.getName());
        assertTrue(response.getThresholds().outsideBounds);
    }

    @Test
    @DisplayName("SPD request with incorrect type")
    void postIncorrectType() {
        populate();

        final GroupMetricRequest payload = RequestPayloadGenerator.incorrectType();

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post()
                .then()
                .statusCode(RestResponse.StatusCode.BAD_REQUEST)
                .body(containsString("got 'male', expected object compatible with 'INT32'"));
    }

    @Test
    @DisplayName("SPD request with incorrect input")
    void postIncorrectInput() {
        populate();

        final GroupMetricRequest payload = RequestPayloadGenerator.incorrectInput();

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post()
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .body(containsString("No protected attribute found with name=city"));

    }

    @Test
    void postUnknownType() {
        final Map<String, Object> payload = RequestPayloadGenerator.unknownType();

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post()
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .body(any(String.class));

    }

    @Test
    void listSchedules() {
        populate();

        // No schedule request made yet
        final ScheduleList emptyList = given()
                .when()
                .get("/requests")
                .then().statusCode(200).extract().body().as(ScheduleList.class);

        assertEquals(0, emptyList.requests.size());

        // Perform multiple schedule requests
        final GroupMetricRequest payload = RequestPayloadGenerator.correct();
        final BaseScheduledResponse firstRequest = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/request")
                .then().statusCode(200).extract().body().as(BaseScheduledResponse.class);

        assertNotNull(firstRequest.getRequestId());

        final BaseScheduledResponse secondRequest = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/request")
                .then().statusCode(200).extract().body().as(BaseScheduledResponse.class);

        assertNotNull(secondRequest.getRequestId());

        ScheduleList scheduleList = given()
                .when()
                .get("/requests")
                .then().statusCode(200).extract().body().as(ScheduleList.class);

        // Correct number of active requests
        assertEquals(2, scheduleList.requests.size());

        // Remove one request
        final ScheduleId firstRequestId = new ScheduleId();
        firstRequestId.requestId = firstRequest.getRequestId();
        given().contentType(ContentType.JSON).when().body(firstRequestId).delete("/request")
                .then().statusCode(200).body(is("Removed"));

        scheduleList = given()
                .when()
                .get("/requests")
                .then().statusCode(200).extract().body().as(ScheduleList.class);

        // Correct number of active requests
        assertEquals(1, scheduleList.requests.size());

        // Remove second request
        final ScheduleId secondRequestId = new ScheduleId();
        secondRequestId.requestId = secondRequest.getRequestId();
        given().contentType(ContentType.JSON).when().body(secondRequestId).delete("/request")
                .then().statusCode(200).body(is("Removed"));

        scheduleList = given()
                .when()
                .get("/requests")
                .then().statusCode(200).extract().body().as(ScheduleList.class);

        // Correct number of active requests
        assertEquals(0, scheduleList.requests.size());

        // Remove non-existing request
        final ScheduleId nonExistingRequestId = new ScheduleId();
        nonExistingRequestId.requestId = secondRequest.getRequestId();
        given().contentType(ContentType.JSON).when().body(nonExistingRequestId).delete("/request")
                .then().statusCode(RestResponse.StatusCode.NOT_FOUND).body(is(String.format(BaseEndpoint.REQUEST_ID_NOT_FOUND_FMT, secondRequest.getRequestId())));

        scheduleList = given()
                .when()
                .get("/requests")
                .then().statusCode(200).extract().body().as(ScheduleList.class);

        // Correct number of active requests
        assertEquals(0, scheduleList.requests.size());
    }

    @Test
    void requestWrongType() {
        populate();

        // No schedule request made yet
        final ScheduleList emptyList = given()
                .when()
                .get("/requests")
                .then().statusCode(200).extract().body().as(ScheduleList.class);

        assertEquals(0, emptyList.requests.size());

        // Perform multiple schedule requests
        final GroupMetricRequest payload = RequestPayloadGenerator.correct();
        final BaseScheduledResponse firstRequest = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/request")
                .then().statusCode(200).extract().body().as(BaseScheduledResponse.class);

        assertNotNull(firstRequest.getRequestId());

        final GroupMetricRequest wrongPayload = RequestPayloadGenerator.incorrectType();
        given()
                .contentType(ContentType.JSON)
                .body(wrongPayload)
                .when()
                .post("/request")
                .then().statusCode(RestResponse.StatusCode.BAD_REQUEST)
                .body(containsString("got 'male', expected object compatible with 'INT32'"));

        ScheduleList scheduleList = given()
                .when()
                .get("/requests")
                .then().statusCode(200).extract().body().as(ScheduleList.class);

        // Correct number of active requests
        assertEquals(1, scheduleList.requests.size());

        // Remove one request
        final ScheduleId firstRequestId = new ScheduleId();
        firstRequestId.requestId = firstRequest.getRequestId();
        given().contentType(ContentType.JSON).when().body(firstRequestId).delete("/request")
                .then().statusCode(200).body(is("Removed"));

        scheduleList = given()
                .when()
                .get("/requests")
                .then().statusCode(200).extract().body().as(ScheduleList.class);

        // Correct number of active requests
        assertEquals(0, scheduleList.requests.size());

    }

    @Test
    void requestUnknowType() {
        populate();

        // No schedule request made yet
        final ScheduleList emptyList = given()
                .when()
                .get("/requests")
                .then().statusCode(200).extract().body().as(ScheduleList.class);

        assertEquals(0, emptyList.requests.size());

        // Perform multiple schedule requests
        final GroupMetricRequest payload = RequestPayloadGenerator.correct();
        final BaseScheduledResponse firstRequest = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/request")
                .then().statusCode(200).extract().body().as(BaseScheduledResponse.class);

        assertNotNull(firstRequest.getRequestId());

        final Map<String, Object> unknownPayload = RequestPayloadGenerator.unknownType();
        given()
                .contentType(ContentType.JSON)
                .body(unknownPayload)
                .when()
                .post("/request")
                .then().statusCode(RestResponse.StatusCode.BAD_REQUEST);

        ScheduleList scheduleList = given()
                .when()
                .get("/requests")
                .then().statusCode(200).extract().body().as(ScheduleList.class);

        // Correct number of active requests
        assertEquals(1, scheduleList.requests.size());

        // Remove one request
        final ScheduleId firstRequestId = new ScheduleId();
        firstRequestId.requestId = firstRequest.getRequestId();
        given().contentType(ContentType.JSON).when().body(firstRequestId).delete("/request")
                .then().statusCode(200).body(is("Removed"));

        scheduleList = given()
                .when()
                .get("/requests")
                .then().statusCode(200).extract().body().as(ScheduleList.class);

        // Correct number of active requests
        assertEquals(0, scheduleList.requests.size());

    }

    @Test
    void postCorrectFilteringSynthetic() throws JsonProcessingException {
        populate();

        final GroupMetricRequest payload = RequestPayloadGenerator.correct();

        final BaseMetricResponse response = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post()
                .then()
                .statusCode(200)
                .extract()
                .body().as(BaseMetricResponse.class);

        assertEquals("metric", response.getType());
        assertEquals("SPD", response.getName());
        Double value = response.getValue();
        assertFalse(Double.isNaN(value));

        final Dataframe dataframe = DataframeGenerators.generateRandomDataframe(N_SAMPLES);
        Prediction prediction = dataframe.asPredictions().get(0);
        PredictionMetadata predictionMetadata = new PredictionMetadata("123", LocalDateTime.now(), Dataframe.InternalTags.SYNTHETIC.get());
        Prediction newPrediction = new SimplePrediction(prediction.getInput(), prediction.getOutput(), predictionMetadata);
        Dataframe newDataframe = Dataframe.createFrom(newPrediction);

        datasource.get().saveDataframe(newDataframe, MODEL_ID);
        final Dataframe syntheticDataframe = DataframeGenerators.generateRandomSyntheticDataframe(N_SAMPLES);
        datasource.get().saveDataframe(syntheticDataframe, MODEL_ID);

        final BaseMetricResponse responseSecond = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post()
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .body().as(BaseMetricResponse.class);

        assertEquals("metric", responseSecond.getType());
        assertEquals("SPD", responseSecond.getName());
        assertEquals(value, responseSecond.getValue());
    }

    @Test
    @DisplayName("SPD request with only synthetic data")
    void postCorrectFilteringOnlySynthetic() throws JsonProcessingException {
        final GroupMetricRequest payload = RequestPayloadGenerator.correct();

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post()
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode());

        final Dataframe syntheticDataframe = DataframeGenerators.generateRandomSyntheticDataframe(N_SAMPLES);
        datasource.get().saveDataframe(syntheticDataframe, MODEL_ID);

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post()
                .then()
                .statusCode(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    }

    @Test
    @DisplayName("SPD request with only synthetic data and then organic")
    void postCorrectFilteringOnlySyntheticThenOrganic() throws JsonProcessingException {
        final GroupMetricRequest payload = RequestPayloadGenerator.correct();

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post()
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode());

        final Dataframe syntheticDataframe = DataframeGenerators.generateRandomSyntheticDataframe(N_SAMPLES);
        datasource.get().saveDataframe(syntheticDataframe, MODEL_ID);

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post()
                .then()
                .statusCode(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());

        final Dataframe organicDataframe = DataframeGenerators.generateRandomDataframe(N_SAMPLES);
        datasource.get().saveDataframe(organicDataframe, MODEL_ID);

        final BaseMetricResponse responseSecond = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post()
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .body().as(BaseMetricResponse.class);

        assertEquals("metric", responseSecond.getType());
        assertEquals("SPD", responseSecond.getName());
        assertFalse(Double.isNaN(responseSecond.getValue()));
    }

    @Test
    void postCorrectNameMapped() throws InterruptedException {
        populate();

        final GroupMetricRequest payload = RequestPayloadGenerator.correct();
        payload.setProtectedAttribute("Gender Mapped");

        HashMap<String, String> inputMapping = new HashMap<>();
        HashMap<String, String> outputMapping = new HashMap<>();
        inputMapping.put("age", "Age Mapped");
        inputMapping.put("gender", "Gender Mapped");
        NameMapping nameMapping = new NameMapping(MODEL_ID, inputMapping, outputMapping);

        given()
                .contentType(ContentType.JSON)
                .body(nameMapping)
                .basePath("/info")
                .when().post("/names")
                .then()
                .statusCode(200)
                .body(is("Feature and output name mapping successfully applied."));

        final BaseMetricResponse response = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post()
                .then()
                .statusCode(200)
                .extract()
                .body().as(BaseMetricResponse.class);

        assertEquals("metric", response.getType());
        assertEquals("SPD", response.getName());
        assertFalse(Double.isNaN(response.getValue()));
    }

    @Test
    void postCorrectNameMappedRequest() throws InterruptedException {
        populate();

        final GroupMetricRequest payload = RequestPayloadGenerator.correct();
        payload.setProtectedAttribute("Gender Mapped");

        HashMap<String, String> inputMapping = new HashMap<>();
        HashMap<String, String> outputMapping = new HashMap<>();
        inputMapping.put("age", "Age Mapped");
        inputMapping.put("gender", "Gender Mapped");
        NameMapping nameMapping = new NameMapping(MODEL_ID, inputMapping, outputMapping);

        given()
                .contentType(ContentType.JSON)
                .body(nameMapping)
                .basePath("/info")
                .when().post("/names")
                .then()
                .statusCode(200)
                .body(is("Feature and output name mapping successfully applied."));

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/request/")
                .then()
                .statusCode(200);

        assertDoesNotThrow(() -> scheduler.get().calculateManual(true));
    }

    // === ADVANCED METRICS ======
    @Test
    void postAdvancedCorrect() throws JsonProcessingException {
        populate();

        final AdvancedGroupMetricRequest payload = RequestPayloadGenerator.advancedCorrect();

        final BaseMetricResponse response = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/advanced")
                .then().statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .body().as(BaseMetricResponse.class);

        assertEquals("metric", response.getType());
        assertEquals("SPD_ADVANCED", response.getName());
        assertFalse(Double.isNaN(response.getValue()));
    }

    @Test
    void postAdvancedIncorrect() throws JsonProcessingException {
        populate();

        final AdvancedGroupMetricRequest payload = RequestPayloadGenerator.advancedIncorrect();

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/advanced")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().response().then()
                .body(containsString("No feature or output found with name=FIELD_DOES_NOT_EXIST."))
                .body(containsString("Invalid type for output=income: got 'WRONG_VALUE_TYPE', expected object compatible with 'INT32'"))
                .body(containsString("RowMatch operation must be one of [BETWEEN, EQUALS], got NO_SUCH_OPERATION"));
    }
}
