package org.kie.trustyai.service.endpoints.metrics.fairness.group;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jboss.resteasy.reactive.RestResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kie.trustyai.service.config.ServiceConfig;
import org.kie.trustyai.service.data.datasources.DataSource;
import org.kie.trustyai.service.endpoints.metrics.RequestPayloadGenerator;
import org.kie.trustyai.service.mocks.MockPrometheusScheduler;
import org.kie.trustyai.service.payloads.BaseScheduledResponse;
import org.kie.trustyai.service.payloads.metrics.BaseMetricRequest;
import org.kie.trustyai.service.payloads.metrics.fairness.group.AdvancedGroupMetricRequest;
import org.kie.trustyai.service.payloads.metrics.fairness.group.GroupMetricRequest;
import org.kie.trustyai.service.payloads.scheduler.ScheduleId;
import org.kie.trustyai.service.payloads.scheduler.ScheduleList;
import org.kie.trustyai.service.payloads.service.NameMapping;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.restassured.http.ContentType;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

abstract class GroupStatisticalParityDifferenceRequestsEndpointBaseTest {

    protected static final String MODEL_ID = "example1";
    protected static final int N_SAMPLES = 100;
    @Inject
    Instance<DataSource> datasource;

    @Inject
    Instance<MockPrometheusScheduler> scheduler;

    @Inject
    Instance<ServiceConfig> serviceConfig;

    @AfterEach
    void clearRequests() {
        scheduler.get().getAllRequests().clear();
    }

    /**
     * When no batch size is specified in the request, the service's default batch size should be used
     */
    @Test
    void postCorrectRequestDefaultBatchSize() {

        final GroupMetricRequest payload = RequestPayloadGenerator.correct();

        final BaseScheduledResponse response = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/request")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .body().as(BaseScheduledResponse.class);

        // Get stored request
        final GroupMetricRequest request = (GroupMetricRequest) scheduler
                .get()
                .getRequests("SPD")
                .get(response.getRequestId());

        final int defaultBatchSize = serviceConfig.get().batchSize().getAsInt();
        assertEquals(defaultBatchSize, request.getBatchSize());
    }

    /**
     * When a batch size is specified in the request, that value should be used
     */
    @Test
    void postCorrectRequestBatchSize() {

        final int BATCH_SIZE = 1000;

        final GroupMetricRequest payload = RequestPayloadGenerator.correct();
        payload.setBatchSize(BATCH_SIZE);

        final BaseScheduledResponse response = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/request")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .body().as(BaseScheduledResponse.class);

        // Get stored request
        final GroupMetricRequest request = (GroupMetricRequest) scheduler
                .get()
                .getRequests("SPD")
                .get(response.getRequestId());

        assertEquals(BATCH_SIZE, request.getBatchSize());
    }

    /**
     * When an invalid batch size is specified in the request, an error should be thrown
     */
    @Test
    void postIncorrectRequestBatchSize() {

        final int BATCH_SIZE = -1;

        final GroupMetricRequest payload = RequestPayloadGenerator.correct();
        payload.setBatchSize(BATCH_SIZE);

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/request")
                .then()
                .statusCode(RestResponse.StatusCode.BAD_REQUEST)
                .body(containsString("Request batch size must be bigger than 0."));

        // Get stored request
        final Map<UUID, BaseMetricRequest> requests = scheduler
                .get()
                .getRequests("SPD");

        assertTrue(requests.isEmpty());
    }

    @DisplayName("SPD request with custom batch size")
    void requestCustomBatchSize() {

        // No schedule request made yet
        final ScheduleList emptyList = given()
                .when()
                .get("/requests")
                .then().statusCode(200).extract().body().as(ScheduleList.class);

        assertEquals(0, emptyList.requests.size());

        // Perform multiple schedule requests
        final GroupMetricRequest payload = RequestPayloadGenerator.correct();
        payload.setBatchSize(N_SAMPLES - 10);
        final BaseScheduledResponse firstRequest = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/request")
                .then().statusCode(200).extract().body().as(BaseScheduledResponse.class);

        assertNotNull(firstRequest.getRequestId());

        payload.setBatchSize(N_SAMPLES + 1000);
        final BaseScheduledResponse secondRequest = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/request")
                .then().statusCode(200).extract().body().as(BaseScheduledResponse.class);

        assertNotNull(secondRequest.getRequestId());

        payload.setBatchSize(0);
        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/request")
                .then().statusCode(400).body(containsString("Request batch size must be bigger than 0."));

        ScheduleList scheduleList = given()
                .when()
                .get("/requests")
                .then().statusCode(200).extract().body().as(ScheduleList.class);

        // Correct number of active requests
        assertEquals(2, scheduleList.requests.size());
    }

