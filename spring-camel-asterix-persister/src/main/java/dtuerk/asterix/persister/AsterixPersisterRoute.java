package dtuerk.asterix.persister;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import dtuerk.asterix.common.HexDump;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Liest die Rohbloecke vom Kafka-Topic ({@code asterix.source.uri}), loggt sie als Hexdump
 * und schreibt sie anschliessend unveraendert nach PostgreSQL ({@code asterix.sink.uri}).
 *
 * <p>Der Datensatz selbst wird nicht angefasst: in der Spalte {@code payload} landen exakt
 * die Bytes, die der Receiver per UDP empfangen hat. Alles Weitere (Zeitstempel, Topic,
 * Partition, Offset) ist reine Herkunftsinformation.</p>
 */
@Component
public class AsterixPersisterRoute extends RouteBuilder {

    public static final String ROUTE_ID = "asterix-persister";

    private static final Logger LOG = LoggerFactory.getLogger(AsterixPersisterRoute.class);

    @Override
    public void configure() {
        from("{{asterix.source.uri}}")
                .routeId(ROUTE_ID)
                // Kafka liefert den Wert dank ByteArrayDeserializer schon als byte[].
                .convertBodyTo(byte[].class)
                .process(this::logBody)
                // Baut die Parameter-Map fuer den INSERT; die Rohbytes wandern unveraendert
                // als Wert von "payload" mit.
                .process(this::toSqlParameters)
                .to("{{asterix.sink.uri}}");
    }

    private void logBody(Exchange exchange) {
        byte[] body = exchange.getMessage().getBody(byte[].class);
        LOG.info("ASTERIX-Block aus Kafka gelesen (topic={}, partition={}, offset={}) - {} Byte{}{}",
                exchange.getMessage().getHeader(KafkaConstants.TOPIC, "n/a", String.class),
                exchange.getMessage().getHeader(KafkaConstants.PARTITION, -1, Integer.class),
                exchange.getMessage().getHeader(KafkaConstants.OFFSET, -1L, Long.class),
                body.length,
                System.lineSeparator(),
                HexDump.of(body));
    }

    /**
     * Camel-SQL loest die benannten Parameter {@code :#name} zuerst gegen eine Map im Body auf,
     * erst danach gegen die Header. Deshalb wird der Body hier zur Parameter-Map.
     */
    private void toSqlParameters(Exchange exchange) {
        byte[] payload = exchange.getMessage().getBody(byte[].class);
        Long kafkaTimestamp = exchange.getMessage().getHeader(KafkaConstants.TIMESTAMP, Long.class);

        Map<String, Object> parameter = new LinkedHashMap<>();
        parameter.put("stored_at", Timestamp.from(Instant.now()));
        parameter.put("kafka_timestamp", kafkaTimestamp == null
                ? null
                : Timestamp.from(Instant.ofEpochMilli(kafkaTimestamp)));
        parameter.put("kafka_topic", exchange.getMessage().getHeader(KafkaConstants.TOPIC, String.class));
        parameter.put("kafka_partition", exchange.getMessage().getHeader(KafkaConstants.PARTITION, Integer.class));
        parameter.put("kafka_offset", exchange.getMessage().getHeader(KafkaConstants.OFFSET, Long.class));
        parameter.put("payload", payload);

        exchange.getMessage().setBody(parameter);
    }
}
