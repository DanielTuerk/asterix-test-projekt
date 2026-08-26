# camel-test-projekt

Spielwiese zur Einarbeitung in **EUROCONTROL ASTERIX** und **Apache Camel**.
Maven-Reactor mit Spring Boot 4.1, Camel 4.22 und Java 21.

## Die Strecke

```
Sender ──UDP:8600──> Receiver ──Kafka-Topic "asterix-raw"──> Persister ──JDBC──> PostgreSQL
```

Auf dem ganzen Weg wird der ASTERIX-Datenblock **nicht** geparst und **nicht** transformiert.
Was der Sender erzeugt, liegt am Ende Byte fuer Byte identisch in der Spalte `payload`.
Dekodieren ist der naechste Schritt, nicht dieser.

## Module

| Modul | Zweck |
| --- | --- |
| [asterix-common](asterix-common) | gemeinsame Hilfsklassen (aktuell der `HexDump` fuers Logging) |
| [asterix-infra](asterix-infra) | startet Kafka und PostgreSQL per Testcontainers auf festen Ports |
| [spring-camel-asterix-sender](spring-camel-asterix-sender) | erzeugt im Takt CAT-048-Datenbloecke und sendet sie per UDP |
| [spring-camel-asterix-receiver](spring-camel-asterix-receiver) | empfaengt sie per UDP, loggt den Hexdump und legt sie roh auf Kafka |
| [spring-camel-asterix-persister](spring-camel-asterix-persister) | liest das Topic, loggt den Hexdump und schreibt die Rohbytes nach PostgreSQL |

Jedes Modul hat ein eigenes README mit Details.

## Voraussetzung: Docker

Kafka und PostgreSQL kommen aus Containern - dafuer muss eine Docker-Umgebung erreichbar sein
(Rancher Desktop oder Docker Desktop sind unter Windows der uebliche Weg).
Ohne Docker laufen Sender und Receiver zwar, aber der Weg ab Kafka bleibt tot und die
Container-Tests schlagen fehl - sie werden bewusst nicht mehr stillschweigend uebersprungen.

Pruefen:

```powershell
docker info
```

## Bauen

Alles auf einmal:

```powershell
.\mvnw.cmd verify
```

Einzelnes Modul:

```powershell
.\mvnw.cmd -pl spring-camel-asterix-sender verify
```

## Ausprobieren

Vier Konsolen, jeweils im Wurzelverzeichnis, in dieser Reihenfolge:

```powershell
# 1. Infrastruktur (Kafka auf 9092, PostgreSQL auf 5432) - laeuft bis Strg+C
.\mvnw.cmd -pl asterix-infra exec:java
```

```powershell
# 2. Persister: liest das Topic und schreibt nach PostgreSQL
.\mvnw.cmd -pl spring-camel-asterix-persister spring-boot:run
```

```powershell
# 3. Receiver: lauscht auf UDP 8600 und legt die Bloecke auf Kafka
.\mvnw.cmd -pl spring-camel-asterix-receiver spring-boot:run
```

```powershell
# 4. Sender: erzeugt im Sekundentakt CAT-048-Bloecke
.\mvnw.cmd -pl spring-camel-asterix-sender spring-boot:run
```

Im Receiver erscheint dann im Sekundentakt:

```
ASTERIX-Block empfangen von /127.0.0.1:57662 - 18 Byte
0000  30 00 12 FC 19 0B 47 25 4A 60 77 C0 24 44 0E 00
0010  04 B0
```

und kurz darauf im Persister derselbe Block, diesmal mit Kafka-Koordinaten:

```
ASTERIX-Block aus Kafka gelesen (topic=asterix-raw, partition=0, offset=17) - 18 Byte
0000  30 00 12 FC 19 0B 47 25 4A 60 77 C0 24 44 0E 00
0010  04 B0
```

Nachsehen, was in der Datenbank steht:

```powershell
docker exec -it $(docker ps --filter ancestor=postgres:17-alpine --format "{{.Names}}") `
  psql -U asterix -d asterix -c "SELECT id, stored_at, kafka_offset, length(payload) FROM asterix_raw ORDER BY id DESC LIMIT 5;"
```

## Aufbau des Reactors

Das Wurzel-POM ist zugleich Aggregator und Parent:

* erbt von `spring-boot-starter-parent`, damit die Spring-Boot-Versionen zentral stehen
  (dazu gehoert auch die Version von Testcontainers),
* importiert das `camel-spring-boot-bom` - dadurch brauchen die Module bei den
  `camel-*-starter` keine Versionen anzugeben,
* legt `java.version` und `camel.version` einmal fest,
* stellt die Testabhaengigkeiten bereit, die jedes Modul braucht.

Die Modul-POMs enthalten daher nur noch ihre eigenen fachlichen Abhaengigkeiten.
Der Maven-Wrapper liegt ausschliesslich im Wurzelverzeichnis.

## Naechste Schritte

1. Header parsen: CAT + LEN aus den ersten drei Byte lesen.
2. UDP-Datagramme enthalten oft mehrere Datenbloecke hintereinander - anhand von LEN splitten.
3. FSPEC auswerten und die Data Items der jeweiligen Kategorie dekodieren.
4. Erst danach lohnt eine fachliche Tabelle neben `asterix_raw`.
