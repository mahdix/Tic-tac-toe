package com.spaceape.hiring

import javax.ws.rs._
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response.Status
import com.codahale.metrics.health.HealthCheck

@Path("/health/default")
class MyHealthCheck extends HealthCheck {
    @GET
    def check: HealthCheck.Result = {
        return HealthCheck.Result.healthy();
    }
}

