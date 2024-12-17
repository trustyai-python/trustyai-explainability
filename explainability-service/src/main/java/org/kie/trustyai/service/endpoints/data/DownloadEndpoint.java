package org.kie.trustyai.service.endpoints.data;

import org.jboss.logging.Logger;
import org.kie.trustyai.explainability.model.dataframe.Dataframe;
import org.kie.trustyai.service.data.datasources.DataSource;
import org.kie.trustyai.service.data.metadata.StorageMetadata;
import org.kie.trustyai.service.data.parsers.CSVParser;
import org.kie.trustyai.service.data.utils.DownloadUtils;
import org.kie.trustyai.service.payloads.data.download.DataRequestPayload;
import org.kie.trustyai.service.payloads.data.download.DataResponsePayload;
import org.kie.trustyai.service.payloads.data.download.MatchOperation;
import org.kie.trustyai.service.payloads.data.download.RowMatcher;
import org.kie.trustyai.service.payloads.values.DataType;
import org.kie.trustyai.service.validators.data.ValidDataDownloadRequest;

import io.quarkus.resteasy.reactive.server.EndpointDisabled;

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
@EndpointDisabled(name = "endpoints.data.download", stringValue = "disable")
@Path("/data")
public class DownloadEndpoint {

    private static final Logger LOG = Logger.getLogger(DownloadEndpoint.class);
    @Inject
    Instance<DataSource> dataSource;

    CSVParser csvParser = new CSVParser();

    public final static String TRUSTY_PREFIX = "trustyai.";

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/download")
    public Response download(@ValidDataDownloadRequest DataRequestPayload dataRequestPayload) {
        String modelId = dataRequestPayload.getModelId();
        Dataframe df = dataSource.get().getDataframe(modelId).copy();
        StorageMetadata metadata = dataSource.get().getMetadata(modelId);

        for (RowMatcher rowMatcher : dataRequestPayload.getMatchAll()) {
            if (rowMatcher.getColumnName().startsWith(DownloadEndpoint.TRUSTY_PREFIX)) {
                Dataframe.InternalColumn internalColumn = Dataframe.InternalColumn.valueOf(rowMatcher.getColumnName().replace(TRUSTY_PREFIX, ""));
                if (MatchOperation.valueOf(rowMatcher.getOperation()) == MatchOperation.BETWEEN) {
                    df = DownloadUtils.betweenMatcherInternal(df, rowMatcher, internalColumn, false);
                } else if (MatchOperation.valueOf(rowMatcher.getOperation()) == MatchOperation.EQUALS) {
                    df = DownloadUtils.equalsMatcherInternal(df, rowMatcher, internalColumn, false);
                }
            } else {
                int columnIndex = df.getColumnNames().indexOf(rowMatcher.getColumnName());
                DataType columnType = DownloadUtils.getDataType(metadata, rowMatcher);

                // row match
                if (MatchOperation.valueOf(rowMatcher.getOperation()) == MatchOperation.BETWEEN) {
                    df = DownloadUtils.betweenMatcher(df, rowMatcher, columnIndex, columnType, false);
                } else if (MatchOperation.valueOf(rowMatcher.getOperation()) == MatchOperation.EQUALS) {
                    df = DownloadUtils.equalsMatcher(df, rowMatcher, columnIndex, columnType, false);
                }
            }
        }

        for (RowMatcher rowMatcher : dataRequestPayload.getMatchNone()) {
            if (rowMatcher.getColumnName().startsWith(DownloadEndpoint.TRUSTY_PREFIX)) {
                Dataframe.InternalColumn internalColumn = Dataframe.InternalColumn.valueOf(rowMatcher.getColumnName().replace(TRUSTY_PREFIX, ""));
                if (MatchOperation.valueOf(rowMatcher.getOperation()) == MatchOperation.BETWEEN) {
                    df = DownloadUtils.betweenMatcherInternal(df, rowMatcher, internalColumn, true);
                } else if (MatchOperation.valueOf(rowMatcher.getOperation()) == MatchOperation.EQUALS) {
                    df = DownloadUtils.equalsMatcherInternal(df, rowMatcher, internalColumn, true);
                }
            } else {
                int columnIndex = df.getColumnNames().indexOf(rowMatcher.getColumnName());
                DataType columnType = DownloadUtils.getDataType(metadata, rowMatcher);

                // row match
                if (MatchOperation.valueOf(rowMatcher.getOperation()) == MatchOperation.BETWEEN) {
                    df = DownloadUtils.betweenMatcher(df, rowMatcher, columnIndex, columnType, true);
                } else if (MatchOperation.valueOf(rowMatcher.getOperation()) == MatchOperation.EQUALS) {
                    df = DownloadUtils.equalsMatcher(df, rowMatcher, columnIndex, columnType, true);
                }
            }
        }

        Dataframe returnDF;
        if (!dataRequestPayload.getMatchAny().isEmpty()) {
            returnDF = df.filterByColumnValue(0, v -> false); //get null df
            for (RowMatcher rowMatcher : dataRequestPayload.getMatchAny()) {
                if (rowMatcher.getColumnName().startsWith(DownloadEndpoint.TRUSTY_PREFIX)) {
                    Dataframe.InternalColumn internalColumn = Dataframe.InternalColumn.valueOf(rowMatcher.getColumnName().replace(TRUSTY_PREFIX, ""));
                    if (MatchOperation.valueOf(rowMatcher.getOperation()) == MatchOperation.BETWEEN) {
                        returnDF.addPredictions(DownloadUtils.betweenMatcherInternal(df, rowMatcher, internalColumn, false).asPredictions());
                    } else if (MatchOperation.valueOf(rowMatcher.getOperation()) == MatchOperation.EQUALS) {
                        returnDF.addPredictions(DownloadUtils.equalsMatcherInternal(df, rowMatcher, internalColumn, false).asPredictions());
                    }
                } else {
                    int columnIndex = df.getColumnNames().indexOf(rowMatcher.getColumnName());
                    DataType columnType = DownloadUtils.getDataType(metadata, rowMatcher);

                    // row match
                    if (MatchOperation.valueOf(rowMatcher.getOperation()) == MatchOperation.BETWEEN) {
                        returnDF.addPredictions(DownloadUtils.betweenMatcher(df, rowMatcher, columnIndex, columnType, false).asPredictions());
                    } else if (MatchOperation.valueOf(rowMatcher.getOperation()) == MatchOperation.EQUALS) {
                        returnDF.addPredictions(DownloadUtils.equalsMatcher(df, rowMatcher, columnIndex, columnType, false).asPredictions());
                    }
                }
            }
        } else {
            returnDF = df;
        }

        DataResponsePayload dataResponsePayload = new DataResponsePayload();
        dataResponsePayload.setDataCSV(csvParser.convertToString(returnDF, true, true));

        return Response.ok().entity(dataResponsePayload).build();

    }
}