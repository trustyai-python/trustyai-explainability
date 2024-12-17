package org.kie.trustyai.service.endpoints.metrics.fairness.group.advanced;

import java.util.Objects;

import org.kie.trustyai.explainability.model.dataframe.Dataframe;
import org.kie.trustyai.service.data.exceptions.DataframeCreateException;
import org.kie.trustyai.service.data.exceptions.MetricCalculationException;
import org.kie.trustyai.service.endpoints.metrics.BaseEndpoint;
import org.kie.trustyai.service.payloads.metrics.BaseMetricResponse;
import org.kie.trustyai.service.payloads.metrics.MetricThreshold;
import org.kie.trustyai.service.payloads.metrics.fairness.group.AdvancedGroupMetricRequest;
import org.kie.trustyai.service.prometheus.MetricValueCarrier;
import org.kie.trustyai.service.validators.metrics.fairness.group.ValidAdvancedGroupMetricRequest;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

public abstract class AdvancedGroupEndpoint extends BaseEndpoint<AdvancedGroupMetricRequest> {
    protected AdvancedGroupEndpoint(String name) {
        super(name);
    }

    public abstract MetricThreshold thresholdFunction(Number delta, MetricValueCarrier metricValue);

    public abstract String specificDefinitionFunction(String privilegedSelector, String unprivilegedSelector, String favorableOutcomeSelector, MetricValueCarrier metricvalue);

    public abstract String getGeneralDefinition();

    public String getSpecificDefinition(MetricValueCarrier metricValue, @ValidAdvancedGroupMetricRequest AdvancedGroupMetricRequest request) {
        return specificDefinitionFunction(
                request.getPrivilegedAttribute().prettyPrint(),
                request.getUnprivilegedAttribute().prettyPrint(),
                request.getFavorableOutcome().prettyPrint(),
                metricValue);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response response(@ValidAdvancedGroupMetricRequest AdvancedGroupMetricRequest request) throws DataframeCreateException {

        final Dataframe dataframe;
        try {
            if (Objects.isNull(request.getBatchSize())) {
                final int defaultBatchSize = serviceConfig.batchSize().getAsInt();
                LOG.warn("Request batch size is empty. Using the default value of " + defaultBatchSize);
                request.setBatchSize(defaultBatchSize);
            }
            dataframe = super.dataSource.get().getOrganicDataframe(request.getModelId(), request.getBatchSize());
        } catch (DataframeCreateException e) {
            LOG.error("No data available for model " + request.getModelId() + ": " + e.getMessage(), e);
            return Response.serverError().status(Response.Status.INTERNAL_SERVER_ERROR).entity("No data available").build();
        }

        final MetricValueCarrier metricValue;
        try {
            metricValue = this.calculate(dataframe, request);
        } catch (MetricCalculationException e) {
            LOG.error("Error calculating metric for model " + request.getModelId() + ": " + e.getMessage(), e);
            return Response.serverError().status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error calculating metric: " + e.getMessage()).build();
        }
        if (metricValue.isSingle()) {
            final String metricDefinition = this.getSpecificDefinition(metricValue, request);

            MetricThreshold thresholds = thresholdFunction(request.getThresholdDelta(), metricValue);
            final BaseMetricResponse dirObj = new BaseMetricResponse(metricValue, metricDefinition, thresholds, super.getMetricName());
            return Response.ok(dirObj).build();
        } else {
            throw new UnsupportedOperationException("Group metric endpoint not yet compatible with multiple-valued metrics");
        }
    }

    @GET
    @Path("/definition")
    @Produces(MediaType.TEXT_PLAIN)
    @Override
    public Response getDefinition() {
        return Response.ok(getGeneralDefinition()).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/request")
    public Response createRequest(@ValidAdvancedGroupMetricRequest AdvancedGroupMetricRequest request) {
        return super.createRequestGeneric(request);
    }
}
