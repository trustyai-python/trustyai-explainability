package org.kie.trustyai.service.endpoints.data;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.jboss.resteasy.reactive.RestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.trustyai.explainability.model.Prediction;
import org.kie.trustyai.explainability.model.dataframe.Dataframe;
import org.kie.trustyai.service.data.parsers.CSVParser;
import org.kie.trustyai.service.data.utils.CSVUtils;
import org.kie.trustyai.service.mocks.flatfile.MockCSVDatasource;
import org.kie.trustyai.service.mocks.flatfile.MockMemoryStorage;
import org.kie.trustyai.service.payloads.data.download.DataRequestPayload;
import org.kie.trustyai.service.payloads.data.download.DataResponsePayload;
import org.kie.trustyai.service.payloads.data.download.RowMatcher;
import org.kie.trustyai.service.profiles.flatfile.MemoryTestProfile;
import org.kie.trustyai.service.utils.DataframeGenerators;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(MemoryTestProfile.class)
@TestHTTPEndpoint(DownloadEndpoint.class)
class DownloadEndpointTest {
    private static final String MODEL_ID = "example1";
    @Inject
    Instance<MockCSVDatasource> datasource;
    @Inject
    CSVParser csvParser;
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

    // data download tests

    @Test
    void downloadData() throws IOException {
        final Dataframe dataframe = DataframeGenerators.generateRandomDataframe(1000);
        datasource.get().saveDataframe(dataframe, MODEL_ID);
        datasource.get().saveMetadata(datasource.get().createMetadata(dataframe), MODEL_ID);

        DataRequestPayload dataRequestPayload = new DataRequestPayload();
        dataRequestPayload.setModelId(MODEL_ID);

        List<RowMatcher> matchAllList = new ArrayList<>();
        matchAllList.add(new RowMatcher("gender", "EQUALS", List.of(new IntNode(0))));
        matchAllList.add(new RowMatcher("race", "EQUALS", List.of(new IntNode(0))));
        matchAllList.add(new RowMatcher("income", "EQUALS", List.of(new IntNode(0))));
        dataRequestPayload.setMatchAll(matchAllList);

        List<RowMatcher> matchAnyList = new ArrayList<>();
        matchAnyList.add(new RowMatcher("age", "BETWEEN", List.of(new IntNode(5), new IntNode(10))));
        matchAnyList.add(new RowMatcher("age", "BETWEEN", List.of(new IntNode(50), new IntNode(70))));
        dataRequestPayload.setMatchAny(matchAnyList);

        List<RowMatcher> matchNoneList = new ArrayList<>();
        matchNoneList.add(new RowMatcher("age", "BETWEEN", List.of(new IntNode(55), new IntNode(65))));
        dataRequestPayload.setMatchNone(matchNoneList);

        DataResponsePayload response = given()
                .contentType(ContentType.JSON)
                .body(dataRequestPayload)
                .when().post("/download")
                .then().extract().body().as(DataResponsePayload.class);

        Dataframe df = Dataframe.createFrom(CSVUtils.parse(response.getDataCSV(), datasource.get().getMetadata(MODEL_ID), true));

        int ageIndex = df.getColumnNames().indexOf("age");
        int genderIndex = df.getColumnNames().indexOf("gender");
        int raceIndex = df.getColumnNames().indexOf("race");

        // check noneMatch
        assertEquals(0, df.filterByColumnValue(ageIndex, v -> (int) v.getUnderlyingObject() > 55 && (int) v.getUnderlyingObject() < 65).getRowDimension());

        // check allMatch
        assertEquals(0, df.filterByColumnValue(genderIndex, v -> (int) v.getUnderlyingObject() == 1).getRowDimension());
        assertEquals(0, df.filterByColumnValue(raceIndex, v -> (int) v.getUnderlyingObject() == 1).getRowDimension());

        // check anyMatch
        assertEquals(0, df.filterByColumnValue(ageIndex, v -> (int) v.getUnderlyingObject() >= 10 && (int) v.getUnderlyingObject() < 50).getRowDimension());
        assertEquals(0, df.filterByColumnValue(ageIndex, v -> (int) v.getUnderlyingObject() > 70).getRowDimension());
    }

