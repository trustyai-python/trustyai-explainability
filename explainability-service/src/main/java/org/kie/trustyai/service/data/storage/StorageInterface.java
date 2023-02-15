package org.kie.trustyai.service.data.storage;

import java.nio.ByteBuffer;

import org.kie.trustyai.service.data.exceptions.StorageReadException;

public interface StorageInterface {
    ByteBuffer getInputData() throws StorageReadException;

    ByteBuffer getOutputData() throws StorageReadException;
}
