package dtuerk.camel.receiver;

import org.apache.camel.ProducerTemplate;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Echte Strecke bis Kafka: der Broker kommt aus einem Container, nur die Quelle ist
 * auf {@code direct:} umgebogen. Der Test setzt eine erreichbare Docker-Umgebung voraus
 * und schlaegt ohne sie fehl.
 */
@SpringBootTest(properties = {
        "asterix.source.uri=direct:asterix-source",
        "asterix.kafka.topic=asterix-raw-it",
        "camel.main.main-run-controller=false"
})
@Testcontainers
class AsterixReceiverKafkaIntegrationTest {

    private static final String TOPIC = "asterix-raw-it";

    private static final byte[] SAMPLE_BLOCK = {
            0x30, 0x00, 0x0B, (byte) 0xF5, 0x02, 0x0D, (byte) 0xE0, 0x1A, (byte) 0xB4, 0x2F, 0x08
    };

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    @Autowired
    private ProducerTemplate producerTemplate;

    /** Reicht die erst zur Laufzeit bekannte Broker-Adresse an die Sink-URI weiter. */
    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("asterix.kafka.brokers", KAFKA::getBootstrapServers);
    }

    @Test
    void schreibtDenRohblockAufDasTopic() {
        try (KafkaConsumer<String, byte[]> consumer = neuerConsumer()) {
            consumer.subscribe(List.of(TOPIC));
            // Erster poll(): stellt die Zuordnung zur Partition her.
            consumer.poll(Duration.ofSeconds(1));

            producerTemplate.sendBody("direct:asterix-source", SAMPLE_BLOCK);

            ConsumerRecord<String, byte[]> record = warteAufDatensatz(consumer);
            assertThat(record).isNotNull();
            assertThat(record.value()).isEqualTo(SAMPLE_BLOCK);
        }
    }

    private ConsumerRecord<String, byte[]> warteAufDatensatz(KafkaConsumer<String, byte[]> consumer) {
        long ende = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < ende) {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(1));
            for (ConsumerRecord<String, byte[]> record : records) {
                return record;
            }
        }
        return null;
    }

    private static KafkaConsumer<String, byte[]> neuerConsumer() {
        Properties props = new Properties();
        props.putAll(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "asterix-receiver-it",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName()));
        return new KafkaConsumer<>(props);
    }
}
