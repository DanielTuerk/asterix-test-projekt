# spring-camel-asterix-receiver

Spring Boot 4.1 + Apache Camel 4.22 (Java 21). Eine Camel-Route liest ASTERIX-Datenbloecke
von einer konfigurierbaren Quelle ein, loggt den Body als Hexdump und legt ihn anschliessend
unveraendert auf ein Kafka-Topic.

## Aufbau

| Datei | Zweck |
| --- | --- |
| `AsterixReceiverApplication` | Spring-Boot-Einstiegspunkt (kein Web-Server) |
| `AsterixReceiverRoute` | die Camel-Route: `from(udp) -> loggen -> to(kafka)` |
| `application.yaml` | Quelle (UDP-Unicast, UDP-Multicast oder Datei) und Kafka-Ziel |

Der `HexDump` liegt im Modul [asterix-common](../common), weil der Persister ihn
ebenfalls benutzt.

Die Route in Kurzform:

```java
from("{{asterix.source.uri}}")
        .convertBodyTo(byte[].class)
        .process(this::logBody)
        .to("{{asterix.sink.uri}}");
```

Quelle **und** Ziel stecken hinter Properties. Dadurch laesst sich dieselbe Route gegen UDP,
Multicast, eine Datei oder im Test gegen `direct:` und `mock:` betreiben.

Weder Parsing noch Transformation: was im Datagramm ankommt, geht Byte fuer Byte identisch
an Kafka. `convertBodyTo(byte[].class)` vereinheitlicht nur den Typ (UDP liefert `byte[]`,
eine Datei einen Stream), nicht den Inhalt.

## Starten

Alle Befehle vom Wurzelverzeichnis des Projekts aus. Vorher sollte die Infrastruktur laufen
(Modul [asterix-infra](../asterix-infra)), sonst findet der Kafka-Producer keinen Broker.

```powershell
.\mvnw.cmd -pl spring-camel-asterix-receiver spring-boot:run
```

Die App lauscht dann per UDP auf `0.0.0.0:8600` und schreibt nach `localhost:9092`,
Topic `asterix-raw`.

## Testdaten erzeugen

Das Schwestermodul `spring-camel-asterix-sender` erzeugt fortlaufend echte CAT-048-Bloecke
und schickt sie an Port 8600. Fuer einen schnellen Einzelschuss reicht auch PowerShell:

```powershell
$udp   = New-Object System.Net.Sockets.UdpClient
$bytes = [byte[]](0x30,0x00,0x0B,0xF5,0x02,0x0D,0xE0,0x1A,0xB4,0x2F,0x08)
$udp.Send($bytes, $bytes.Length, "127.0.0.1", 8600)
$udp.Close()
```

Erwartete Log-Ausgabe:

```
ASTERIX-Block empfangen von /127.0.0.1:62703 - 11 Byte
0000  30 00 0B F5 02 0D E0 1A B4 2F 08
```

Die ersten drei Byte sind bereits der ASTERIX-Header: `30` = CAT 048 (dezimal 48),
`00 0B` = LEN 11 Byte fuer den gesamten Datenblock.

### Wenn nichts ankommt

Unter Windows duerfen sich mehrere Prozesse denselben UDP-Port teilen, das Datagramm
bekommt aber nur einer davon. Laeuft der Empfaenger noch aus einem frueheren Start
(z. B. in IntelliJ), erhaelt der neue Prozess nichts, ohne dass eine Fehlermeldung
erscheint. Wer den Port belegt, zeigt:

```powershell
Get-NetUDPEndpoint -LocalPort 8600 | Select-Object LocalAddress, OwningProcess
```

## Quelle umstellen

In `src/main/resources/application.yaml` sind drei Varianten hinterlegt:

* **UDP-Unicast** (Default) - `netty:udp://0.0.0.0:8600?sync=false&udpByteArrayCodec=true`
* **UDP-Multicast** - Host auf die Gruppenadresse setzen und `networkInterface=<Name>` angeben
* **Datei** - `file:data/asterix?...` fuer aufgezeichnete Bloecke, wenn kein Live-Feed da ist

`udpByteArrayCodec=true` ist nicht optional: ohne diese Option versucht der Netty-Consumer,
die Daten per Java-Serialisierung zu dekodieren, und scheitert an rohen ASTERIX-Bytes.

## Ziel: Kafka

```yaml
asterix:
  kafka:
    brokers: "localhost:9092"
    topic: "asterix-raw"
  sink:
    uri: "kafka:${asterix.kafka.topic}?brokers=${asterix.kafka.brokers}&valueSerializer=org.apache.kafka.common.serialization.ByteArraySerializer"
```

Zwei Punkte lohnen den Blick:

* Der `ByteArraySerializer` ist Pflicht. Camels Default ist der `StringSerializer` - der
  wuerde die Rohbytes durch die Zeichensatz-Konvertierung schicken und beschaedigen.
* Die `${...}` loest bereits Spring auf; Camel bekommt die fertige URI zu sehen. So stehen
  Broker und Topic nur einmal in der Datei und lassen sich einzeln ueberschreiben.

Anderer Broker ohne Aenderung an der yaml:

```powershell
.\mvnw.cmd -pl spring-camel-asterix-receiver spring-boot:run `
  "-Dspring-boot.run.arguments=--asterix.kafka.brokers=localhost:29092"
```

## Tests

```powershell
.\mvnw.cmd -pl spring-camel-asterix-receiver test
```

| Test | Was er prueft | Docker noetig |
| --- | --- | --- |
| `AsterixReceiverRouteTest` | Quelle `direct:`, Ziel `mock:` - der Block kommt unveraendert am Ziel an | nein |
| `AsterixReceiverKafkaIntegrationTest` | echter Broker aus dem Container; gelesen wird mit einem normalen `KafkaConsumer` | ja (sonst schlaegt er fehl) |

## Naechste Schritte

1. Header parsen: CAT + LEN aus den ersten drei Byte lesen.
2. UDP-Datagramme enthalten oft mehrere Datenbloecke hintereinander - anhand von LEN splitten.
3. FSPEC auswerten und die Data Items der jeweiligen Kategorie dekodieren.
