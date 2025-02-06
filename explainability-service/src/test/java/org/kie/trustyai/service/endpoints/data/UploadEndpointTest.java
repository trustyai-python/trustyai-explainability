package org.kie.trustyai.service.endpoints.data;

import java.util.List;

import org.jboss.resteasy.reactive.RestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.trustyai.explainability.model.dataframe.Dataframe;
import org.kie.trustyai.service.mocks.flatfile.MockCSVDatasource;
import org.kie.trustyai.service.mocks.flatfile.MockMemoryStorage;
import org.kie.trustyai.service.payloads.data.upload.ModelInferJointPayload;
import org.kie.trustyai.service.profiles.flatfile.MemoryTestProfile;
import org.kie.trustyai.service.utils.KserveRestPayloads;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(MemoryTestProfile.class)
@TestHTTPEndpoint(DownloadEndpoint.class)
class UploadEndpointTest {
    private static final String MODEL_ID = "example1";
    @Inject
    Instance<MockCSVDatasource> datasource;

    @Inject
    Instance<MockMemoryStorage> storage;

    /**
     * Empty the storage before each test.
     */
    @BeforeEach
    void emptyStorage() throws JsonProcessingException {
        datasource.get().reset();
        storage.get().emptyStorage();
    }

    // data upload tests ===============================================================================================
    @Test
    void uploadData() throws JsonProcessingException {
        int[] testInputRows = new int[] { 1, 5, 250 };
        int[] testInputCols = new int[] { 1, 4 };
        int[] testOutputCols = new int[] { 1, 2 };
        String[] testDatatypes = new String[] { "INT64", "INT32", "FP32", "FP64", "BOOL" };
        String dataTag = "TRAINING";

        for (int nInputRows : testInputRows) {
            for (int nInputCols : testInputCols) {
                for (int nOutputCols : testOutputCols) {
                    for (String datatype : testDatatypes) {
                        ModelInferJointPayload payload = KserveRestPayloads.generatePayload(nInputRows, nInputCols, nOutputCols, datatype, dataTag);
                        emptyStorage();

                        given()
                                .contentType(ContentType.JSON)
                                .body(payload)
                                .when().post("/upload")
                                .then()
                                .statusCode(RestResponse.StatusCode.OK)
                                .body(containsString(nInputRows + " datapoints"));

                        // check that tagging is correctly applied
                        Dataframe df = datasource.get().getDataframe(payload.getModelName());
                        Dataframe trainDF = df.filterRowsByTagEquals(dataTag);
                        Dataframe nonTrainDF = df.filterRowsByTagNotEquals(dataTag);

                        assertEquals(nInputRows, df.getRowDimension());
                        assertEquals(nInputRows, trainDF.getRowDimension());
                        assertEquals(0, nonTrainDF.getRowDimension());
                    }
                }
            }
        }
        emptyStorage();
    }

    @Test
    void uploadMultiInputData() throws JsonProcessingException {
        int[] testRows = new int[] { 1, 3, 5, 250 };
        int[] testInputCols = new int[] { 2, 6 };
        int[] testOutputCols = new int[] { 4 };
        String[] testDatatypes = new String[] { "INT64", "INT32", "FP32", "FP64", "BOOL" };
        String dataTag = "TRAINING";

        for (int nRows : testRows) {
            for (int nInputCols : testInputCols) {
                for (int nOutputCols : testOutputCols) {
                    for (String datatype : testDatatypes) {
                        ModelInferJointPayload payload = KserveRestPayloads.generateMultiInputPayload(nRows, nInputCols, nOutputCols, datatype, dataTag);

                        emptyStorage();

                        given()
                                .contentType(ContentType.JSON)
                                .body(payload)
                                .when().post("/upload")
                                .then()
                                .statusCode(RestResponse.StatusCode.OK)
                                .body(containsString(nRows + " datapoints"));

                        // check that tagging is correctly applied
                        Dataframe df = datasource.get().getDataframe(payload.getModelName());
                        Dataframe trainDF = df.filterRowsByTagEquals(dataTag);
                        Dataframe nonTrainDF = df.filterRowsByTagNotEquals(dataTag);

                        assertEquals(nRows, df.getRowDimension());
                        assertEquals(nInputCols + nOutputCols, df.getColumnDimension());
                        assertEquals(nInputCols, df.getInputNames().size());
                        assertEquals(nOutputCols, df.getOutputNames().size());
                        assertEquals(nRows, trainDF.getRowDimension());
                        assertEquals(0, nonTrainDF.getRowDimension());
                    }
                }
            }
        }
        emptyStorage();
    }

