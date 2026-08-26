package dtuerk.camel.common;

import java.util.HexFormat;

/**
 * Klassischer Hexdump (16 Byte je Zeile mit Offset) fuer die Log-Ausgabe.
 */
public final class HexDump {

    /** Ab dieser Groesse wird der Dump gekuerzt, damit das Log lesbar bleibt. */
    public static final int MAX_BYTES = 512;

    private static final int BYTES_PER_LINE = 16;
    private static final HexFormat HEX = HexFormat.ofDelimiter(" ").withUpperCase();

    private HexDump() {
    }

    public static String of(byte[] data) {
        return of(data, MAX_BYTES);
    }

    public static String of(byte[] data, int maxBytes) {
        if (data.length == 0) {
            return "<leer>";
        }
        int shown = Math.min(data.length, maxBytes);
        StringBuilder sb = new StringBuilder();
        for (int offset = 0; offset < shown; offset += BYTES_PER_LINE) {
            int end = Math.min(offset + BYTES_PER_LINE, shown);
            sb.append(String.format("%04X  ", offset))
                    .append(HEX.formatHex(data, offset, end));
            if (end < shown) {
                sb.append(System.lineSeparator());
            }
        }
        if (shown < data.length) {
            sb.append(System.lineSeparator())
                    .append("... ").append(data.length - shown).append(" weitere Byte gekuerzt");
        }
        return sb.toString();
    }
}
