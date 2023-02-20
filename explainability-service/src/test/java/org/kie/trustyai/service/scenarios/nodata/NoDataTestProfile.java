package org.kie.trustyai.service.scenarios.nodata;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.quarkus.test.junit.QuarkusTestProfile;

public class NoDataTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        final Map<String, String> overrides = new HashMap<>();
        overrides.put("service.storage-format", "PVC");
        overrides.put("service.model-name", "example");
        overrides.put("service.data-format", "CSV");
        overrides.put("service.metrics-schedule", "5s");
        return overrides;
    }

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(NoDataStorage.class, MockConsumerDatasource.class);

    }

}