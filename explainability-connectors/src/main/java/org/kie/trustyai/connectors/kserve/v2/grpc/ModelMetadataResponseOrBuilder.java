// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: grpc_predict_v2.proto

package org.kie.trustyai.connectors.kserve.v2.grpc;

public interface ModelMetadataResponseOrBuilder extends
        // @@protoc_insertion_point(interface_extends:inference.ModelMetadataResponse)
        com.google.protobuf.MessageOrBuilder {

    /**
     * <pre>
     * The model name.
     * </pre>
     *
     * <code>string name = 1;</code>
     * 
     * @return The name.
     */
    java.lang.String getName();

    /**
     * <pre>
     * The model name.
     * </pre>
     *
     * <code>string name = 1;</code>
     * 
     * @return The bytes for name.
     */
    com.google.protobuf.ByteString
            getNameBytes();

    /**
     * <pre>
     * The versions of the model available on the server.
     * </pre>
     *
     * <code>repeated string versions = 2;</code>
     * 
     * @return A list containing the versions.
     */
    java.util.List<java.lang.String>
            getVersionsList();

    /**
     * <pre>
     * The versions of the model available on the server.
     * </pre>
     *
     * <code>repeated string versions = 2;</code>
     * 
     * @return The count of versions.
     */
    int getVersionsCount();

    /**
     * <pre>
     * The versions of the model available on the server.
     * </pre>
     *
     * <code>repeated string versions = 2;</code>
     * 
     * @param index The index of the element to return.
     * @return The versions at the given index.
     */
    java.lang.String getVersions(int index);

    /**
     * <pre>
     * The versions of the model available on the server.
     * </pre>
     *
     * <code>repeated string versions = 2;</code>
     * 
     * @param index The index of the value to return.
     * @return The bytes of the versions at the given index.
     */
    com.google.protobuf.ByteString
            getVersionsBytes(int index);

    /**
     * <pre>
     * The model's platform. See Platforms.
     * </pre>
     *
     * <code>string platform = 3;</code>
     * 
     * @return The platform.
     */
    java.lang.String getPlatform();

    /**
     * <pre>
     * The model's platform. See Platforms.
     * </pre>
     *
     * <code>string platform = 3;</code>
     * 
     * @return The bytes for platform.
     */
    com.google.protobuf.ByteString
            getPlatformBytes();

    /**
     * <pre>
     * The model's inputs.
     * </pre>
     *
     * <code>repeated .inference.ModelMetadataResponse.TensorMetadata inputs = 4;</code>
     */
    java.util.List<org.kie.trustyai.connectors.kserve.v2.grpc.ModelMetadataResponse.TensorMetadata>
            getInputsList();

    /**
     * <pre>
     * The model's inputs.
     * </pre>
     *
     * <code>repeated .inference.ModelMetadataResponse.TensorMetadata inputs = 4;</code>
     */
    org.kie.trustyai.connectors.kserve.v2.grpc.ModelMetadataResponse.TensorMetadata getInputs(int index);

    /**
     * <pre>
     * The model's inputs.
     * </pre>
     *
     * <code>repeated .inference.ModelMetadataResponse.TensorMetadata inputs = 4;</code>
     */
    int getInputsCount();

    /**
     * <pre>
     * The model's inputs.
     * </pre>
     *
     * <code>repeated .inference.ModelMetadataResponse.TensorMetadata inputs = 4;</code>
     */
    java.util.List<? extends org.kie.trustyai.connectors.kserve.v2.grpc.ModelMetadataResponse.TensorMetadataOrBuilder>
            getInputsOrBuilderList();

    /**
     * <pre>
     * The model's inputs.
     * </pre>
     *
     * <code>repeated .inference.ModelMetadataResponse.TensorMetadata inputs = 4;</code>
     */
    org.kie.trustyai.connectors.kserve.v2.grpc.ModelMetadataResponse.TensorMetadataOrBuilder getInputsOrBuilder(
            int index);

    /**
     * <pre>
     * The model's outputs.
     * </pre>
     *
     * <code>repeated .inference.ModelMetadataResponse.TensorMetadata outputs = 5;</code>
     */
    java.util.List<org.kie.trustyai.connectors.kserve.v2.grpc.ModelMetadataResponse.TensorMetadata>
            getOutputsList();

    /**
     * <pre>
     * The model's outputs.
     * </pre>
     *
     * <code>repeated .inference.ModelMetadataResponse.TensorMetadata outputs = 5;</code>
     */
    org.kie.trustyai.connectors.kserve.v2.grpc.ModelMetadataResponse.TensorMetadata getOutputs(int index);

    /**
     * <pre>
     * The model's outputs.
     * </pre>
     *
     * <code>repeated .inference.ModelMetadataResponse.TensorMetadata outputs = 5;</code>
     */
    int getOutputsCount();

    /**
     * <pre>
     * The model's outputs.
     * </pre>
     *
     * <code>repeated .inference.ModelMetadataResponse.TensorMetadata outputs = 5;</code>
     */
    java.util.List<? extends org.kie.trustyai.connectors.kserve.v2.grpc.ModelMetadataResponse.TensorMetadataOrBuilder>
            getOutputsOrBuilderList();

    /**
     * <pre>
     * The model's outputs.
     * </pre>
     *
     * <code>repeated .inference.ModelMetadataResponse.TensorMetadata outputs = 5;</code>
     */
    org.kie.trustyai.connectors.kserve.v2.grpc.ModelMetadataResponse.TensorMetadataOrBuilder getOutputsOrBuilder(
            int index);
}
