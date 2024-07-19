package org.kie.trustyai.service.payloads.explainers.lime;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.kie.trustyai.explainability.local.lime.LimeConfig;
import org.kie.trustyai.service.payloads.explainers.BaseExplainerConfig;

import com.fasterxml.jackson.annotation.JsonProperty;

@Schema(description = "Configuration for the LIME explainer")
@Tag(name = "Payloads", description = "Payload definitions for the API")
public class LimeExplainerConfig extends BaseExplainerConfig {

    @Schema(required = false, description = "Separable dataset ration", defaultValue = "0.9", example = "0.9")
    @JsonProperty(value = "separable_dataset_ratio")
    private double separableDatasetRation = LimeConfig.DEFAULT_SEPARABLE_DATASET_RATIO;

    @Schema(required = false, description = "Number of retries", defaultValue = "3", example = "3")
    @JsonProperty(value = "retries")
    private int retries = LimeConfig.DEFAULT_NO_OF_RETRIES;

    @Schema(required = false, description = "Whether the explainer should adapt the variance in the generated (perturbed) data when it's not separable", defaultValue = "true", example = "true")
    @JsonProperty(value = "adaptive_variance")
    private boolean adaptiveVariance = LimeConfig.DEFAULT_ADAPT_DATASET_VARIANCE;

    @Schema(required = false, description = "Whether to penalize weights whose sparse features encoding is balanced with respect to target output", defaultValue = "true", example = "true")
    @JsonProperty(value = "penalize_balance_sparse")
    private boolean penalizeBalanceSparse = LimeConfig.DEFAULT_PENALIZE_BALANCE_SPARSE;

    @Schema(required = false, description = "Whether to prefer filtering by proximity over weighting by proximity when generating samples for the linear model", defaultValue = "true",
            example = "true")
    @JsonProperty(value = "proximity_filter")
    private boolean proximityFilter = LimeConfig.DEFAULT_PROXIMITY_FILTER;

    @Schema(required = false, description = "The proximity threshold used to filter samples when proximity filter is true", defaultValue = "0.83", example = "0.83")
    @JsonProperty(value = "proximity_threshold")
    private double proximityThreshold = LimeConfig.DEFAULT_PROXIMITY_THRESHOLD;

    @Schema(required = false, description = "The width of the kernel used to calculate proximity of sparse vector instances", defaultValue = "0.5", example = "0.5")
    @JsonProperty(value = "proximity_kernel_width")
    private double proximityKernelWidth = LimeConfig.DEFAULT_PROXIMITY_KERNEL_WIDTH;
    @Schema(required = false, description = "Encoding cluster threshold", defaultValue = "0.07", example = "0.07")
    @JsonProperty(value = "encoding_cluster_threshold")
    private double encodingClusterThreshold = LimeConfig.DEFAULT_ENCODING_CLUSTER_THRESHOLD;
    @Schema(required = false, description = "Encoding Gaussian filter width", defaultValue = "0.07", example = "0.07")
    @JsonProperty(value = "encoding_gaussian_filter_width")
    private double encodingGaussianFilterWidth = LimeConfig.DEFAULT_ENCODING_GAUSSIAN_FILTER_WIDTH;
    @Schema(required = false, description = "Whether to normalize weights generated by LIME or not", defaultValue = "false", example = "false")
    @JsonProperty(value = "normalize_weights")
    private boolean normalizeWeights = LimeConfig.DEFAULT_NORMALIZE_WEIGHTS;
    @Schema(required = false, description = "Whether to use high score feature zones for more accurate numeric features sampling", defaultValue = "true", example = "true")
    @JsonProperty(value = "high_score_feature_zones")
    private boolean highScoreFeatureZones = LimeConfig.DEFAULT_HIGH_SCORE_ZONES;
    @Schema(required = false, description = "Whether to operate feature selection", defaultValue = "true", example = "true")
    @JsonProperty(value = "feature_selection")
    private boolean featureSelection = LimeConfig.DEFAULT_FEATURE_SELECTION;
    @Schema(required = false, description = "Number of features to use", defaultValue = "true", example = "true")
    @JsonProperty(value = "n_features")
    private int nFeatures = LimeConfig.DEFAULT_NO_OF_FEATURES;
    @Schema(required = false, description = "Whether to track byproduct counterfactuals", defaultValue = "false", example = "false")
    @JsonProperty(value = "track_counterfactuals")
    private boolean trackCounterfactuals = LimeConfig.DEFAULT_TRACK_COUNTERFACTUALS;
    @Schema(required = false, description = "Whether to use a weighted linear regression model", defaultValue = "true", example = "true")
    @JsonProperty(value = "use_wlr_model")
    private boolean useWLRModel = LimeConfig.DEFAULT_USE_WLR_LINEAR_MODEL;
    @Schema(required = false, description = "Whether to run proximity filter in the interpretable space", defaultValue = "false", example = "false")
    @JsonProperty(value = "filter_interpretable")
    private boolean filterInterpretable = LimeConfig.DEFAULT_FILTER_INTERPRETABLE;

