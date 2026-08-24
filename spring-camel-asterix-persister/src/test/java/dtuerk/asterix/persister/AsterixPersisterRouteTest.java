package dtuerk.asterix.persister;

import java.util.Map;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.kafka.KafkaConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die Route gegen eine In-Memory-Datenbank: Kafka ist durch {@code direct:} ersetzt,
 * PostgreSQL durch H2 im PostgreSQL-Modus. Laeuft ohne Docker.
 */
@SpringBootTest(properties = {
        "asterix.source.uri=direct:kafka-in",
        "spring.datasource.url=jdbc:h2:mem:asterix-persister-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "camel.main.main-run-controller=false"
})
class AsterixPersisterRouteTest {

    /** Beispielblock: CAT 048 (0x30), Laenge 11 Byte. */
    private static final byte[] SAMPLE_BLOCK = {
            0x30, 0x00, 0x0B, (byte) 0xF5, 0x02, 0x0D, (byte) 0xE0, 0x1A, (byte) 0xB4, 0x2F, 0x08
    };

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void schreibtDenRohblockUnveraendertInDieDatenbank() {
        producerTemplate.sendBodyAndHeaders("direct:kafka-in", SAMPLE_BLOCK, Map.of(
                KafkaConstants.TOPIC, "asterix-raw",
                KafkaConstants.PARTITION, 0,
                KafkaConstants.OFFSET, 42L,
                KafkaConstants.TIMESTAMP, 1_700_000_000_000L));

        Map<String, Object> zeile = jdbcTemplate.queryForMap(
                "SELECT kafka_topic, kafka_partition, kafka_offset, payload FROM asterix_raw");

        assertThat(zeile.get("kafka_topic")).isEqualTo("asterix-raw");
        assertThat(zeile.get("kafka_partition")).isEqualTo(0);
        assertThat(zeile.get("kafka_offset")).isEqualTo(42L);
        assertThat((byte[]) zeile.get("payload")).isEqualTo(SAMPLE_BLOCK);
    }
}
