package dtuerk.asterix.receiver;

import dtuerk.asterix.common.HexDump;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Liest ASTERIX-Datenbloecke von der konfigurierten Quelle ({@code asterix.source.uri}),
 * loggt den Body als Hexdump und legt ihn anschliessend unveraendert auf das Kafka-Topic
 * ({@code asterix.sink.uri}).
 *
 * <p>Bewusst findet weder Parsing noch Transformation statt: was im UDP-Datagramm ankommt,
 * geht Byte fuer Byte identisch an Kafka weiter.</p>
 */
@Component
public class AsterixReceiverRoute extends RouteBuilder {

    public static final String ROUTE_ID = "asterix-receiver";

    private static final Logger LOG = LoggerFactory.getLogger(AsterixReceiverRoute.class);

    @Override
    public void configure() {
        from("{{asterix.source.uri}}")
                .routeId(ROUTE_ID)
                // Vereinheitlicht nur den Typ (UDP liefert byte[], eine Datei einen Stream) -
                // der Inhalt bleibt unangetastet.
                .convertBodyTo(byte[].class)
                .process(this::logBody)
                // Body bleibt byte[]; dazu passt der ByteArraySerializer in der Sink-URI.
                .to("{{asterix.sink.uri}}");
    }

    private void logBody(Exchange exchange) {
        byte[] body = exchange.getMessage().getBody(byte[].class);
        LOG.info("ASTERIX-Block empfangen von {} - {} Byte{}{}",
                exchange.getMessage().getHeader("CamelNettyRemoteAddress", "n/a", String.class),
                body.length,
                System.lineSeparator(),
                HexDump.of(body));
    }
}
