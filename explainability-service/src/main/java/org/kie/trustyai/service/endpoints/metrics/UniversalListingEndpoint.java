package org.kie.trustyai.service.endpoints.metrics;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;
import org.kie.trustyai.service.payloads.metrics.BaseMetricRequest;
import org.kie.trustyai.service.payloads.scheduler.ScheduleList;
import org.kie.trustyai.service.payloads.scheduler.ScheduleRequest;
import org.kie.trustyai.service.prometheus.PrometheusScheduler;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Tag(name = "Metrics Information Endpoint", description = "Return a list of all metrics being currently requested.")
@Path("/metrics/all")
public class UniversalListingEndpoint {
    private static final Logger LOG = Logger.getLogger(UniversalListingEndpoint.class);

    private static final String TYPE_ALL = "all";
    private static final String TYPE_FAIRNESS = "fairness";

    @Inject
    PrometheusScheduler scheduler;

    UniversalListingEndpoint() {
        super();
    }

    @GET
    @Path("/requests")
    @Operation(summary = "Retrieve a list of all currently scheduled metric computations.")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listRequests(@QueryParam("type") String type) {
        final ScheduleList scheduleList = new ScheduleList();
        if (type == null || type.equals(TYPE_ALL)) {
            // Return all requested metrics, for all metric types
            for (Map.Entry<UUID, BaseMetricRequest> entry : scheduler.getAllRequestsFlat().entrySet()) {
                scheduleList.requests.add(new ScheduleRequest(entry.getKey(), entry.getValue().getRepresentationForRequestListing()));
            }
        } else if (type.equals(TYPE_FAIRNESS)) {
            // Return all requested metrics, just for bias metrics
            for (Map.Entry<UUID, BaseMetricRequest> entry : scheduler.getAllRequestsFlat().entrySet()) {
                final String metricName = entry.getValue().getMetricName();
                if (Objects.equals(metricName, "SPD") || Objects.equals(metricName, "DIR")) {
                    scheduleList.requests.add(new ScheduleRequest(entry.getKey(), entry.getValue().getRepresentationForRequestListing()));
                }
            }
        } else {
            // If the requests type is invalid
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid metric requests type: " + type).build();
        }
        return Response.ok(scheduleList).build();
    }
}
