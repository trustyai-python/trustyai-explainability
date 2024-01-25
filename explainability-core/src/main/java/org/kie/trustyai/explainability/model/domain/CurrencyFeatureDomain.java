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
package org.kie.trustyai.explainability.model.domain;

import java.util.Arrays;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;

@Embeddable
public class CurrencyFeatureDomain extends AbstractCategoricalFeatureDomain<Currency> {

    private CurrencyFeatureDomain(Set<Currency> categories) {
        super(categories);
    }

    public CurrencyFeatureDomain() {
        super(new HashSet<>());
    }

    /**
     * Create a {@link FeatureDomain} for a categorical feature
     *
     * @param categories A set with all the allowed category values
     * @return A {@link FeatureDomain}
     */
    public static FeatureDomain<Currency> create(Set<Currency> categories) {
        return new CurrencyFeatureDomain(categories);
    }

    public static FeatureDomain<Currency> create(List<Currency> categories) {
        return new CurrencyFeatureDomain(new HashSet<>(categories));
    }

    public static FeatureDomain<Currency> create(Currency... categories) {
        return new CurrencyFeatureDomain(new HashSet<>(Arrays.asList(categories)));
    }

    @ElementCollection
    @Access(AccessType.FIELD)
    @Override
    public Set<Currency> getCategories() {
        return this.categories;
    }
}
