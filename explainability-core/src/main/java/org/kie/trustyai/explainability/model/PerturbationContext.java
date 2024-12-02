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

import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * Context object for performing perturbations.
 * This is meant to provide metadata and utilities that are needed to perturb different {@code TYpe}s.
 *
 * see {{@link Type#perturb(Value, PerturbationContext)}}
 */
public class PerturbationContext {

    private static final double DEFAULT_STANDARD_DEVIATION = 0.01;
    private final Optional<Long> seed;

    private final Random random;

    private final int noOfPerturbations;

    private final double standardDeviation;

    public PerturbationContext(Random random, int noOfPerturbations) {
        this(random, noOfPerturbations, DEFAULT_STANDARD_DEVIATION); // preserving old behavior
    }

    public PerturbationContext(Random random, int noOfPerturbations, double standardDeviation) {
        this.standardDeviation = standardDeviation;
        this.seed = Optional.empty();
        this.random = random;
        this.noOfPerturbations = noOfPerturbations;
    }

    public PerturbationContext(Long seed, Random random, int noOfPerturbations) {
        this(seed, random, noOfPerturbations, DEFAULT_STANDARD_DEVIATION);
    }

    public PerturbationContext(Long seed, Random random, int noOfPerturbations, double standardDeviation) {
        this.seed = Optional.ofNullable(seed);
        this.random = random;
        this.standardDeviation = standardDeviation;
        if (seed != null) {
            random.setSeed(seed);
        }
        this.noOfPerturbations = noOfPerturbations;
    }

    public int getNoOfPerturbations() {
        return noOfPerturbations;
    }

    public Random getRandom() {
        return random;
    }

    public Optional<Long> getSeed() {
        return seed;
    }

    public double getStandardDeviation() {
        return standardDeviation;
    }

    @Override
    public String toString() {
        return "PerturbationContext{" +
                "random=" + random +
                ", noOfPerturbations=" + noOfPerturbations +
                ", seed=" + seed +
                ", standardDeviation=" + standardDeviation +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PerturbationContext that = (PerturbationContext) o;
        return noOfPerturbations == that.noOfPerturbations && Objects.equals(seed, that.seed) && Objects.equals(random, that.random) && standardDeviation == that.standardDeviation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(seed, random, noOfPerturbations, standardDeviation);
    }
}