    @Test
    void downloadTextData() throws IOException {
        final Dataframe dataframe = DataframeGenerators.generateRandomTextDataframe(1000);
        datasource.get().saveDataframe(dataframe, MODEL_ID);
        datasource.get().saveMetadata(datasource.get().createMetadata(dataframe), MODEL_ID);

        DataRequestPayload dataRequestPayload = new DataRequestPayload();
        dataRequestPayload.setModelId(MODEL_ID);

        List<RowMatcher> matchAllList = new ArrayList<>();
        matchAllList.add(new RowMatcher("make", "EQUALS", List.of(new TextNode("Chevy"), new TextNode("Ford"), new TextNode("Dodge"))));
        matchAllList.add(new RowMatcher("year", "BETWEEN", List.of(new IntNode(1990), new IntNode(2050))));
        dataRequestPayload.setMatchAll(matchAllList);

        DataResponsePayload response = given()
                .contentType(ContentType.JSON)
                .body(dataRequestPayload)
                .when().post("/download")
                .then().extract().body().as(DataResponsePayload.class);

        List<Prediction> predictions = CSVUtils.parse(response.getDataCSV(), datasource.get().getMetadata(MODEL_ID), true, true);
        Dataframe df = Dataframe.createFrom(predictions);

        int yearIndex = df.getColumnNames().indexOf("year");
        int makeIndex = df.getColumnNames().indexOf("make");
        int colorIndex = df.getColumnNames().indexOf("color");

        assertEquals(0, df.filterByColumnValue(yearIndex, value -> (int) value.getUnderlyingObject() < 1990).getRowDimension());
        assertEquals(0, df.filterByColumnValue(makeIndex, value -> value.getUnderlyingObject().equals("GMC")).getRowDimension());
        assertEquals(0, df.filterByColumnValue(makeIndex, value -> value.getUnderlyingObject().equals("Buick")).getRowDimension());
    }

    @Test
    void downloadTextDataBetweenError() throws IOException {
        final Dataframe dataframe = DataframeGenerators.generateRandomTextDataframe(1000);
        datasource.get().saveDataframe(dataframe, MODEL_ID);
        datasource.get().saveMetadata(datasource.get().createMetadata(dataframe), MODEL_ID);

        DataRequestPayload dataRequestPayload = new DataRequestPayload();
        dataRequestPayload.setModelId(MODEL_ID);

        List<RowMatcher> matchAllList = new ArrayList<>();
        matchAllList.add(new RowMatcher("make", "BETWEEN", List.of(new TextNode("Chevy"), new TextNode("Ford"), new TextNode("Dodge"))));
        dataRequestPayload.setMatchAll(matchAllList);

        given()
                .contentType(ContentType.JSON)
                .body(dataRequestPayload)
                .when().post("/download")
                .then().statusCode(RestResponse.StatusCode.BAD_REQUEST)
                .body(containsString("BETWEEN operation must contain exactly two values, describing the lower and upper bounds of the desired range. Received 3 values"),
                        containsString("BETWEEN operation must only contain numbers, describing the lower and upper bounds of the desired range. Received non-numeric values"));
    }

    @Test
    void downloadTextDataInvalidColumnError() throws IOException {
        final Dataframe dataframe = DataframeGenerators.generateRandomTextDataframe(1000);
        datasource.get().saveDataframe(dataframe, MODEL_ID);
        datasource.get().saveMetadata(datasource.get().createMetadata(dataframe), MODEL_ID);

        DataRequestPayload dataRequestPayload = new DataRequestPayload();
        dataRequestPayload.setModelId(MODEL_ID);

        List<RowMatcher> matchAllList = new ArrayList<>();
        matchAllList.add(new RowMatcher("mak123e", "EQUALS", List.of(new TextNode("Chevy"), new TextNode("Ford"))));
        dataRequestPayload.setMatchAll(matchAllList);

        given()
                .contentType(ContentType.JSON)
                .body(dataRequestPayload)
                .when().post("/download")
                .then().statusCode(RestResponse.StatusCode.BAD_REQUEST)
                .body(containsString("No feature or output found with name="));
    }

