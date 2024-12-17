package org.kie.trustyai.service.payloads.metrics;

import java.util.HashMap;
import java.util.Map;

import org.kie.trustyai.service.payloads.metrics.drift.DriftMetricRequest;
import org.kie.trustyai.service.payloads.metrics.fairness.group.AdvancedGroupMetricRequest;
import org.kie.trustyai.service.payloads.metrics.fairness.group.GroupMetricRequest;
import org.kie.trustyai.service.payloads.metrics.identity.IdentityMetricRequest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = GroupMetricRequest.class, name = "GroupMetricRequest"),
        @JsonSubTypes.Type(value = AdvancedGroupMetricRequest.class, name = "AdvancedGroupMetricRequest"),
        @JsonSubTypes.Type(value = IdentityMetricRequest.class, name = "IdentityMetricRequest"),
        @JsonSubTypes.Type(value = DriftMetricRequest.class, name = "DriftMetricRequest"),
})

@JsonTypeName("BaseMetricRequest")
public abstract class BaseMetricRequest {
    private String modelId;
    private String requestName; // this is the unique name of this specific request
    private String metricName; // this is the name of the metric that this request calculates, e.g., DIR or SPD

    private Integer batchSize;

    protected BaseMetricRequest() {
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getRequestName() {
        return requestName;
    }

    public void setRequestName(String requestName) {
        this.requestName = requestName;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
    }

    public Map<String, String> retrieveDefaultTags() {
        HashMap<String, String> output = new HashMap<>();
        if (requestName != null) {
            output.put("requestName", requestName);
        }
        output.put("metricName", metricName);
        output.put("model", modelId);
        return output;
    }

    @JsonIgnore
    public BaseMetricRequest getRepresentationForRequestListing() {
        return this;
    }

    public abstract Map<String, String> retrieveTags();
}
