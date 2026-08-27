package dtuerk.camel.persister;

import com.fasterxml.jackson.databind.ObjectMapper;
import dtuerk.camel.common.FlightPlan;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;

@Component
public class IfplPersisterRoute extends RouteBuilder {

    public static final String ROUTE_ID = "adexp-persister";

    private static final Logger LOG = LoggerFactory.getLogger(IfplPersisterRoute.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void configure() {
        from("{{adexp.source.ifpl.uri}}")
                .routeId(ROUTE_ID)
                // Kafka liefert den Wert dank ByteArrayDeserializer schon als byte[].
                .convertBodyTo(byte[].class)
                .process(this::logBody)
                // Baut die Parameter-Map fuer den INSERT; die Rohbytes wandern unveraendert
                // als Wert von "payload" mit.
                .process(this::toSqlParameters)
                .to("{{adexp.sink.ifpl.uri}}")
                // check result
                .choice()
                .when(header("CamelSqlUpdateCount").isEqualTo(0))
                .log(LoggingLevel.WARN, "IFPL ${body[ifplid]} existiert bereits")
                .otherwise()
                .log(LoggingLevel.INFO, "IFPL ${body[ifplid]} wurde gespeichert")
                .end();
    }

    private void logBody(Exchange exchange) {
        var body = exchange.getMessage().getBody(String.class);
        LOG.info("adexp aus Kafka gelesen (topic={}, partition={}, offset={}) - {}",
                exchange.getMessage().getHeader(KafkaConstants.TOPIC, "n/a", String.class),
                exchange.getMessage().getHeader(KafkaConstants.PARTITION, -1, Integer.class),
                exchange.getMessage().getHeader(KafkaConstants.OFFSET, -1L, Long.class),
                body);
    }

    /**
     * Camel-SQL loest die benannten Parameter {@code :#name} zuerst gegen eine Map im Body auf,
     * erst danach gegen die Header. Deshalb wird der Body hier zur Parameter-Map.
     */
    private void toSqlParameters(Exchange exchange) throws IOException {
        final var rawPayload = exchange.getMessage().getBody(byte[].class);
        final var flightPlan =
                objectMapper.readValue(rawPayload, FlightPlan.class);

        final var parameters = new HashMap<String, Object>();
        parameters.put("ifpl_id", flightPlan.ifplId());
        parameters.put("title", flightPlan.title());
        parameters.put("arcid", flightPlan.arcid());
        parameters.put("adep", flightPlan.adep());
        parameters.put("ades", flightPlan.ades());
        parameters.put("arctyp", flightPlan.arctyp());
        parameters.put("eobd", flightPlan.eobd());
        parameters.put("eobt", flightPlan.eobt());
        parameters.put("rfl", flightPlan.rfl());
        parameters.put("speed", flightPlan.speed());
        parameters.put("route", flightPlan.route());
        parameters.put("origin_fac", flightPlan.originFac());
        parameters.put(
                "addresses",
                objectMapper.writeValueAsString(flightPlan.addresses())
        );
        parameters.put(
                "route_points",
                objectMapper.writeValueAsString(flightPlan.routePoints())
        );
        parameters.put(
                "eet_per_fir",
                objectMapper.writeValueAsString(flightPlan.eetPerFir())
        );
        parameters.put("raw_payload", rawPayload);

        exchange.getMessage().setBody(parameters);
    }
}