    @Test
    void downloadTextDataInvalidOperationError() throws IOException {
        final Dataframe dataframe = DataframeGenerators.generateRandomTextDataframe(1000);
        datasource.get().saveDataframe(dataframe, MODEL_ID);
        datasource.get().saveMetadata(datasource.get().createMetadata(dataframe), MODEL_ID);

        DataRequestPayload dataRequestPayload = new DataRequestPayload();
        dataRequestPayload.setModelId(MODEL_ID);

        List<RowMatcher> matchAllList = new ArrayList<>();
        matchAllList.add(new RowMatcher("mak123e", "DOESNOTEXIST", List.of(new TextNode("Chevy"), new TextNode("Ford"))));
        dataRequestPayload.setMatchAll(matchAllList);

        given()
                .contentType(ContentType.JSON)
                .body(dataRequestPayload)
                .when().post("/download")
                .then().statusCode(RestResponse.StatusCode.BAD_REQUEST)
                .body(containsString("RowMatch operation must be one of [BETWEEN, EQUALS]"));
    }

    @Test
    void downloadTextDataInternalColumn() throws IOException {
        final Dataframe dataframe = DataframeGenerators.generateRandomTextDataframe(1000);

        HashMap<String, List<List<Integer>>> tags = new HashMap<>();
        tags.put("TRAINING", List.of(List.of(0, 500)));
        dataframe.tagDataPoints(tags);

        datasource.get().saveDataframe(dataframe, MODEL_ID);
        datasource.get().saveMetadata(datasource.get().createMetadata(dataframe), MODEL_ID);

        DataRequestPayload dataRequestPayload = new DataRequestPayload();
        dataRequestPayload.setModelId(MODEL_ID);

        List<RowMatcher> matchAllList = new ArrayList<>();
        matchAllList.add(new RowMatcher("trustyai.TAG", "EQUALS", List.of(new TextNode("TRAINING"))));
        dataRequestPayload.setMatchAll(matchAllList);

        DataResponsePayload response = given()
                .contentType(ContentType.JSON)
                .body(dataRequestPayload)
                .when().post("/download")
                .then().extract().body().as(DataResponsePayload.class);
        Dataframe df = Dataframe.createFrom(CSVUtils.parse(response.getDataCSV(), datasource.get().getMetadata(MODEL_ID), true));

        assertEquals(500, df.getRowDimension());
    }

    @Test
    void downloadTextDataInternalColumnIndex() throws IOException {
        final Dataframe dataframe = DataframeGenerators.generateRandomTextDataframe(1000);
        datasource.get().saveDataframe(dataframe, MODEL_ID);
        datasource.get().saveMetadata(datasource.get().createMetadata(dataframe), MODEL_ID);
        List<Prediction> predsToExtract = datasource.get().getDataframe(MODEL_ID).asPredictions().subList(0, 10);

        DataRequestPayload dataRequestPayload = new DataRequestPayload();
        dataRequestPayload.setModelId(MODEL_ID);

        List<RowMatcher> matchAllList = new ArrayList<>();
        matchAllList.add(new RowMatcher("trustyai.INDEX", "BETWEEN", List.of(new IntNode(0), new IntNode(10))));
        dataRequestPayload.setMatchAll(matchAllList);

        DataResponsePayload response = given()
                .contentType(ContentType.JSON)
                .body(dataRequestPayload)
                .when().post("/download")
                .then().extract().body().as(DataResponsePayload.class);
        Dataframe df = Dataframe.createFrom(CSVUtils.parse(response.getDataCSV(), datasource.get().getMetadata(MODEL_ID), true));

        assertEquals(10, df.getRowDimension());

        List<Prediction> returnedPredictions = df.asPredictions();
        for (int i = 0; i < 10; i++) {
            assertEquals(predsToExtract.get(i).getInput(), returnedPredictions.get(i).getInput());
        }
    }

