# asterix-common

Kleines Modul fuer das, was mehrere Apps brauchen. Bewusst ohne Spring- und
Camel-Abhaengigkeiten: reines Java.

| Klasse | Zweck |
| --- | --- |
| `HexDump` | formatiert `byte[]` als klassischen Hexdump (16 Byte/Zeile mit Offset) |

`HexDump` lag urspruenglich im Receiver. Seit auch der Persister die Rohbloecke loggt,
steht die Klasse hier - einmal, statt zweimal kopiert.

```
0000  30 00 12 FC 19 0B 47 25 4A 60 77 C0 24 44 0E 00
0010  04 B0
```

Ab `HexDump.MAX_BYTES` (512) wird gekuerzt, damit ein grosses Datagramm das Log nicht
unlesbar macht.
