package org.kie.trustyai.service.endpoints.metrics.fairness.group.advanced;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.kie.trustyai.explainability.model.dataframe.Dataframe;
import org.kie.trustyai.metrics.fairness.FairnessDefinitions;
import org.kie.trustyai.metrics.fairness.group.GroupStatisticalParityDifference;
import org.kie.trustyai.service.data.cache.MetricCalculationCacheKeyGen;
import org.kie.trustyai.service.data.exceptions.MetricCalculationException;
import org.kie.trustyai.service.data.metadata.StorageMetadata;
import org.kie.trustyai.service.data.utils.DownloadUtils;
import org.kie.trustyai.service.payloads.metrics.BaseMetricRequest;
import org.kie.trustyai.service.payloads.metrics.MetricThreshold;
import org.kie.trustyai.service.payloads.metrics.fairness.group.AdvancedGroupMetricRequest;
import org.kie.trustyai.service.prometheus.MetricValueCarrier;
import org.kie.trustyai.service.validators.metrics.ValidReconciledMetricRequest;
import org.kie.trustyai.service.validators.metrics.fairness.group.ValidAdvancedGroupMetricRequest;

import io.quarkus.cache.CacheResult;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Path;

@ApplicationScoped
@Tag(name = "Statistical Parity Difference Endpoint", description = "Statistical Parity Difference (SPD) measures imbalances in classifications by calculating the " +
        "difference between the proportion of the majority and protected classes getting a particular outcome.")
@Path("/metrics/group/fairness/spd/advanced")
public class AdvancedGroupStatisticalParityDifferenceEndpoint extends AdvancedGroupEndpoint {
    @Override
    public MetricThreshold thresholdFunction(Number delta, MetricValueCarrier metricValue) {
        if (delta == null) {
            return new MetricThreshold(
                    metricsConfig.spd().thresholdLower(),
                    metricsConfig.spd().thresholdUpper(),
                    metricValue.getValue());
        } else {
            return new MetricThreshold(
                    0 - delta.doubleValue(),
                    delta.doubleValue(),
                    metricValue.getValue());
        }
    }

    @Override
    public String specificDefinitionFunction(String privilegedSelector, String unprivilegedSelector, String favorableOutcomeSelector, MetricValueCarrier metricValue) {
        return FairnessDefinitions.defineGroupStatisticalParityDifference(
                privilegedSelector, unprivilegedSelector, favorableOutcomeSelector,
                metricValue.getValue());
    };

    @Override
    @CacheResult(cacheName = "metrics-calculator-spd", keyGenerator = MetricCalculationCacheKeyGen.class)
    public MetricValueCarrier calculate(Dataframe dataframe, @ValidReconciledMetricRequest BaseMetricRequest request) {
        LOG.debug("Cache miss. Calculating metric for " + request.getModelId());

        @ValidAdvancedGroupMetricRequest
        AdvancedGroupMetricRequest gmRequest = (AdvancedGroupMetricRequest) request;
        StorageMetadata metadata = dataSource.get().getMetadata(request.getModelId());
        try {

            final Dataframe privileged = DownloadUtils.applyMatches(dataframe, metadata, gmRequest.getPrivilegedAttribute());
            final Dataframe privilegedPositive = DownloadUtils.applyMatches(privileged, metadata, gmRequest.getFavorableOutcome());
            final Dataframe unprivileged = DownloadUtils.applyMatches(dataframe, metadata, gmRequest.getUnprivilegedAttribute());
            final Dataframe unprivilegedPositive = DownloadUtils.applyMatches(unprivileged, metadata, gmRequest.getFavorableOutcome());

            return new MetricValueCarrier(
                    GroupStatisticalParityDifference.calculate(privileged, privilegedPositive, unprivileged, unprivilegedPositive));
        } catch (Exception e) {
            throw new MetricCalculationException(e.getMessage(), e);
        }
    }

    @Override
    public String getGeneralDefinition() {
        return FairnessDefinitions.defineGroupStatisticalParityDifference();
    }

    public AdvancedGroupStatisticalParityDifferenceEndpoint() {
        super("SPD_ADVANCED");
    }

}