    @Test
    void downloadTextDataInternalColumnTimestamp() throws IOException, InterruptedException {
        Dataframe dataframe = DataframeGenerators.generateRandomTextDataframe(1, -1);

        // enforce different timestamps per prediction
        for (int i = 0; i < 100; i++) {
            Thread.sleep(1);
            dataframe.addPredictions(DataframeGenerators.generateRandomTextDataframe(1, i).asPredictions());
        }

        datasource.get().saveDataframe(dataframe, MODEL_ID);
        datasource.get().saveMetadata(datasource.get().createMetadata(dataframe), MODEL_ID);

        // grab the timestamps to split over
        int extractIdx = 50;
        int nToGet = 10;
        List<Prediction> predsToExtract = datasource.get().getDataframe(MODEL_ID).asPredictions().subList(extractIdx, extractIdx + nToGet);
        List<LocalDateTime> timesToExtract = dataframe.getTimestamps().subList(extractIdx, extractIdx + nToGet + 1);

        DataRequestPayload dataRequestPayload = new DataRequestPayload();
        dataRequestPayload.setModelId(MODEL_ID);

        List<RowMatcher> matchAnyList = new ArrayList<>();
        matchAnyList.add(new RowMatcher("trustyai.TIMESTAMP", "BETWEEN",
                List.of(
                        new TextNode(timesToExtract.get(0).toString()),
                        new TextNode(timesToExtract.get(nToGet).toString()))));
        dataRequestPayload.setMatchAny(matchAnyList);

        DataResponsePayload response = given()
                .contentType(ContentType.JSON)
                .body(dataRequestPayload)
                .when().post("/download")
                .then().statusCode(RestResponse.StatusCode.OK)
                .extract().body().as(DataResponsePayload.class);
        Dataframe df = Dataframe.createFrom(CSVUtils.parse(response.getDataCSV(), datasource.get().getMetadata(MODEL_ID), true));

        assertEquals(10, df.getRowDimension()); //should return *at least 10* rows, in case of overlapping timestamps
        List<Prediction> returnedPredictions = df.asPredictions();
        for (int i = 0; i < 10; i++) {
            assertEquals(predsToExtract.get(i).getInput(), returnedPredictions.get(i).getInput());
        }
    }

    @Test
    void downloadTextDataInternalColumnTimestampUnparseable() throws IOException {
        final Dataframe dataframe = DataframeGenerators.generateRandomTextDataframe(1000);
        datasource.get().saveDataframe(dataframe, MODEL_ID);
        datasource.get().saveMetadata(datasource.get().createMetadata(dataframe), MODEL_ID);

        int extractIdx = 500;
        int nToGet = 10;
        List<Prediction> predsToExtract = datasource.get().getDataframe(MODEL_ID).asPredictions().subList(extractIdx, extractIdx + nToGet);
        List<LocalDateTime> timesToExtract = dataframe.getTimestamps().subList(extractIdx, extractIdx + nToGet + 1);

        DataRequestPayload dataRequestPayload = new DataRequestPayload();
        dataRequestPayload.setModelId(MODEL_ID);

        List<RowMatcher> matchAnyList = new ArrayList<>();
        matchAnyList.add(new RowMatcher("trustyai.TIMESTAMP", "BETWEEN",
                List.of(
                        new TextNode("not a timestamp"),
                        new TextNode("also not a timestamp"))));
        dataRequestPayload.setMatchAny(matchAnyList);

        given()
                .contentType(ContentType.JSON)
                .body(dataRequestPayload)
                .when().post("/download")
                .then().statusCode(RestResponse.StatusCode.BAD_REQUEST)
                .body(containsString("is unparseable as an ISO_LOCAL_DATE_TIME"));
    }

    @Test
    void downloadTextDataNullRequest() throws IOException {
        final Dataframe dataframe = DataframeGenerators.generateRandomTextDataframe(1000);
        datasource.get().saveDataframe(dataframe, MODEL_ID);
        datasource.get().saveMetadata(datasource.get().createMetadata(dataframe), MODEL_ID);

        DataRequestPayload dataRequestPayload = new DataRequestPayload();
        dataRequestPayload.setModelId(MODEL_ID);

        DataResponsePayload response = given()
                .contentType(ContentType.JSON)
                .body(dataRequestPayload)
                .when().post("/download")
                .then().extract().body().as(DataResponsePayload.class);
        Dataframe df = Dataframe.createFrom(CSVUtils.parse(response.getDataCSV(), datasource.get().getMetadata(MODEL_ID), true));

        assertEquals(1000, df.getRowDimension());
    }
}