    /**
     * Deleting a request should work
     */
    @Test
    void deleteRequest() {
        final GroupMetricRequest payload = RequestPayloadGenerator.correct();
        final BaseScheduledResponse response = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/request")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .body().as(BaseScheduledResponse.class);

        final ScheduleId requestId = new ScheduleId();
        requestId.requestId = response.getRequestId();

        given()
                .contentType(ContentType.JSON)
                .body(requestId)
                .when().delete("/request")
                .then()
                .statusCode(Response.Status.OK.getStatusCode());
    }

    /**
     * Deleting a request when passing a null ID should return a sensible error message
     */
    @Test
    void deleteRequestNullID() {
        final GroupMetricRequest payload = RequestPayloadGenerator.correct();
        final BaseScheduledResponse response = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/request")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .body().as(BaseScheduledResponse.class);

        final ScheduleId requestId = new ScheduleId();
        requestId.requestId = null;

        given()
                .contentType(ContentType.JSON)
                .body(requestId)
                .when().delete("/request")
                .then()
                .statusCode(Response.Status.NOT_FOUND.getStatusCode());
    }

    /**
     * Deleting a request when passing an invalid UUID should return a sensible error message
     */
    @Test
    void deleteRequestInvalidID() {
        final GroupMetricRequest payload = RequestPayloadGenerator.correct();
        final BaseScheduledResponse response = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/request")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .body().as(BaseScheduledResponse.class);

        final ScheduleId requestId = new ScheduleId();
        requestId.requestId = null;

        given()
                .contentType(ContentType.JSON)
                .body("{\"id\": \"invalidUUID\"}")
                .when().delete("/request")
                .then()
                .statusCode(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    void postAdvancedCorrect() throws JsonProcessingException {
        final AdvancedGroupMetricRequest payload = RequestPayloadGenerator.advancedCorrect();
        final BaseScheduledResponse response = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/advanced/request")
                .then().statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .body().as(BaseScheduledResponse.class);

        // check compatibility with ODH UI
        given()
                .when()
                .get("/advanced/requests")
                .then().statusCode(200).extract().response().then()
                .body(containsString("privilegedAttribute\":{\"type\":\"null\",\"value\":\"DataRequestPayload"))
                .body(containsString("unprivilegedAttribute\":{\"type\":\"null\",\"value\":\"DataRequestPayload"))
                .body(containsString("favorableOutcome\":{\"type\":\"null\",\"value\":\"DataRequestPayload"))
                .body(containsString("\"protectedAttribute\":\"Defined by TrustyQL\""))
                .body(containsString("\"outcomeName\":\"Defined by TrustyQL\""));
    }

    @Test
    void postAdvancedNameMappingCorrect() throws JsonProcessingException {
        final AdvancedGroupMetricRequest payload = RequestPayloadGenerator.advancedNameMappedCorrect();

        HashMap<String, String> inputMapping = new HashMap<>();
        HashMap<String, String> outputMapping = new HashMap<>();
        inputMapping.put("age", "ageMapped");
        inputMapping.put("race", "raceMapped");
        inputMapping.put("gender", "genderMapped");
        outputMapping.put("income", "incomeMapped");
        NameMapping nameMapping = new NameMapping(MODEL_ID, inputMapping, outputMapping);

        given()
                .contentType(ContentType.JSON)
                .body(nameMapping)
                .basePath("/info")
                .when().post("/names")
                .then()
                .statusCode(200)
                .body(is("Feature and output name mapping successfully applied."));

        final BaseScheduledResponse response = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/advanced/request")
                .then().statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .body().as(BaseScheduledResponse.class);

        // check compatibility with ODH UI
        given()
                .when()
                .get("/advanced/requests")
                .then().statusCode(200).extract().response().then()
                .body(containsString("privilegedAttribute\":{\"type\":\"null\",\"value\":\"DataRequestPayload"))
                .body(containsString("unprivilegedAttribute\":{\"type\":\"null\",\"value\":\"DataRequestPayload"))
                .body(containsString("favorableOutcome\":{\"type\":\"null\",\"value\":\"DataRequestPayload"))
                .body(containsString("\"protectedAttribute\":\"Defined by TrustyQL\""))
                .body(containsString("\"outcomeName\":\"Defined by TrustyQL\""));
    }

    @Test
    void postAdvancedIncorrect() throws JsonProcessingException {

        final AdvancedGroupMetricRequest payload = RequestPayloadGenerator.advancedIncorrect();

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/advanced/request")
                .then().statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .extract().response().then()
                .body(containsString("No feature or output found with name=FIELD_DOES_NOT_EXIST."))
                .body(containsString("Invalid type for output=income: got 'WRONG_VALUE_TYPE', expected object compatible with 'INT32'"))
                .body(containsString("RowMatch operation must be one of [BETWEEN, EQUALS], got NO_SUCH_OPERATION"));
    }
}
