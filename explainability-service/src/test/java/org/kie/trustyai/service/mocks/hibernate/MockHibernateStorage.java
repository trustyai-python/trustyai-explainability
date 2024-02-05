package org.kie.trustyai.service.mocks.hibernate;

import org.kie.trustyai.service.data.storage.hibernate.HibernateStorage;

import io.quarkus.test.Mock;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

@Mock
@Alternative
@ApplicationScoped
public class MockHibernateStorage extends HibernateStorage {

    public MockHibernateStorage() {
        super(new MockHibernateServiceConfig(), new MockHibernateStorageConfig());
    }

}
