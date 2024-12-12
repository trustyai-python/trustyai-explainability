package org.kie.trustyai.connectors.grpc;

/**
 * Enum for gRPC load balancing policies
 */
public enum GrpcLoadBalancing {
    ROUND_ROBIN("round_robin");

    private final String value;

    GrpcLoadBalancing(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
