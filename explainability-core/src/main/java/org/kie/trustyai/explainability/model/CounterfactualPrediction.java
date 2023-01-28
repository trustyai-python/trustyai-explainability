/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
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
package org.kie.trustyai.explainability.model;

import java.util.UUID;

import org.kie.trustyai.explainability.local.counterfactual.goal.CounterfactualGoalCriteria;
import org.kie.trustyai.explainability.local.counterfactual.goal.DefaultCounterfactualGoalCriteria;

/**
 * The prediction generated by a {@link AsyncPredictionProvider}.
 */
public class CounterfactualPrediction extends BasePrediction {

    private final DataDistribution dataDistribution;
    private final Long maxRunningTimeSeconds;

    public CounterfactualGoalCriteria getGoalCriteria() {
        return goalCriteria;
    }

    private final CounterfactualGoalCriteria goalCriteria;

    public CounterfactualPrediction(PredictionInput input,
            PredictionOutput goal,
            DataDistribution dataDistribution,
            UUID executionId,
            Long maxRunningTimeSeconds) {
        this(input, goal, dataDistribution, executionId, maxRunningTimeSeconds, DefaultCounterfactualGoalCriteria.create(goal.getOutputs()));
    }

    public CounterfactualPrediction(PredictionInput input,
            PredictionOutput goal,
            double threshold,
            DataDistribution dataDistribution,
            UUID executionId,
            Long maxRunningTimeSeconds) {
        this(input, goal, dataDistribution, executionId, maxRunningTimeSeconds, DefaultCounterfactualGoalCriteria.create(goal.getOutputs(), threshold));
    }

    /**
     * Build a counterfactual prediction using a custom {@link CounterfactualGoalCriteria}.
     * In this case, the goal is not used directly, but kept for compatibility with the super class.
     * 
     * @param input
     * @param goal
     * @param dataDistribution
     * @param executionId
     * @param maxRunningTimeSeconds
     * @param goalCriteria
     */
    public CounterfactualPrediction(PredictionInput input,
            PredictionOutput goal,
            DataDistribution dataDistribution,
            UUID executionId,
            Long maxRunningTimeSeconds,
            CounterfactualGoalCriteria goalCriteria) {
        super(input, goal, executionId);
        this.dataDistribution = dataDistribution;
        this.maxRunningTimeSeconds = maxRunningTimeSeconds;
        this.goalCriteria = goalCriteria;
    }

    public DataDistribution getDataDistribution() {
        return dataDistribution;
    }

    public Long getMaxRunningTimeSeconds() {
        return maxRunningTimeSeconds;
    }
}
