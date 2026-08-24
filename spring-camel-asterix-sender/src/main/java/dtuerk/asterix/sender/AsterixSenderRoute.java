package dtuerk.asterix.sender;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Erzeugt im festen Takt einen ASTERIX-CAT-048-Datenblock und schickt ihn an das
 * konfigurierte Ziel ({@code asterix.target.uri}) - im Normalfall per UDP an den
 * spring-camel-asterix-receiver.
 */
@Component
public class AsterixSenderRoute extends RouteBuilder {

    public static final String ROUTE_ID = "asterix-sender";

    private static final Logger LOG = LoggerFactory.getLogger(AsterixSenderRoute.class);

    private final SimulatedTarget simulatedTarget;

    public AsterixSenderRoute(SimulatedTarget simulatedTarget) {
        this.simulatedTarget = simulatedTarget;
    }

    @Override
    public void configure() {
        from("timer:asterix-sender?period={{asterix.sender.period-millis}}")
                .routeId(ROUTE_ID)
                // Body: Cat048Plot
                .bean(simulatedTarget, "nextPlot")
                .log(LoggingLevel.INFO, LOG, "Sende ${body}")
                // Body: byte[] - der fertige ASTERIX-Datenblock
                .bean(Cat048Encoder.class, "encode")
                .to("{{asterix.target.uri}}");
    }
}
