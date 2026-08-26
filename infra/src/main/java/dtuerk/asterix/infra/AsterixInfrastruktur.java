package dtuerk.asterix.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Startet die gemeinsame Infrastruktur der Spielwiese: einen Kafka-Broker und eine
 * PostgreSQL-Instanz, beides per Testcontainers.
 *
 * <p>Warum ein eigenes Modul? Receiver und Persister muessen denselben Broker sehen.
 * Wuerde jede App ihre eigenen Container hochziehen, bekaeme jede einen eigenen Broker
 * auf einem zufaelligen Port - die Strecke waere unterbrochen. Darum laufen die Container
 * hier einmal zentral auf festen Ports, und beide Apps zeigen per Default darauf.</p>
 *
 * <p>Beenden mit Strg+C; Testcontainers raeumt die Container beim Herunterfahren auf.</p>
 */
public final class AsterixInfrastruktur {

    private static final Logger LOG = LoggerFactory.getLogger(AsterixInfrastruktur.class);

    /** Feste Host-Ports, damit die Apps sie in der application.yaml fest eintragen koennen. */
    private static final int KAFKA_PORT = Integer.getInteger("asterix.infra.kafka-port", 9092);
    private static final int POSTGRES_PORT = Integer.getInteger("asterix.infra.postgres-port", 5432);

    private static final String DATENBANK = "asterix";
    private static final String BENUTZER = "asterix";
    private static final String PASSWORT = "asterix";

    private static final int KAFKA_UI_PORT = Integer.getInteger("asterix.infra.kafka-ui-port", 8080);

    private AsterixInfrastruktur() {
    }

    public static void main(String[] args) throws InterruptedException {
        Network network = Network.newNetwork();

        KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");
        // setPortBindings statt der sonst zufaelligen Port-Zuordnung von Testcontainers.
        kafka.setPortBindings(List.of(KAFKA_PORT + ":9092"));

        GenericContainer<?> kafkaUi =
                new GenericContainer<>("ghcr.io/kafbat/kafka-ui:latest")
                        .withNetwork(network)
                        .withEnv("KAFKA_CLUSTERS_0_NAME", "Asterix lokal")
                        .withEnv("KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS",
                                "kafka:19092")
                        .dependsOn(kafka);
        kafkaUi.setPortBindings(List.of(KAFKA_UI_PORT + ":8080"));

        PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
                .withDatabaseName(DATENBANK)
                .withUsername(BENUTZER)
                .withPassword(PASSWORT);
        postgres.setPortBindings(List.of(POSTGRES_PORT + ":5432"));

        LOG.info("Starte Kafka und PostgreSQL - beim ersten Mal dauert das Laden der Images einen Moment.");
        Startables.deepStart(postgres, kafka, kafkaUi).join();

        LOG.info("""

                        Infrastruktur laeuft:
                          Kafka      : {}
                          Kafka UI   : {}
                          PostgreSQL : {}
                                       Benutzer {} / Passwort {}
                          psql       : docker exec -it {} psql -U {} -d {}

                        Beenden mit Strg+C.""",
                kafka.getBootstrapServers(),
                kafkaUi.getContainerName(),
                postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword(),
                postgres.getContainerName().replaceFirst("^/", ""), BENUTZER, DATENBANK);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> LOG.info("Infrastruktur wird beendet.")));

        // Blockiert bis Strg+C; die Container stoppt Testcontainers beim Herunterfahren.
        new CountDownLatch(1).await();
    }
}
