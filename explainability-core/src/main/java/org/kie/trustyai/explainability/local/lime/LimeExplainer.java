/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.trustyai.explainability.local.lime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.kie.trustyai.explainability.local.LocalExplainer;
import org.kie.trustyai.explainability.local.LocalExplanationException;
import org.kie.trustyai.explainability.model.DataDistribution;
import org.kie.trustyai.explainability.model.Feature;
import org.kie.trustyai.explainability.model.FeatureDistribution;
import org.kie.trustyai.explainability.model.FeatureImportance;
import org.kie.trustyai.explainability.model.Output;
import org.kie.trustyai.explainability.model.PerturbationContext;
import org.kie.trustyai.explainability.model.Prediction;
import org.kie.trustyai.explainability.model.PredictionInput;
import org.kie.trustyai.explainability.model.PredictionOutput;
import org.kie.trustyai.explainability.model.PredictionProvider;
import org.kie.trustyai.explainability.model.Saliency;
import org.kie.trustyai.explainability.model.SaliencyResults;
import org.kie.trustyai.explainability.model.SimplePrediction;
import org.kie.trustyai.explainability.model.Type;
import org.kie.trustyai.explainability.model.Value;
import org.kie.trustyai.explainability.utils.DataUtils;
import org.kie.trustyai.explainability.utils.LinearModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * An implementation of LIME algorithm (Ribeiro et al., 2016) that handles tabular data, text data, complex hierarchically
 * organized data, etc. seamlessly.
 * <p>
 * Differences with respect to the original (python) implementation:
 * - the linear (interpretable) model is based on a perceptron algorithm instead of Lasso + Ridge regression
 * - perturbing numerical features is done by sampling from a standard normal distribution centered around the value of the feature value associated with the prediction to be explained
 * - numerical features are max-min scaled and clustered via a gaussian kernel
 */
