package org.kie.trustyai.service.data.storage.hibernate;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;
import org.kie.trustyai.explainability.model.dataframe.Dataframe;
import org.kie.trustyai.explainability.model.dataframe.DataframeMetadata;
import org.kie.trustyai.explainability.model.dataframe.DataframeRow;
import org.kie.trustyai.service.config.ServiceConfig;
import org.kie.trustyai.service.config.storage.MigrationConfig;
import org.kie.trustyai.service.config.storage.StorageConfig;
import org.kie.trustyai.service.data.DataSource;
import org.kie.trustyai.service.data.exceptions.StorageReadException;
import org.kie.trustyai.service.data.exceptions.StorageWriteException;
import org.kie.trustyai.service.data.metadata.StorageMetadata;
import org.kie.trustyai.service.data.parsers.CSVParser;
import org.kie.trustyai.service.data.storage.DataFormat;
import org.kie.trustyai.service.data.storage.Storage;

import io.quarkus.arc.lookup.LookupIfProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.kie.trustyai.service.data.storage.flatfile.PVCStorage;

@LookupIfProperty(name = "service.storage.format", stringValue = "HIBERNATE")
@ApplicationScoped
public class HibernateStorage extends Storage implements HibernateStorageInterface {
    @PersistenceContext
    EntityManager em;

    private static final Logger LOG = Logger.getLogger(HibernateStorage.class);
    private final int batchSize;
    private Optional<MigrationConfig> migrationConfig = Optional.empty();

    public HibernateStorage(ServiceConfig serviceConfig, StorageConfig storageConfig) {
        LOG.info("Starting Hibernate storage consumer" + this);

        if (serviceConfig.batchSize().isPresent()) {
            this.batchSize = serviceConfig.batchSize().getAsInt();
        } else {
            final String message = "Missing data batch size";
            LOG.error(message);
            throw new IllegalArgumentException(message);
        }

        if (storageConfig.migrationConfig().fromFilename().isPresent() && storageConfig.migrationConfig().fromFolder().isPresent()) {
            migrationConfig = Optional.of(storageConfig.migrationConfig());
        }

    }


    @PostConstruct
    protected void migrate() {
        if (migrationConfig.isPresent()) {
            MigrationConfig mc = migrationConfig.get();
            if (mc.fromFolder().isPresent() && mc.fromFilename().isPresent()) {
                String fromFolder = mc.fromFolder().get();
                String fromFile = mc.fromFilename().get();

                PVCStorage pvcStorage = new PVCStorage(fromFolder, fromFile, batchSize);
                DataSource oldDataSource = new DataSource();
                oldDataSource.setParser(new CSVParser());
                oldDataSource.setStorageOverride(pvcStorage);
                List<String> modelIds = pvcStorage.listAllModelIds();

                if (!modelIds.isEmpty()) {
                    LOG.info("Starting migration");
                    for (String modelId : modelIds) {
                        LOG.info("Migrating " + modelId + " metadata");
                        StorageMetadata sm = oldDataSource.getMetadata(modelId);
                        saveMetadata(sm, modelId);

                        // batch save the df
                        int nObs = sm.getObservations();
                        int startIdx = 0;
                        while (startIdx < nObs) {
                            int endIdx = Math.min(startIdx + batchSize, nObs);
                            LOG.info("Migrating " + modelId + " data, rows " + startIdx + "-" + endIdx + " of " + nObs);
                            Dataframe df = oldDataSource.getDataframe(modelId, startIdx, endIdx);
                            save(df, modelId);
                            startIdx += batchSize;
                        }
                    }
                }
                LOG.info("Migration complete, the PVC is now safe to remove.");
                migrationConfig = Optional.empty();
            } else {
                throw new IllegalArgumentException("Both migration file and folder must be specified to perform database migration.");
            }
        }
    }

    @Override
    public DataFormat getDataFormat() {
        return DataFormat.BEAN;
    }

    @Override
    public Dataframe readData(String modelId) throws StorageReadException {
        LOG.debug("Reading dataframe " + modelId + " from Hibernate");
        return readData(modelId, batchSize);
    }

    public Dataframe readAllData(String modelId) throws StorageReadException {
        LOG.debug("Reading dataframe " + modelId + " from Hibernate (batched)");

        List<DataframeRow> rows = em.createQuery("" +
                "select dr from DataframeRow dr" +
                " where dr.modelId = ?1 ", DataframeRow.class)
                .setParameter(1, modelId)
                .getResultList();
        DataframeMetadata dm = em.find(DataframeMetadata.class, modelId);
        return Dataframe.untranspose(rows, dm);
    }

