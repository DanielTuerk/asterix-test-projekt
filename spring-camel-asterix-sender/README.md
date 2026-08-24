# spring-camel-asterix-sender

Gegenstueck zu `spring-camel-asterix-receiver`: erzeugt im festen Takt einen ASTERIX-Datenblock
der Kategorie 048 (Monoradar-Zielbericht) und schickt ihn per UDP an den Empfaenger.

## Aufbau

| Datei | Zweck |
| --- | --- |
| `AsterixSenderApplication` | Spring-Boot-Einstiegspunkt (kein Web-Server) |
| `AsterixSenderRoute` | die Camel-Route: `timer:` -> Plot erzeugen -> kodieren -> `netty:udp` |
| `SimulatedTarget` | ein Ziel, das sich langsam im Kreis auf das Radar zubewegt |
| `Cat048Plot` | fachlicher Plot: Zeit, RHO, THETA, Mode-3/A, Flight Level |
| `Cat048Encoder` | kodiert einen Plot als 18 Byte langen CAT-048-Datenblock |
| `AsterixSenderProperties` | SAC/SIC des Radars und Eigenschaften des Ziels |

Die Route besteht aus vier Schritten - der Body wandert dabei von einem Java-Record zum
fertigen Byte-Array:

```java
from("timer:asterix-sender?period={{asterix.sender.period-millis}}")
        .routeId(ROUTE_ID)
        .bean(simulatedTarget, "nextPlot")   // Body: Cat048Plot
        .log(LoggingLevel.INFO, LOG, "Sende ${body}")
        .bean(Cat048Encoder.class, "encode") // Body: byte[]
        .to("{{asterix.target.uri}}");
```

## Aufbau des erzeugten Datenblocks

```
Offset  Laenge  Inhalt
0       1       CAT   = 48
1       2       LEN   = 18 (Gesamtlaenge inkl. CAT und LEN)
3       1       FSPEC = 0xFC -> Data Items 1 bis 6 vorhanden, FX = 0
4       2       I048/010 Data Source Identifier (SAC, SIC)
6       3       I048/140 Time of Day            (LSB = 1/128 s)
9       1       I048/020 Target Report Descriptor
10      4       I048/040 Measured Position      (RHO: 1/256 NM, THETA: 360/2^16 Grad)
14      2       I048/070 Mode-3/A Code          (12 Bit Oktalcode)
16      2       I048/090 Flight Level           (LSB = 1/4 FL)
```

Der FSPEC folgt der Standard-UAP von CAT 048: Bit 8 steht fuer FRN 1 (I048/010),
Bit 7 fuer FRN 2 (I048/140) usw.; Bit 1 ist das FX-Bit, das ein weiteres FSPEC-Oktett
ankuendigt. Fuer die ersten sechs Items reicht ein Oktett: `1111 1100`.

Beispielblock (SAC 25, SIC 11, 12:00:00 UTC, 100,5 NM, 45 Grad, Code 7000, FL300):

```
30 00 12 FC 19 0B 54 60 00 60 64 80 20 00 0E 00 04 B0
```

## Starten

Alle Befehle vom Wurzelverzeichnis des Projekts aus - erst den Empfaenger, dann in einer
zweiten Konsole den Sender:

```powershell
.\mvnw.cmd -pl spring-camel-asterix-receiver spring-boot:run
```

```powershell
.\mvnw.cmd -pl spring-camel-asterix-sender spring-boot:run
```

Der Sender loggt den fachlichen Plot, der Empfaenger die empfangenen Rohbytes:

```
Sende Cat048Plot[sac=25, sic=11, timeOfDay=10:07:06.581, rangeNauticalMiles=119.75, ...]
```
```
ASTERIX-Block empfangen von /127.0.0.1:57662 - 18 Byte
0000  30 00 12 FC 19 0B 47 25 4A 60 77 C0 24 44 0E 00
0010  04 B0
```

### Wenn nichts ankommt

Unter Windows duerfen sich mehrere Prozesse denselben UDP-Port teilen, das Datagramm
bekommt aber nur einer davon. Laeuft der Empfaenger noch aus einem frueheren Start
(z. B. in IntelliJ), erhaelt der neue Prozess nichts, ohne dass eine Fehlermeldung
erscheint. Wer den Port belegt, zeigt:

```powershell
Get-NetUDPEndpoint -LocalPort 8600 | Select-Object LocalAddress, OwningProcess
```

## Konfiguration

Alles in `src/main/resources/application.yaml`:

| Property | Default | Bedeutung |
| --- | --- | --- |
| `asterix.target.uri` | `netty:udp://127.0.0.1:8600?...` | wohin gesendet wird |
| `asterix.sender.period-millis` | `1000` | Takt der Plot-Erzeugung |
| `asterix.sender.sac` / `.sic` | `25` / `11` | Kennung des simulierten Radars |
| `asterix.sender.mode3a-code` | `7000` | Mode-3/A-Code des Ziels (oktal) |
| `asterix.sender.flight-level` | `300.0` | Flugflaeche des Ziels |

Zum Umstellen ohne Neubau reicht ein Kommandozeilenargument:

```powershell
.\mvnw.cmd -pl spring-camel-asterix-sender spring-boot:run "-Dspring-boot.run.arguments=--asterix.sender.period-millis=200"
```

## Tests

```powershell
.\mvnw.cmd -pl spring-camel-asterix-sender test
```

`Cat048EncoderTest` prueft die Kodierung Byte fuer Byte, `AsterixSenderRouteTest` biegt
`asterix.target.uri` auf ein `mock:`-Endpoint um, sodass kein Empfaenger laufen muss.

## Naechste Schritte

1. Mehrere Ziele gleichzeitig simulieren (mehrere Tracks mit eigener Track Number, I048/161).
2. Mehrere Datenbloecke in ein Datagramm packen - so kommt ASTERIX im Betrieb oft an.
3. Im Empfaenger den Gegenpart bauen: FSPEC auswerten und die Items wieder dekodieren.