    @Test
    void uploadMultiInputDataNoUniqueName() throws JsonProcessingException {
        ModelInferJointPayload payload = KserveRestPayloads.generateMismatchedShapeNoUniqueNameMultiInputPayload(250, 4, 3, "FP64", "TRAINING");

        emptyStorage();

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/upload").peek()
                .then()
                .statusCode(RestResponse.StatusCode.BAD_REQUEST)
                .body(containsString("One or more errors"), containsString("unique names"), containsString("first dimension"));

    }

    @Test
    void uploadMultipleTagging() {
        int nPayload1 = 50;
        int nPayload2 = 51;
        ModelInferJointPayload payload1 = KserveRestPayloads.generatePayload(nPayload1, 10, 1, "INT64", "TRAINING");
        ModelInferJointPayload payload2 = KserveRestPayloads.generatePayload(nPayload2, 10, 1, "INT64", "NOT TRAINING");

        given()
                .contentType(ContentType.JSON)
                .body(payload1)
                .when().post("/upload")
                .then()
                .statusCode(RestResponse.StatusCode.OK)
                .body(containsString(nPayload1 + " datapoints"));

        given()
                .contentType(ContentType.JSON)
                .body(payload2)
                .when().post("/upload")
                .then()
                .statusCode(RestResponse.StatusCode.OK)
                .body(containsString(nPayload2 + " datapoints"));

        // check that tagging is correctly applied
        Dataframe df = datasource.get().getDataframe(payload1.getModelName());
        Dataframe trainDF = df.filterRowsByTagEquals("TRAINING");
        Dataframe synthDF = df.filterRowsByTagEquals("NOT TRAINING");

        assertEquals(nPayload1 + nPayload2, df.getRowDimension());
        assertEquals(nPayload1, trainDF.getRowDimension());
        assertEquals(nPayload2, synthDF.getRowDimension());
    }

    @Test
    void uploadTagThatUsesProtectedName() {
        ModelInferJointPayload payload1 = KserveRestPayloads.generatePayload(5, 10, 1, "INT64", Dataframe.TRUSTYAI_INTERNAL_TAG_PREFIX + "_something");

        given()
                .contentType(ContentType.JSON)
                .body(payload1)
                .when().post("/upload")
                .then()
                .statusCode(RestResponse.StatusCode.BAD_REQUEST)
                .body(is(String.format(
                        "The tag prefix '%s' is reserved for internal TrustyAI use only. Provided tag '%s_something' violates this restriction.",
                        Dataframe.TRUSTYAI_INTERNAL_TAG_PREFIX,
                        Dataframe.TRUSTYAI_INTERNAL_TAG_PREFIX)));
    }

    private void postTest(Object payload, int statusCode, List<String> checkMsgs) {
        ValidatableResponse r = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post("/upload")
                .then();

        for (String checkMsg : checkMsgs) {
            r.statusCode(statusCode).body(containsString(checkMsg));
        }
    }

    @Test
    void uploadDataAndGroundTruth() throws JsonProcessingException {
        int[] testInputRows = new int[] { 1, 5, 250 };
        int[] testInputCols = new int[] { 1, 4 };
        int[] testOutputCols = new int[] { 1, 2 };
        String[] testDatatypes = new String[] { "INT64", "INT32", "FP32", "FP64", "BOOL" };

        // sorry for the quad loop
        for (int nInputRows : testInputRows) {
            for (int nInputCols : testInputCols) {
                for (int nOutputCols : testOutputCols) {
                    for (String datatype : testDatatypes) {
                        ModelInferJointPayload payload = KserveRestPayloads.generatePayload(nInputRows, nInputCols, nOutputCols, datatype, "TRAINING");
                        payload.setGroundTruth(false);
                        emptyStorage();

                        postTest(payload, RestResponse.StatusCode.OK, List.of(nInputRows + " datapoints"));

                        Dataframe originalDF = datasource.get().getDataframe(payload.getModelName());
                        List<String> ids = originalDF.getIds();

                        ModelInferJointPayload payloadGroundTruth = KserveRestPayloads.generatePayload(nInputRows, nInputCols, nOutputCols, datatype, "TRAINING", 0, 1);
                        payloadGroundTruth.setRequest(payload.getRequest());
                        payloadGroundTruth.getRequest().getTensorPayloads()[0].setExecutionIDs(ids.toArray(String[]::new));
                        payloadGroundTruth.setGroundTruth(true);

                        postTest(payloadGroundTruth, RestResponse.StatusCode.OK, List.of(nInputRows + " ground truths"));

                        Dataframe gtDF = datasource.get().getGroundTruths(payload.getModelName());
                        assertEquals(originalDF.getRowDimension(), gtDF.getRowDimension());
                        assertEquals(originalDF.getOutputNames().size(), gtDF.getColumnDimension());

                        // check that ground truths are correctly correlated
                        assertEquals(originalDF.getIds(), gtDF.getIds());
                    }
                }
            }
        }
    }

