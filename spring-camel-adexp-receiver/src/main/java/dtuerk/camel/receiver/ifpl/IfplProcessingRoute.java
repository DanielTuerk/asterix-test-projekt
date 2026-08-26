package dtuerk.camel.receiver.ifpl;

import dtuerk.camel.common.FlightPlan;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IfplProcessingRoute extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(IfplProcessingRoute.class);

    public static final String ROUTE_ID = "process-ifpl-route";

    @Override
    public void configure() throws Exception {
        from("direct:handleIfpl")
                .routeId(ROUTE_ID)
                .log("handle IFPL message, Body: ${body}")
                .convertBodyTo(String.class)
                .convertBodyTo(FlightPlan.class)
                .log("convert FlightPlan to FlightPlan: ${body}")
                .marshal().json(JsonLibrary.Jackson)
                .to("{{adexp.sink.ifpl.uri}}");
    }
}
