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
                .log("handle IFPL message")
                .convertBodyTo(String.class)
                .convertBodyTo(FlightPlan.class)
                .log("convert FlightPlan to FlightPlan: ${body}")
                .marshal().json(JsonLibrary.Jackson)
                /*
                 * ON CONFLICT (ifplid):
                 * Das ist nur richtig, wenn du bewusst ausschließlich die erste empfangene Version eines Flugplans speichern möchtest.
                 *
                 * Spätere Nachrichten könnten aber beispielsweise:
                 *
                 * geänderte Flugplandaten enthalten,
                 * eine aktualisierte Route enthalten,
                 * einen geänderten Flugzeugtyp oder Flight Level enthalten,
                 * andere oder zusätzliche IFP-Hinweise enthalten,
                 * denselben Flugplan erneut an deine ATC-Unit verteilen.
                 *
                 * EUROCONTROL beschreibt beispielsweise Folgeinformationen und Änderungen, die unter Angabe der IFPLID verarbeitet und an betroffene ATS Units verteilt werden
                 */
                .to("{{adexp.sink.ifpl.uri}}");
    }
}