    @Test
    void uploadMismatchInputValues() {
        int nInputRows = 5;
        ModelInferJointPayload payload0 = KserveRestPayloads.generatePayload(nInputRows, 10, 1, "INT64", "TRAINING");
        ModelInferJointPayload payload1 = KserveRestPayloads.generatePayload(nInputRows, 10, 1, "INT64", "TRAINING", 1, 0);

        postTest(payload0, RestResponse.StatusCode.OK, List.of(nInputRows + " datapoints"));

        Dataframe originalDF = datasource.get().getDataframe(payload0.getModelName());
        List<String> ids = originalDF.getIds();

        payload1.setRequest(payload1.getRequest());
        payload1.getRequest().getTensorPayloads()[0].setExecutionIDs(ids.toArray(String[]::new));
        payload1.setGroundTruth(true);
        postTest(payload1, RestResponse.StatusCode.BAD_REQUEST, List.of(
                "Found fatal mismatches between uploaded data and recorded inference data:",
                "inputs are not identical",
                "Value=0 !=  Value=1",
                "Value=8 !=  Value=9"));
    }

    @Test
    void uploadMismatchInputLengths() {
        int nInputRows = 5;
        ModelInferJointPayload payload0 = KserveRestPayloads.generatePayload(nInputRows, 10, 1, "INT64", "TRAINING");
        ModelInferJointPayload payload1 = KserveRestPayloads.generatePayload(nInputRows, 10 + 1, 1, "INT64", "TRAINING");

        postTest(payload0, RestResponse.StatusCode.OK, List.of(nInputRows + " datapoints"));

        Dataframe originalDF = datasource.get().getDataframe(payload0.getModelName());
        List<String> ids = originalDF.getIds();

        payload1.setRequest(payload1.getRequest());
        payload1.getRequest().getTensorPayloads()[0].setExecutionIDs(ids.toArray(String[]::new));
        payload1.setGroundTruth(true);
        postTest(payload1, RestResponse.StatusCode.BAD_REQUEST, List.of(
                "Found fatal mismatches between uploaded data and recorded inference data:",
                "input shapes do not match. Observed inputs have length=10 while uploaded inputs have length=11"));
    }

    @Test
    void uploadMismatchInputAndOutputTypes() {
        int nInputRows = 5;
        ModelInferJointPayload payload0 = KserveRestPayloads.generatePayload(nInputRows, 10, 2, "INT64", "TRAINING");
        ModelInferJointPayload payload1 = KserveRestPayloads.generatePayload(nInputRows, 10, 2, "FP32", "TRAINING", 0, 1);

        postTest(payload0, RestResponse.StatusCode.OK, List.of(nInputRows + " datapoints"));

        Dataframe originalDF = datasource.get().getDataframe(payload0.getModelName());
        List<String> ids = originalDF.getIds();

        payload1.setRequest(payload1.getRequest());
        payload1.getRequest().getTensorPayloads()[0].setExecutionIDs(ids.toArray(String[]::new));
        payload1.setGroundTruth(true);
        postTest(payload1, RestResponse.StatusCode.BAD_REQUEST, List.of(
                "Found fatal mismatches between uploaded data and recorded inference data:",
                "1 | Class=Long != Class=Float",
                "inputs are not identical",
                "Class=Long, Value=9 != Class=Float, Value=9.0"));
    }
}
