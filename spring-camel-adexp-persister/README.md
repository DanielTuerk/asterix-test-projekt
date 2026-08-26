# spring-camel-adexp-persister

Spring Boot 4.1 + Apache Camel 4.22 (Java 21). Eine Camel-Route liest die Rohbloecke vom Kafka-Topic, loggt sie als
Hexdump und schreibt sie unveraendert nach PostgreSQL.

## Starten

Erst die Infrastruktur (Modul `adexp-infra`), dann:

```powershell
.\mvnw.cmd -pl spring-camel-adexp-persister spring-boot:run
```