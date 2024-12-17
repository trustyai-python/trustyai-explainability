package org.kie.trustyai.metrics.fairness.group;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.kie.trustyai.explainability.model.*;
import org.kie.trustyai.explainability.model.dataframe.Dataframe;
import org.kie.trustyai.metrics.fairness.FairnessMetricsUtils;

public class DisparateImpactRatio {
    /**
     * Calculate disparate impact ratio (DIR).
     *
     * @param groupSelector a predicate used to select the privileged group
     * @param samples a list of inputs to be used for testing fairness
     * @param model the model to be tested for fairness
     * @param favorableOutputs the outputs that are considered favorable / desirable
     * @return SPD, between 0 and 1
     * @throws ExecutionException if any error occurs during model prediction
     * @throws InterruptedException if timeout or other interruption issues occur during model prediction
     */
    public static double calculate(Predicate<PredictionInput> groupSelector, List<PredictionInput> samples,
            PredictionProvider model, List<Output> favorableOutputs)
            throws ExecutionException, InterruptedException {

        double probabilityUnprivileged = FairnessMetricsUtils.getFavorableLabelProbability(groupSelector.negate(), samples, model, favorableOutputs);
        double probabilityPrivileged = FairnessMetricsUtils.getFavorableLabelProbability(groupSelector, samples, model, favorableOutputs);

        return probabilityUnprivileged / probabilityPrivileged;
    }

    /**
     * Calculate disparate impact ratio (DIR).
     *
     * @param samples A dataframe of inputs to be used for testing fairness
     * @param model A model as a {@link PredictionProvider} to be tested for fairness
     * @param priviledgeColumns A {@link List} of integers specifying the privileged columns
     * @param priviledgeValues A {@link List} of {@link Value} specifying the privileged values
     * @param favorableOutput The favorable output
     * @return The DIR score
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public static double calculate(Dataframe samples, PredictionProvider model, List<Integer> priviledgeColumns,
            List<Value> priviledgeValues, List<Output> favorableOutput) throws ExecutionException, InterruptedException {

        final List<PredictionInput> inputs = samples.asPredictionInputs();
        final List<PredictionOutput> outputs = model.predictAsync(inputs).get();

        final Dataframe data = Dataframe.createFrom(inputs, outputs);

        final Predicate<List<Value>> priviledgeFilter = values -> priviledgeColumns
                .stream().map(values::get)
                .collect(Collectors.toList())
                .equals(priviledgeValues);

        final Dataframe privileged = data.filterRowsByInputs(priviledgeFilter);
        final Dataframe unprivileged = data.filterRowsByInputs(priviledgeFilter.negate());

        return calculate(privileged, unprivileged, favorableOutput);
    }

    /**
     * Calculate disparate impact ratio (DIR).
     *
     * @param priviledged {@link Dataframe} with the priviledged groups
     * @param unpriviledged {@link Dataframe} with the unpriviledged groups
     * @param favorableOutput an output that is considered favorable / desirable
     * @return DIR, between 0 and 1
     */
    public static double calculate(Dataframe priviledged, Dataframe unpriviledged, List<Output> favorableOutput) {
        final List<Value> favorableValues = favorableOutput.stream().map(Output::getValue).collect(Collectors.toUnmodifiableList());
        final Predicate<List<Value>> match = values -> values.equals(favorableValues);

        final double probabilityPrivileged = (double) priviledged.filterRowsByOutputs(match).getRowDimension() / (double) priviledged.getRowDimension();
        final double probabilityUnprivileged = (double) unpriviledged.filterRowsByOutputs(match).getRowDimension() / (double) unpriviledged.getRowDimension();

        return probabilityUnprivileged / probabilityPrivileged;
    }

    /**
     * Calculate disparate impact ratio (DIR).
     *
     * @param privileged {@link Dataframe} with the privileged groups
     * @param privilegedPositive {@link Dataframe} with the privileged groups that received a positive outcome
     * @param unprivileged {@link Dataframe} with the unprivileged groups
     * @param unprivilegedPositive {@link Dataframe} with the unprivileged groups that received a positive outcome
     * @return DIR, between 0 and 1
     */
    public static double calculate(Dataframe privileged, Dataframe privilegedPositive, Dataframe unprivileged, Dataframe unprivilegedPositive) {
        final double probabilityPrivileged = (double) privilegedPositive.getRowDimension() / (double) privileged.getRowDimension();
        final double probabilityUnprivileged = (double) unprivilegedPositive.getRowDimension() / (double) unprivileged.getRowDimension();

        return probabilityUnprivileged / probabilityPrivileged;
    }
}
