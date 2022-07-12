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
package org.kie.trustyai.explainability.model;

/**
 * Metadata about a given {@link PredictionProvider}.
 */
public interface PredictionProviderMetadata {

    /**
     * Fetch the data distribution associated to this model
     *
     * @return the data distribution
     */
    DataDistribution getDataDistribution();

    /**
     * get the shape of inputs expected by this model
     *
     * @return a synthetic prediction input (with fake or null values)
     */
    PredictionInput getInputShape();

    /**
     * get the shape of prediction outputs generated by this model
     *
     * @return a synthetic prediction output (with fake or null values)
     */
    PredictionOutput getOutputShape();
}