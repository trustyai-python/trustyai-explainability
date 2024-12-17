package org.kie.trustyai.service.payloads.metrics.fairness.group;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.kie.trustyai.service.payloads.data.download.DataRequestPayload;
import org.kie.trustyai.service.payloads.metrics.BaseMetricRequest;
import org.kie.trustyai.service.payloads.values.reconcilable.ReconcilableFeature;
import org.kie.trustyai.service.payloads.values.reconcilable.ReconcilableOutput;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.node.TextNode;

@JsonPropertyOrder({ "protected", "favorable" })
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "@type",
        defaultImpl = AdvancedGroupMetricRequest.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = AdvancedGroupMetricRequest.class, name = "AdvancedGroupMetricRequest")
})
public class AdvancedGroupMetricRequest extends BaseMetricRequest {
    private DataRequestPayload privilegedAttribute;
    private DataRequestPayload unprivilegedAttribute;
    private DataRequestPayload favorableOutcome;
    private String modelId;

    // define an "output name" and "privileged attribute name" to match the existing metric request format
    private final static String VIRTUAL_FIELD_NAME = "Defined by TrustyQL";

    private Double thresholdDelta;

    public AdvancedGroupMetricRequest() {
        // Public default no-argument constructor
        super();

    }

    public DataRequestPayload getFavorableOutcome() {
        return favorableOutcome;
    }

    public void setFavorableOutcome(DataRequestPayload favorableOutcome) {
        this.favorableOutcome = favorableOutcome;
    }

    public DataRequestPayload getPrivilegedAttribute() {
        return privilegedAttribute;
    }

    public void setPrivilegedAttribute(DataRequestPayload privilegedAttribute) {
        this.privilegedAttribute = privilegedAttribute;
    }

    public DataRequestPayload getUnprivilegedAttribute() {
        return unprivilegedAttribute;
    }

    public void setUnprivilegedAttribute(DataRequestPayload unprivilegedAttribute) {
        this.unprivilegedAttribute = unprivilegedAttribute;
    }

    public Double getThresholdDelta() {
        return thresholdDelta;
    }

    public void setThresholdDelta(Double thresholdDelta) {
        this.thresholdDelta = thresholdDelta;
    }

    public String getOutcomeName() {
        return VIRTUAL_FIELD_NAME;
    }

    public String getProtectedAttribute() {
        return VIRTUAL_FIELD_NAME;
    }

    // Tag Retrieval
    public Map<String, String> retrieveTags() {
        Map<String, String> tags = new HashMap<>();
        tags.put("favorable_value", this.getFavorableOutcome().toString());
        tags.put("privileged", this.getPrivilegedAttribute().toString());
        tags.put("unprivileged", this.getUnprivilegedAttribute().toString());
        tags.put("batch_size", String.valueOf(this.getBatchSize()));
        return tags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        AdvancedGroupMetricRequest that = (AdvancedGroupMetricRequest) o;
        return favorableOutcome.equals(that.favorableOutcome)
                && privilegedAttribute.equals(that.privilegedAttribute)
                && unprivilegedAttribute.equals(that.unprivilegedAttribute)
                && this.getMetricName().equals(that.getMetricName())
                && Objects.equals(thresholdDelta, that.thresholdDelta)
                && Objects.equals(getBatchSize(), that.getBatchSize());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                favorableOutcome,
                privilegedAttribute,
                unprivilegedAttribute,
                thresholdDelta,
                getBatchSize(),
                this.getMetricName());
    }

    // map the representation in the /requests endpoint to comply with group endpoint spec
    @JsonIgnore
    @Override
    public BaseMetricRequest getRepresentationForRequestListing() {
        GroupMetricRequest gmr = new GroupMetricRequest();
        gmr.setMetricName(this.getMetricName());
        gmr.setModelId(this.getModelId());
        gmr.setOutcomeName(getOutcomeName());
        gmr.setProtectedAttribute(this.getProtectedAttribute());
        gmr.setPrivilegedAttribute(new ReconcilableFeature(new TextNode(privilegedAttribute.toString())));
        gmr.setUnprivilegedAttribute(new ReconcilableFeature(new TextNode(unprivilegedAttribute.toString())));
        gmr.setFavorableOutcome(new ReconcilableOutput(new TextNode(favorableOutcome.toString())));
        return gmr;
    }
}
