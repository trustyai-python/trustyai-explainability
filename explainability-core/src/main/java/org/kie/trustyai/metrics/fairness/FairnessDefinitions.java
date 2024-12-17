package org.kie.trustyai.metrics.fairness;

import java.util.List;
import java.util.stream.Collectors;

import org.kie.trustyai.explainability.model.Output;
import org.kie.trustyai.explainability.model.Value;

public class FairnessDefinitions {

    private FairnessDefinitions() {
    }

    // === SPD =========================================================================================================
    /**
     * Produce a general explanation of Group Statistical Parity Difference
     */
    public static String defineGroupStatisticalParityDifference() {
        return "Statistical Parity Difference (SPD) measures imbalances in classifications by calculating " +
                "the difference between the proportion of the majority and protected classes getting a " +
                "particular outcome. Typically, -0.1 < SPD < 0.1 indicates a fair model, while a value outside " +
                "those bounds indicates an unfair model for the groups and outcomes in question.";
    }

    /**
     * Produce a specific explanation of Group Statistical Parity Difference for the chosen output and
     * computed metric value
     */
    public static String defineGroupStatisticalParityDifference(
            String protectedAttribute, List<String> privileged, List<String> unprivileged,
            String outputName, List<Value> favourableOutputValue, double metricValue) {
        String specificExample = "The SPD of %f indicates that the likelihood of " +
                "Group:%s=%s receiving Outcome:%s=%s ";
        if (metricValue > 0) {
            specificExample += "was %f percentage points higher than that of Group:%s=%s.";
        } else if (metricValue < 0) {
            specificExample += "was %f percentage points lower than that of Group:%s=%s.";
        } else {
            specificExample += "was equivalent to that of group:%s=%s.";
        }

        return String.format(specificExample,
                metricValue,
                protectedAttribute, privileged.toString(),
                outputName, favourableOutputValue.stream().map(Value::toString).collect(Collectors.toList()),
                metricValue * 100,
                protectedAttribute, unprivileged.toString());
    }

    /**
     * Produce a specific explanation of Group Statistical Parity Difference for the chosen output and
     * computed metric value
     */
    public static String defineGroupStatisticalParityDifference(
            String privilegedSelector, String unprivilegedSelector, String favorableSelector,
            double metricValue) {
        String specificExample = "The SPD of %f indicates that the likelihood of the group matching " +
                "%s receiving an outcome matching %s ";
        if (metricValue > 0) {
            specificExample += "was %f percentage points higher than that of the group matching %s.";
        } else if (metricValue < 0) {
            specificExample += "was %f percentage points lower than that of the group matching %s.";
        } else {
            specificExample += "was equivalent to that of group:%s=%s.";
        }

        return String.format(specificExample,
                metricValue,
                privilegedSelector,
                favorableSelector,
                metricValue * 100,
                unprivilegedSelector);
    }

    /**
     * Produce a specific explanation of Group Statistical Parity Difference for the chosen output and
     * computed metric value
     */
    public static String defineGroupStatisticalParityDifference(Output favorableOutput, double metricValue) {
        String specificExample = "The SPD of %f indicates that the likelihood of the " +
                "selected group receiving Outcome:%s=%s ";
        if (metricValue > 0) {
            specificExample += "was %f percentage points higher than that of the unselected group.";
        } else if (metricValue < 0) {
            specificExample += "was %f percentage points lower than that of the unselected group.";
        } else {
            specificExample += "was equivalent to that of the unselected group.";
        }

        return String.format(specificExample,
                metricValue,
                favorableOutput.getName(),
                favorableOutput.getValue().toString(),
                metricValue * 100);
    }

    // === DIR =========================================================================================================
    /**
     * Produce a general explanation of Group Disparate Impact Ratio
     */
    public static String defineGroupDisparateImpactRatio() {
        return "Disparate Impact Ratio (DIR) measures imbalances in classifications by calculating " +
                "the ratio between the proportion of the majority and protected classes getting a " +
                "particular outcome. Typically, the further away the DIR is from 1, the more unfair the model. A DIR " +
                "equal to 1 indicates a perfectly fair model for the groups and outcomes in question.";
    }

    public static String defineGroupDisparateImpactRatio(String protectedAttribute, List<String> privileged, List<String> unprivileged, String outputName, List<Value> favourableOutputValue,
            double metricValue) {
        String specificExample = "The DIR of %f indicates that the likelihood of Group:%s=%s receiving Outcome:%s=%s ";
        if (metricValue != 0) {
            specificExample += "is %f times that of Group:%s=%s.";
        } else {
            specificExample += "is equivalent to that of Group:%s=s.";
        }

        return String.format(specificExample,
                metricValue,
                protectedAttribute, privileged,
                outputName, favourableOutputValue.stream().map(Value::toString).collect(Collectors.toList()),
                metricValue,
                protectedAttribute, unprivileged);
    }

    public static String defineGroupDisparateImpactRatio(String privilegedSelector, String unprivilegedSelector, String favorableSelector,
            double metricValue) {
        String specificExample = "The DIR of %f indicates that the likelihood of the group matching %s receiving an outcome matching %s ";
        if (metricValue != 0) {
            specificExample += "is %f times that of the group matching %s.";
        } else {
            specificExample += "is equivalent to that of the group matching %s.";
        }

        return String.format(specificExample,
                metricValue,
                privilegedSelector,
                favorableSelector,
                metricValue,
                unprivilegedSelector);
    }

    /**
     * Produce a specific explanation of Group Disparate Impact Ratio for the chosen output and
     * computed metric value
     */
    public static String defineGroupDisparateImpactRatio(Output favorableOutput, double metricValue) {
        String specificExample = "The DIR of %f indicates that the likelihood of the " +
                "selected group receiving outcome:%s=%s ";
        if (metricValue != 0) {
            specificExample += "is %f times that of the unselected group.";
        } else {
            specificExample += "is equivalent to that of the unselected group.";
        }

        return String.format(specificExample,
                metricValue,
                favorableOutput.getName(),
                favorableOutput.getValue().toString(),
                metricValue);
    }
}