    @Override
    public Dataframe readData(String modelId, int batchSize) throws StorageReadException {
        LOG.debug("Reading dataframe " + modelId + " from Hibernate (batched)");
        List<DataframeRow> rows = em.createQuery("" +
                "select dr from DataframeRow dr" +
                " where dr.modelId = ?1 " +
                "order by dr.dbId DESC ", DataframeRow.class)
                .setParameter(1, modelId)
                .setMaxResults(batchSize).getResultList();
        Collections.reverse(rows);
        DataframeMetadata dm = em.find(DataframeMetadata.class, modelId);
        return Dataframe.untranspose(rows, dm);
    }

    @Override
    public Dataframe readData(String modelId, int startPos, int endPos) throws StorageReadException {
        if (endPos <= startPos){
            throw new IllegalArgumentException("HibernateStorage.readData endPos must be greater than startPos. Got startPos="+startPos + ", endPos="+endPos);
        }

        LOG.debug("Reading dataframe " + modelId + " from Hibernate (batched)");
        List<DataframeRow> rows = em.createQuery("" +
                        "select dr from DataframeRow dr" +
                        " where dr.modelId = ?1 " +
                        "order by dr.dbId ASC ", DataframeRow.class)
                .setParameter(1, modelId)
                .setFirstResult(startPos)
                .setMaxResults(endPos-startPos).getResultList();
        DataframeMetadata dm = em.find(DataframeMetadata.class, modelId);
        return Dataframe.untranspose(rows, dm);
    }

    // get the row count of a persisted dataframe
    public int colCount(String modelId) {
        return em.createQuery("select size(dr.row) from DataframeRow dr where dr.modelId = ?1", int.class)
                .setParameter(1, modelId)
                .getSingleResult();
    }

    public long rowCount(String modelId) {
        return em.createQuery("select count(dr) from DataframeRow dr WHERE dr.modelId = ?1", long.class)
                .setParameter(1, modelId)
                .getSingleResult();
    }


    @Override
    @Transactional
    public void save(Dataframe dataframe, String modelId) throws StorageWriteException {
        LOG.debug("Writing dataframe=" + modelId + ", rows=" + dataframe.getRowDimension() + " to Hibernate");
        List<DataframeRow> transpose = dataframe.transpose(modelId);
        for (DataframeRow dr : transpose) {
            em.persist(dr);
        }
        DataframeMetadata dm = dataframe.getMetadata();
        dm.setId(modelId);
        if (dataframeExists(modelId)) {
            em.merge(dm);
        } else {
            em.persist(dm);
        }
    }

    @Override
    public StorageMetadata readMetadata(String modelId) throws StorageReadException {
        LOG.debug("Reading metadata for " + modelId + " from Hibernate");
        return em.find(StorageMetadata.class, modelId);
    }

    @Override
    @Transactional
    public void saveMetadata(StorageMetadata storageMetadata, String modelId) throws StorageWriteException {
        LOG.debug("Saving metadata for " + modelId + " into Hibernate");
        storageMetadata.setModelId(modelId);
        em.persist(storageMetadata);
    }


    @Transactional
    public void updateMetadata(StorageMetadata storageMetadata) throws StorageWriteException {
        LOG.debug("Updating existing metadata for " + storageMetadata.getModelId() + " in Hibernate");
        em.merge(storageMetadata);
    }

    @Override
    @Transactional
    public void append(Dataframe dataframe, String modelId) throws StorageWriteException {
        LOG.debug("Appending " + dataframe.getRowDimension() + " new rows to dataframe=" + modelId + " within Hibernate");
        List<DataframeRow> transpose = dataframe.transpose(modelId);
        for (DataframeRow dr : transpose) {
            em.persist(dr);
        }
    }


    @Override
    public boolean dataframeExists(String modelId){
        try {
            return em.find(DataframeMetadata.class, modelId) != null;
        } catch (EntityNotFoundException e) {
            return false;
        }
    }

    @Override
    public long getLastModified(String modelId) {
        return 0;
    }

    @Transactional
    public void clearData(String modelId) {
        if (dataframeExists(modelId)) {
            LOG.debug("Deleting all data from " + modelId + " within Hibernate");
            em.createQuery("" +
                    "DELETE from DataframeRow dr " +
                    "where dr.modelId = ?1")
                    .setParameter(1, modelId)
                    .executeUpdate();
            em.createQuery("" +
                    "DELETE from DataframeMetadata dm " +
                    "where dm.id = ?1")
                    .setParameter(1, modelId)
                    .executeUpdate();
        }
    }


}
