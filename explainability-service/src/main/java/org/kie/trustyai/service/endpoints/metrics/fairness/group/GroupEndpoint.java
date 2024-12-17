package org.kie.trustyai.service.endpoints.metrics.fairness.group;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.kie.trustyai.explainability.model.Value;
import org.kie.trustyai.explainability.model.dataframe.Dataframe;
import org.kie.trustyai.service.data.exceptions.DataframeCreateException;
import org.kie.trustyai.service.data.exceptions.MetricCalculationException;
import org.kie.trustyai.service.data.exceptions.StorageReadException;
import org.kie.trustyai.service.data.metadata.StorageMetadata;
import org.kie.trustyai.service.endpoints.metrics.BaseEndpoint;
import org.kie.trustyai.service.payloads.PayloadConverter;
import org.kie.trustyai.service.payloads.definitions.GroupDefinitionRequest;
import org.kie.trustyai.service.payloads.metrics.BaseMetricResponse;
import org.kie.trustyai.service.payloads.metrics.MetricThreshold;
import org.kie.trustyai.service.payloads.metrics.RequestReconciler;
import org.kie.trustyai.service.payloads.metrics.fairness.group.GroupMetricRequest;
import org.kie.trustyai.service.prometheus.MetricValueCarrier;
import org.kie.trustyai.service.validators.metrics.ValidReconciledMetricRequest;
import org.kie.trustyai.service.validators.metrics.fairness.group.ValidGroupMetricRequest;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

public abstract class GroupEndpoint extends BaseEndpoint<GroupMetricRequest> {
    // names need to be unique!
    protected GroupEndpoint(String name) {
        super(name);
    }

    public abstract MetricThreshold thresholdFunction(Number delta, MetricValueCarrier metricValue);

    public abstract String specificDefinitionFunction(String outcomeName, List<Value> favorableOutcomeAttr, String protectedAttribute, List<String> privileged, List<String> unprivileged,
            MetricValueCarrier metricvalue);

    public abstract String getGeneralDefinition();

    public String getSpecificDefinition(MetricValueCarrier metricValue, @ValidReconciledMetricRequest GroupMetricRequest request) {
        final String outcomeName = request.getOutcomeName();

        final List<Value> favorableOutcomeAttrs = PayloadConverter.convertToValues(request.getFavorableOutcome().getReconciledType().get());
        final String protectedAttribute = request.getProtectedAttribute();

        final List<String> privilegeds = request.getPrivilegedAttribute().getReconciledType().get().stream()
                .map(PayloadConverter::convertToValue)
                .map(Value::toString)
                .collect(Collectors.toList());
        final List<String> unprivilegeds = request.getUnprivilegedAttribute().getReconciledType().get().stream()
                .map(PayloadConverter::convertToValue)
                .map(Value::toString)
                .collect(Collectors.toList());
        return specificDefinitionFunction(outcomeName, favorableOutcomeAttrs, protectedAttribute, privilegeds, unprivilegeds, metricValue);
    }

    @POST
    @Operation(summary = "Compute the current value of this metric.")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response response(@ValidGroupMetricRequest GroupMetricRequest request) throws DataframeCreateException {

        final Dataframe dataframe;
        final StorageMetadata storageMetadata;
        try {
            if (Objects.isNull(request.getBatchSize())) {
                final int defaultBatchSize = serviceConfig.batchSize().getAsInt();
                LOG.warn("Request batch size is empty. Using the default value of " + defaultBatchSize);
                request.setBatchSize(defaultBatchSize);
            }

            dataframe = super.dataSource.get().getOrganicDataframe(request.getModelId(), request.getBatchSize());
            storageMetadata = dataSource.get().getMetadata(request.getModelId());

        } catch (DataframeCreateException | StorageReadException e) {
            LOG.error(e.getMessage());
            return Response.serverError().status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }

        RequestReconciler.reconcile(request, storageMetadata);

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
    @Operation(summary = "Provide a general definition of this metric.")
    @Produces(MediaType.TEXT_PLAIN)
    @Override
    public Response getDefinition() {
        return Response.ok(getGeneralDefinition()).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Provide a specific, plain-english interpretation of a specific value of this metric.")
    @Path("/definition")
    public Response getSpecificDefinition(GroupDefinitionRequest request) {
        try {
            RequestReconciler.reconcile(request, dataSource);
        } catch (DataframeCreateException | StorageReadException e) {
            LOG.error(e.getMessage());
            return Response.serverError().status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }

        return Response.ok(this.getSpecificDefinition(new MetricValueCarrier(request.getMetricValue()), request)).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Schedule a recurring computation of this metric.")
    @Path("/request")
    public Response createRequest(@ValidGroupMetricRequest GroupMetricRequest request) {
        return super.createRequestGeneric(request);
    }
}
