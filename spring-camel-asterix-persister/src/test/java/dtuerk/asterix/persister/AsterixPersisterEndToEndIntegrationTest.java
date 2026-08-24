package dtuerk.asterix.persister;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vollstaendige Strecke des Persisters: echter Kafka-Broker, echtes PostgreSQL, beides aus
 * Containern. Der Test setzt eine erreichbare Docker-Umgebung voraus und schlaegt ohne sie fehl.
 */
@SpringBootTest(properties = {
        "asterix.kafka.topic=asterix-raw-it",
        "asterix.kafka.group-id=asterix-persister-it",
        "camel.main.main-run-controller=false"
})
@Testcontainers
class AsterixPersisterEndToEndIntegrationTest {

    private static final String TOPIC = "asterix-raw-it";

    private static final byte[] SAMPLE_BLOCK = {
            0x30, 0x00, 0x0B, (byte) 0xF5, 0x02, 0x0D, (byte) 0xE0, 0x1A, (byte) 0xB4, 0x2F, 0x08
    };

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("asterix")
            .withUsername("asterix")
            .withPassword("asterix");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Reicht die erst zur Laufzeit bekannten Adressen an die Konfiguration weiter. */
    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("asterix.kafka.brokers", KAFKA::getBootstrapServers);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void schreibtEinenBlockVomTopicNachPostgres() {
        try (KafkaProducer<String, byte[]> producer = neuerProducer()) {
            producer.send(new ProducerRecord<>(TOPIC, SAMPLE_BLOCK));
            producer.flush();
        }

        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    List<Map<String, Object>> zeilen = jdbcTemplate.queryForList(
                            "SELECT kafka_topic, payload FROM asterix_raw");
                    assertThat(zeilen).hasSize(1);
                    assertThat(zeilen.getFirst().get("kafka_topic")).isEqualTo(TOPIC);
                    assertThat((byte[]) zeilen.getFirst().get("payload")).isEqualTo(SAMPLE_BLOCK);
                });
    }

    private static KafkaProducer<String, byte[]> neuerProducer() {
        Properties props = new Properties();
        props.putAll(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName()));
        return new KafkaProducer<>(props);
    }
}
