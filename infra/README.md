# asterix-infra

Startet die gemeinsame Infrastruktur der Spielwiese per **Testcontainers**: einen
Kafka-Broker und eine PostgreSQL-Instanz - beides auf festen Ports.

## Warum ein eigenes Modul?

Testcontainers wird sonst pro Testklasse benutzt und vergibt zufaellige Host-Ports.
Receiver und Persister muessen aber **denselben** Broker sehen. Wuerde jede App ihre
eigenen Container hochziehen, haette jede einen eigenen Kafka auf einem eigenen Port und
die Strecke waere unterbrochen. Darum laufen die Container hier einmal zentral, und beide
Apps zeigen per Default darauf.

| Dienst | Adresse | Zugangsdaten |
| --- | --- | --- |
| Kafka | `localhost:9092` | - |
| PostgreSQL | `jdbc:postgresql://localhost:5432/asterix` | `asterix` / `asterix` |

## Starten

```powershell
.\mvnw.cmd -pl asterix-infra exec:java
```

Beim ersten Mal dauert es einen Moment, bis die Images geladen sind. Danach steht in der
Konsole, unter welchen Adressen die Dienste erreichbar sind. Beenden mit **Strg+C** -
Testcontainers raeumt die Container beim Herunterfahren selbst auf.

Andere Ports (z. B. wenn lokal schon ein PostgreSQL auf 5432 lauscht):

```powershell
.\mvnw.cmd -pl asterix-infra exec:java "-Dasterix.infra.postgres-port=55432"
```

Dann brauchen die Apps den passenden Wert:

```powershell
.\mvnw.cmd -pl spring-camel-asterix-persister spring-boot:run `
  "-Dspring-boot.run.arguments=--spring.datasource.url=jdbc:postgresql://localhost:55432/asterix"
```

## Voraussetzung

Eine erreichbare Docker-Umgebung (`docker info` muss funktionieren). Unter Windows ist
Rancher Desktop oder Docker Desktop der uebliche Weg. Ein Docker, das nur
innerhalb der WSL-Distribution laeuft, sieht die JVM auf der Windows-Seite nicht -
in dem Fall entweder Rancher/Docker Desktop nutzen oder `DOCKER_HOST` auf den WSL-Daemon zeigen
lassen.

## Nuetzliche Handgriffe

Topic-Inhalt ansehen:

```powershell
docker exec -it $(docker ps --filter ancestor=apache/kafka:3.9.1 --format "{{.Names}}") `
  /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic asterix-raw --from-beginning
```

In die Datenbank schauen:

```powershell
docker exec -it $(docker ps --filter ancestor=postgres:17-alpine --format "{{.Names}}") `
  psql -U asterix -d asterix -c "SELECT count(*) FROM asterix_raw;"
```