    public double getProximityKernelWidth() {
        return proximityKernelWidth;
    }

    public void setProximityKernelWidth(double proximityKernelWidth) {
        this.proximityKernelWidth = proximityKernelWidth;
    }

    public boolean isUseWLRModel() {
        return useWLRModel;
    }

    public void setUseWLRModel(boolean useWLRModel) {
        this.useWLRModel = useWLRModel;
    }

    public boolean isTrackCounterfactuals() {
        return trackCounterfactuals;
    }

    public void setTrackCounterfactuals(boolean trackCounterfactuals) {
        this.trackCounterfactuals = trackCounterfactuals;
    }

    public int getnFeatures() {
        return nFeatures;
    }

    public void setnFeatures(int nFeatures) {
        if (nFeatures <= 0) {
            throw new IllegalArgumentException("Number of features must be > 0");
        }
        this.nFeatures = nFeatures;
    }

    public double getEncodingGaussianFilterWidth() {
        return encodingGaussianFilterWidth;
    }

    public void setEncodingGaussianFilterWidth(double encodingGaussianFilterWidth) {
        this.encodingGaussianFilterWidth = encodingGaussianFilterWidth;
    }

    public double getEncodingClusterThreshold() {
        return encodingClusterThreshold;
    }

    public void setEncodingClusterThreshold(double encodingClusterThreshold) {
        this.encodingClusterThreshold = encodingClusterThreshold;
    }

    public double getProximityThreshold() {
        return proximityThreshold;
    }

    public void setProximityThreshold(double proximityThreshold) {
        this.proximityThreshold = proximityThreshold;
    }

    public boolean isPenalizeBalanceSparse() {
        return penalizeBalanceSparse;
    }

    public void setPenalizeBalanceSparse(boolean penalizeBalanceSparse) {
        this.penalizeBalanceSparse = penalizeBalanceSparse;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        if (retries <= 0) {
            throw new IllegalArgumentException("Number of retries must be > 0");
        }

        this.retries = retries;
    }

    public boolean isNormalizeWeights() {
        return normalizeWeights;
    }

    public void setNormalizeWeights(boolean normalizeWeights) {
        this.normalizeWeights = normalizeWeights;
    }

    public boolean isFilterInterpretable() {
        return filterInterpretable;
    }

    public void setFilterInterpretable(boolean filterInterpretable) {
        this.filterInterpretable = filterInterpretable;
    }

    public boolean isFeatureSelection() {
        return featureSelection;
    }

    public void setFeatureSelection(boolean featureSelection) {
        this.featureSelection = featureSelection;
    }

    public boolean isAdaptiveVariance() {
        return adaptiveVariance;
    }

    public void setAdaptiveVariance(boolean adaptiveVariance) {
        this.adaptiveVariance = adaptiveVariance;
    }

    public boolean isHighScoreFeatureZones() {
        return highScoreFeatureZones;
    }

    public void setHighScoreFeatureZones(boolean highScoreFeatureZones) {
        this.highScoreFeatureZones = highScoreFeatureZones;
    }

    public boolean isProximityFilter() {
        return proximityFilter;
    }

    public void setProximityFilter(boolean proximityFilter) {
        this.proximityFilter = proximityFilter;
    }

    public double getSeparableDatasetRation() {
        return separableDatasetRation;
    }

    public void setSeparableDatasetRation(double separableDatasetRation) {
        this.separableDatasetRation = separableDatasetRation;
    }
}
