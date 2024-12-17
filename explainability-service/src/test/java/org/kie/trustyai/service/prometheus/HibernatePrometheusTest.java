package org.kie.trustyai.service.prometheus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.kie.trustyai.service.mocks.hibernate.MockHibernateStorage;
import org.kie.trustyai.service.profiles.hibernate.HibernatePrometheusTestProfile;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(HibernatePrometheusTestProfile.class)
public class HibernatePrometheusTest extends BasePrometheusTest {
    @Inject
    Instance<MockHibernateStorage> storage;

    @Inject
    Instance<PrometheusScheduler> scheduler;

    protected static final String MODEL_ID = "example1";
    protected static final int N_SAMPLES = 100;

    @BeforeEach
    void clearRequests() {
        scheduler.get().getAllRequests().clear();
    }

    @AfterEach
    void cleanStorage() {
        // Empty mock storage
        storage.get().clearData(MODEL_ID);
    }
}