public class LimeExplainer implements LocalExplainer<SaliencyResults> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LimeExplainer.class);

    private final LimeConfig limeConfig;

    public LimeExplainer() {
        this(new LimeConfig());
    }

    public LimeExplainer(LimeConfig limeConfig) {
        this.limeConfig = limeConfig;
    }

    public LimeConfig getLimeConfig() {
        return limeConfig;
    }

    @Override
    public CompletableFuture<SaliencyResults> explainAsync(Prediction prediction, PredictionProvider model,
            Consumer<SaliencyResults> intermediateResultsConsumer) {
        PredictionInput originalInput = prediction.getInput();
        if (originalInput == null || originalInput.getFeatures() == null ||
                (originalInput.getFeatures() != null && originalInput.getFeatures().isEmpty())) {
            throw new LocalExplanationException("cannot explain a prediction whose input is empty");
        }
        // transform a possibly complex / nested input into a flat input with "linearized" features
        PredictionInput targetInput = DataUtils.linearizeInputs(List.of(originalInput)).get(0);
        List<Feature> linearizedTargetInputFeatures = targetInput.getFeatures();
        if (linearizedTargetInputFeatures.isEmpty()) {
            throw new LocalExplanationException("input features linearization failed");
        }
        // get the actual output of the model
        List<Output> actualOutputs = prediction.getOutput().getOutputs();

        LimeConfig executionConfig = limeConfig.copy();
        return explainWithExecutionConfig(model, originalInput, linearizedTargetInputFeatures, actualOutputs, executionConfig);
    }

    protected CompletableFuture<SaliencyResults> explainWithExecutionConfig(PredictionProvider model, PredictionInput originalInput, List<Feature> linearizedTargetInputFeatures,
            List<Output> actualOutputs, LimeConfig executionConfig) {
        int noOfSamples = executionConfig.getNoOfSamples();

        if (noOfSamples <= 0) {
            noOfSamples = (int) Math.pow(2, linearizedTargetInputFeatures.size());
            LOGGER.debug("using 2^|features| samples ({})", noOfSamples);
            executionConfig = executionConfig.withSamples(noOfSamples);
        }

        return explainRetryCycle(model, originalInput, linearizedTargetInputFeatures, actualOutputs, executionConfig);
    }

    protected CompletableFuture<SaliencyResults> explainRetryCycle(
            PredictionProvider model,
            PredictionInput originalInput,
            List<Feature> linearizedTargetInputFeatures,
            List<Output> actualOutputs,
            LimeConfig executionConfig) {

        // perturb the original input many times and produce a list of perturbed inputs and their respective preservation masks
        Pair<List<PredictionInput>, List<boolean[]>> perturbedInputsAndPreservationMask =
                getPerturbedInputs(originalInput.getFeatures(), executionConfig, model);

        return model.predictAsync(perturbedInputsAndPreservationMask.getLeft())
                .thenCompose(predictionOutputs -> {
                    try {
                        boolean strict = executionConfig.getNoOfRetries() > 0;
                        List<LimeInputs> limeInputsList = getLimeInputs(linearizedTargetInputFeatures, actualOutputs,
                                perturbedInputsAndPreservationMask.getLeft(), perturbedInputsAndPreservationMask.getRight(), predictionOutputs, strict);
                        return completedFuture(getSaliencies(linearizedTargetInputFeatures, actualOutputs, limeInputsList, executionConfig));
                    } catch (DatasetNotSeparableException e) {
                        if (executionConfig.getNoOfRetries() > 0) {
                            return adjustAndRetry(model, originalInput, linearizedTargetInputFeatures, actualOutputs, executionConfig);
                        }
                        throw e;
                    }
                });
    }

    private CompletableFuture<SaliencyResults> adjustAndRetry(PredictionProvider model, PredictionInput originalInput,
            List<Feature> linearizedTargetInputFeatures, List<Output> actualOutputs,
            LimeConfig executionConfig) {
        if (executionConfig.isAdaptDatasetVariance()) {
            PerturbationContext newPerturbationContext = getNewPerturbationContext(linearizedTargetInputFeatures, executionConfig.getNoOfRetries(), executionConfig.getPerturbationContext());
            int newNoOfSamples = executionConfig.getNoOfSamples() + executionConfig.getNoOfSamples() / executionConfig.getNoOfRetries();
            executionConfig = executionConfig.withSamples(newNoOfSamples).withPerturbationContext(newPerturbationContext);
        }
        return explainRetryCycle(model, originalInput, linearizedTargetInputFeatures, actualOutputs, executionConfig.withRetries(executionConfig.getNoOfRetries() - 1));
    }

    private PerturbationContext getNewPerturbationContext(List<Feature> linearizedTargetInputFeatures, int noOfRetries, PerturbationContext perturbationContext) {
        PerturbationContext newPerturbationContext;
        int nextPerturbationSize = Math.max(perturbationContext.getNoOfPerturbations() + 1,
                linearizedTargetInputFeatures.size() / noOfRetries);
        // make sure to stay within the max no. of features boundaries
        nextPerturbationSize = Math.min(linearizedTargetInputFeatures.size() - 1, nextPerturbationSize);
        Optional<Long> optionalSeed = perturbationContext.getSeed();
        if (optionalSeed.isPresent()) {
            Long seed = optionalSeed.get();
            newPerturbationContext = new PerturbationContext(seed, perturbationContext.getRandom(),
                    nextPerturbationSize);
        } else {
            newPerturbationContext = new PerturbationContext(perturbationContext.getRandom(),
                    nextPerturbationSize);
        }
        return newPerturbationContext;
    }

    /**
     * Obtain the inputs to the LIME algorithm, for each output in the original prediction.
     *
     * @param linearizedTargetInputFeatures the linarized features
     * @param actualOutputs the list of outputs to generate the explanations for
     * @param perturbedInputs the list of perturbed inputs
     * @param predictionOutputs the list of outputs associated to each perturbed input
     * @param strict whether accepting unique values for a given output in the {@code perturbedOutputs}
     * @return a list of inputs to the LIME algorithm
     */
    private List<LimeInputs> getLimeInputs(List<Feature> linearizedTargetInputFeatures, List<Output> actualOutputs,
            List<PredictionInput> perturbedInputs, List<boolean[]> preservationMasks, List<PredictionOutput> predictionOutputs, boolean strict) {
        List<LimeInputs> limeInputsList = new ArrayList<>();
        for (int o = 0; o < actualOutputs.size(); o++) {
            LimeInputs limeInputs = prepareInputs(perturbedInputs, preservationMasks, predictionOutputs, linearizedTargetInputFeatures,
                    o, actualOutputs.get(o), strict);
            limeInputsList.add(limeInputs);
        }
        return limeInputsList;
    }

    private SaliencyResults getSaliencies(List<Feature> linearizedTargetInputFeatures, List<Output> actualOutputs,
            List<LimeInputs> limeInputsList, LimeConfig executionConfig) {

        Map<String, Saliency> result = new HashMap<>();
        for (int o = 0; o < actualOutputs.size(); o++) {
            LimeInputs limeInputs = limeInputsList.get(o);
            Output originalOutput = actualOutputs.get(o);
            getSaliency(linearizedTargetInputFeatures, result, limeInputs, originalOutput, executionConfig);
            LOGGER.debug("weights set for output {}", originalOutput);
        }

        List<Pair<SimplePrediction, boolean[]>> availableCounterfactuals = new ArrayList<>();
        if (executionConfig.isTrackCounterfactuals()) {
            for (int inputIDX = 0; inputIDX < limeInputsList.get(0).getPerturbedInputs().size(); inputIDX++) {
                availableCounterfactuals.add(Pair.of(
                        new SimplePrediction(limeInputsList.get(0).getPerturbedInputs().get(inputIDX),
                                limeInputsList.get(0).getPerturbedPredictionOutputs().get(inputIDX)),
                        limeInputsList.get(0).getPreservationMasks().get(inputIDX)));
            }
        }
        return new SaliencyResults(result,
                availableCounterfactuals,
                SaliencyResults.SourceExplainer.LIME);
    }

    private void getSaliency(List<Feature> targetInputFeatures, Map<String, Saliency> result,
            LimeInputs limeInputs, Output originalOutput, LimeConfig executionConfig) {
        List<FeatureImportance> featureImportanceList = new ArrayList<>();

        List<Feature> linearizedTargetInputFeatures;
        if (executionConfig.isFeatureSelection() && targetInputFeatures.size() > executionConfig.getNoOfFeatures()) {
            linearizedTargetInputFeatures = selectFeatures(executionConfig, limeInputs, targetInputFeatures,
                    originalOutput, executionConfig.getPerturbationContext());
        } else {
            linearizedTargetInputFeatures = targetInputFeatures;
        }

        // encode the training data so that it can be fed into the linear model
        List<PredictionInput> datasetInputs = limeInputs.getPerturbedInputs();
        List<Output> datasetOutputs = limeInputs.getPerturbedOutputs();
        DatasetEncoder datasetEncoder = new DatasetEncoder(datasetInputs, datasetOutputs,
                linearizedTargetInputFeatures, originalOutput, executionConfig.getEncodingParams());
        List<Pair<double[], Double>> trainingSet = datasetEncoder.getEncodedTrainingSet();

        // weight the training samples based on the proximity to the target input to explain
        double kernelWidth = executionConfig.getProximityKernelWidth() * Math.sqrt(linearizedTargetInputFeatures.size());
        double[] sampleWeights;
        if (executionConfig.isFilterInterpretable()) {
            sampleWeights = SampleWeighter.getSampleWeightsInterpretable(linearizedTargetInputFeatures, trainingSet, kernelWidth);
        } else {
            List<List<Feature>> featureLists =
                    limeInputs.getPerturbedInputs().stream().map(PredictionInput::getFeatures).map(DataUtils::getLinearizedFeatures).collect(Collectors.toList());
            sampleWeights = SampleWeighter.getSampleWeightsOriginal(targetInputFeatures, featureLists, kernelWidth);
        }

        int ts = linearizedTargetInputFeatures.size();
        double[] featureWeights = new double[ts];
        Arrays.fill(featureWeights, 1);
        if (executionConfig.isPenalizeBalanceSparse()) {
            IndependentSparseFeatureBalanceFilter sparseFeatureBalanceFilter = new IndependentSparseFeatureBalanceFilter();
            sparseFeatureBalanceFilter.apply(featureWeights, linearizedTargetInputFeatures, trainingSet);
        }

        if (executionConfig.isProximityFilter()) {
            ProximityFilter proximityFilter = new ProximityFilter(executionConfig.getProximityThreshold(),
                    executionConfig.getProximityFilteredDatasetMinimum().doubleValue());
            proximityFilter.apply(trainingSet, sampleWeights);
        }

        LinearModel linearModel = new LinearModel(linearizedTargetInputFeatures.size(), limeInputs.isClassification(),
                executionConfig.getPerturbationContext().getRandom());

        double loss = executionConfig.isUseWLRLinearModel() ? linearModel.fitWLRR(trainingSet, sampleWeights) : linearModel.fit(trainingSet, sampleWeights);
        if (!Double.isNaN(loss)) {
            // create the output saliency
            double[] weights = linearModel.getWeights();
            if (executionConfig.isNormalizeWeights() && weights.length > 0) {
                normalizeWeights(weights);
            }
            int i = 0;
            for (Feature linearizedFeature : linearizedTargetInputFeatures) {
                FeatureImportance featureImportance = new FeatureImportance(linearizedFeature, weights[i]
                        * featureWeights[i]);
                featureImportanceList.add(featureImportance);
                i++;
            }
        }
        Saliency saliency = new Saliency(originalOutput, featureImportanceList);
        result.put(originalOutput.getName(), saliency);
    }

    private List<Feature> selectFeatures(LimeConfig executionConfig, LimeInputs limeInputs, List<Feature> linearizedTargetInputFeatures,
            Output originalOutput, PerturbationContext perturbationContext) {
        // encode the training data so that it can be fed into the linear model
        DatasetEncoder datasetEncoder = new DatasetEncoder(limeInputs.getPerturbedInputs(), limeInputs.getPerturbedOutputs(),
                linearizedTargetInputFeatures, originalOutput, executionConfig.getEncodingParams());
        List<Pair<double[], Double>> trainingSet = datasetEncoder.getEncodedTrainingSet();

        // weight the training samples based on the proximity to the target input to explain
        double kernelWidth = executionConfig.getProximityKernelWidth() * Math.sqrt(linearizedTargetInputFeatures.size());
        double[] sampleWeights;
        if (executionConfig.isFilterInterpretable()) {
            sampleWeights = SampleWeighter.getSampleWeightsInterpretable(linearizedTargetInputFeatures, trainingSet, kernelWidth);
        } else {
            List<List<Feature>> featureLists =
                    limeInputs.getPerturbedInputs().stream().map(PredictionInput::getFeatures).map(DataUtils::getLinearizedFeatures).collect(Collectors.toList());
            sampleWeights = SampleWeighter.getSampleWeightsOriginal(linearizedTargetInputFeatures, featureLists, kernelWidth);
        }

        List<Feature> selectedFeatures;
        if (executionConfig.isProximityFilter()) {
            ProximityFilter proximityFilter = new ProximityFilter(executionConfig.getProximityThreshold(),
                    executionConfig.getProximityFilteredDatasetMinimum().doubleValue());
            proximityFilter.apply(trainingSet, sampleWeights);
        }
        if (linearizedTargetInputFeatures.size() > 6) {
            // highest weights
            LinearModel linearModel = new LinearModel(linearizedTargetInputFeatures.size(), limeInputs.isClassification(), perturbationContext.getRandom());
            double loss = executionConfig.isUseWLRLinearModel() ? linearModel.fitWLRR(trainingSet, sampleWeights) : linearModel.fit(trainingSet, sampleWeights);
            LOGGER.trace("Feature selection loss: {}", loss);
            double[] weights = linearModel.getWeights();
            List<FeatureImportance> fis = new ArrayList<>();
            for (int i = 0; i < weights.length; i++) {
                fis.add(new FeatureImportance(linearizedTargetInputFeatures.get(i), weights[i]));
            }
            List<FeatureImportance> topFeatures = new Saliency(originalOutput, fis).getTopFeatures(executionConfig.getNoOfFeatures());
            selectedFeatures = topFeatures.stream().map(FeatureImportance::getFeature).collect(Collectors.toList());
        } else {
            // forward selection
            int s = 0;
            List<Feature> candidates = new ArrayList<>(linearizedTargetInputFeatures);
            List<Feature> selected = new ArrayList<>();
            while (s < executionConfig.getNoOfFeatures()) {
                // for each feature:
                Map<Feature, Double> scores = new HashMap<>();
                for (Feature candidateFeature : candidates) {
                    // 1. add one feature at a time from the candidates
                    List<Feature> currentFeatures = new ArrayList<>(selected);
                    currentFeatures.add(candidateFeature);
                    DatasetEncoder currentDatasetEncoder = new DatasetEncoder(limeInputs.getPerturbedInputs(), limeInputs.getPerturbedOutputs(),
                            currentFeatures, originalOutput, executionConfig.getEncodingParams());
                    List<Pair<double[], Double>> currentTrainingSet = currentDatasetEncoder.getEncodedTrainingSet();

                    if (executionConfig.isProximityFilter()) {
                        ProximityFilter proximityFilter = new ProximityFilter(executionConfig.getProximityThreshold(),
                                executionConfig.getProximityFilteredDatasetMinimum().doubleValue());
                        proximityFilter.apply(currentTrainingSet, sampleWeights);
                    }

                    // 2. train the model
                    LinearModel currentLinearModel = new LinearModel(currentFeatures.size(), limeInputs.isClassification(), perturbationContext.getRandom());

                    double candidateLoss =
                            executionConfig.isUseWLRLinearModel() ? currentLinearModel.fitWLRR(currentTrainingSet, sampleWeights) : currentLinearModel.fit(currentTrainingSet, sampleWeights);

                    // 3. record its score
                    scores.put(candidateFeature, candidateLoss);
                }
                // 4. finally select the top scoring feature
                List<Map.Entry<Feature, Double>> sortedEntries = scores.entrySet().stream().sorted(Comparator.comparingDouble(Map.Entry::getValue)).collect(Collectors.toList());
                Feature selectedFeature = sortedEntries.get(0).getKey();

                // 5. remove it from the candidates
                candidates.removeIf(f -> f.equals(selectedFeature));

                // 6. add it to the selected
                selected.add(selectedFeature);
                s++;
            }
            selectedFeatures = selected;
        }
        return selectedFeatures;
    }

    private void normalizeWeights(double[] weights) {
        double max = Arrays.stream(weights).max().orElse(1);
        double min = Arrays.stream(weights).min().orElse(0);
        if (max != min) {
            for (int k = 0; k < weights.length; k++) {
                weights[k] = (weights[k] - min) / (max - min);
            }
        }
    }

    /**
     * Check the perturbed inputs so that the dataset of perturbed input / outputs contains more than just one output
     * class, otherwise it would be impossible to linearly separate it, and hence learn meaningful weights to be used as
     * feature importance scores.
     * The check can be {@code strict} or not, if so it will throw a {@code DatasetNotSeparableException} when the dataset
     * for a given output is not separable.
     */
    private LimeInputs prepareInputs(List<PredictionInput> perturbedInputs, List<boolean[]> preservationMasks, List<PredictionOutput> perturbedOutputs,
            List<Feature> linearizedTargetInputFeatures, int o, Output currentOutput, boolean strict) {

        if (currentOutput.getValue() != null && currentOutput.getValue().getUnderlyingObject() != null) {
            Map<Double, Long> rawClassesBalance;

            // calculate the no. of samples belonging to each output class
            Value fv = currentOutput.getValue();
            rawClassesBalance = getClassBalance(perturbedOutputs, fv, o);
            Long max = rawClassesBalance.values().stream().max(Long::compareTo).orElse(1L);
            double separationRatio = (double) max / (double) perturbedInputs.size();

            List<Output> outputs = perturbedOutputs.stream().map(po -> po.getOutputs().get(o)).collect(Collectors.toList());
            boolean classification = rawClassesBalance.size() == 2;
            if (strict) {
                // check if the dataset is separable and also if the linear model should fit a regressor or a classifier
                if (rawClassesBalance.size() > 1 && separationRatio < limeConfig.getSeparableDatasetRatio()) {
                    // if dataset creation process succeeds use it to train the linear model
                    return new LimeInputs(classification, linearizedTargetInputFeatures, currentOutput, perturbedInputs, preservationMasks, perturbedOutputs, outputs);
                } else {
                    throw new DatasetNotSeparableException(currentOutput, rawClassesBalance);
                }
            } else {
                LOGGER.warn("Using a hardly separable dataset for output '{}' of type '{}' with value '{}' ({})",
                        currentOutput.getName(), currentOutput.getType(), currentOutput.getValue(), rawClassesBalance);
                return new LimeInputs(classification, linearizedTargetInputFeatures, currentOutput, perturbedInputs, preservationMasks, perturbedOutputs, outputs);
            }

        } else {
            return new LimeInputs(false, linearizedTargetInputFeatures, currentOutput, emptyList(), emptyList(), emptyList(), emptyList());
        }
    }

    private Map<Double, Long> getClassBalance(List<PredictionOutput> perturbedOutputs, Value fv, int finalO) {
        Map<Double, Long> rawClassesBalance;
        rawClassesBalance = perturbedOutputs.stream()
                .map(p -> p.getOutputs().get(finalO)) // get the (perturbed) output value corresponding to the one to be explained
                .map(output -> toDouble(output, fv))
                .collect(Collectors.groupingBy(Double::doubleValue, Collectors.counting())); // then group-count distinct output values
        LOGGER.debug("raw samples per class: {}", rawClassesBalance);
        return rawClassesBalance;
    }

    private double toDouble(Output output, Value fv) {
        // if numeric use it as it is
        if (Type.NUMBER.equals(output.getType())) {
            return output.getValue().asNumber();
        }
        // otherwise check if target and perturbed outputs are both null
        boolean nullValues = output.getValue().getUnderlyingObject() == null
                && fv.getUnderlyingObject() == null;
        // if not null, check for underlying value equality
        boolean equalityCheck = output.getValue().getUnderlyingObject() != null
                && output.getValue().asString().equals(fv.asString());

        return nullValues || equalityCheck ? 1d : 0d;
    }

    private Pair<List<PredictionInput>, List<boolean[]>> getPerturbedInputs(List<Feature> features, LimeConfig executionConfig,
            PredictionProvider predictionProvider) {
        List<PredictionInput> perturbedInputs = new ArrayList<>();
        List<boolean[]> preservationMask = new ArrayList<>();
        int size = executionConfig.getNoOfSamples();
        DataDistribution dataDistribution = executionConfig.getDataDistribution();

        Map<String, FeatureDistribution> featureDistributionsMap;
        PerturbationContext perturbationContext = executionConfig.getPerturbationContext();
        // if an existing distribution is present, use it to bootstrap numeric feature distributions
        if (!dataDistribution.isEmpty()) {
            Map<String, HighScoreNumericFeatureZones> numericFeatureZonesMap;
            int max = executionConfig.getBoostrapInputs();
            if (executionConfig.isHighScoreFeatureZones()) {
                // identify high score feature zones, if possible
                numericFeatureZonesMap = HighScoreNumericFeatureZonesProvider
                        .getHighScoreFeatureZones(dataDistribution, predictionProvider, features, max);
            } else {
                numericFeatureZonesMap = new HashMap<>();
            }

            // generate feature distributions, if possible
            featureDistributionsMap = DataUtils.boostrapFeatureDistributions(
                    dataDistribution, perturbationContext, 2 * size,
                    1, Math.min(size, max), numericFeatureZonesMap);
        } else {
            featureDistributionsMap = new HashMap<>();
        }

        for (int i = 0; i < size; i++) {
            Pair<List<Feature>, boolean[]> newFeaturesAndPreservationMask =
                    DataUtils.perturbFeaturesWithPreservationMask(features, perturbationContext, featureDistributionsMap);
            perturbedInputs.add(new PredictionInput(newFeaturesAndPreservationMask.getLeft()));
            preservationMask.add(newFeaturesAndPreservationMask.getRight());
        }
        return Pair.of(perturbedInputs, preservationMask);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LimeExplainer that = (LimeExplainer) o;
        return Objects.equals(limeConfig, that.limeConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(limeConfig);
    }
}
