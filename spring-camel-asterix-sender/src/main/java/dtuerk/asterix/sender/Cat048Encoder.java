package dtuerk.asterix.sender;

import java.nio.ByteBuffer;

/**
 * Kodiert einen {@link Cat048Plot} als ASTERIX-Datenblock der Kategorie 048.
 *
 * <p>Aufbau des erzeugten Blocks (18 Byte):</p>
 * <pre>
 *   Offset  Laenge  Inhalt
 *   0       1       CAT   = 48
 *   1       2       LEN   = 18 (Gesamtlaenge inkl. CAT und LEN)
 *   3       1       FSPEC = 0xFC -> Data Items 1 bis 6 vorhanden, FX = 0
 *   4       2       I048/010 Data Source Identifier (SAC, SIC)
 *   6       3       I048/140 Time of Day
 *   9       1       I048/020 Target Report Descriptor
 *   10      4       I048/040 Measured Position in Polar Co-ordinates (RHO, THETA)
 *   14      2       I048/070 Mode-3/A Code
 *   16      2       I048/090 Flight Level
 * </pre>
 *
 * <p>Der FSPEC folgt der Standard-UAP von CAT 048: Bit 8 steht fuer FRN 1 (I048/010),
 * Bit 7 fuer FRN 2 (I048/140) usw.; Bit 1 ist das FX-Bit, das ein weiteres FSPEC-Oktett
 * ankuendigt. Da hier nur die ersten sechs Items gesendet werden, reicht ein Oktett:
 * 1111 1100.</p>
 */
public final class Cat048Encoder {

    /** ASTERIX-Kategorie fuer Monoradar-Zielberichte. */
    public static final int CATEGORY = 48;

    /** Laenge des hier erzeugten Datenblocks in Byte. */
    public static final int BLOCK_LENGTH = 18;

    /** Data Items 1 bis 6 vorhanden, kein weiteres FSPEC-Oktett. */
    static final int FSPEC = 0b1111_1100;

    /** I048/020: TYP = 011 (Single SSR + PSR), keine weiteren Extents (FX = 0). */
    static final int TARGET_REPORT_DESCRIPTOR = 0b011_0_0_0_0_0;

    private static final int SECONDS_PER_DAY = 24 * 60 * 60;

    /** I048/140 zaehlt in Schritten von 1/128 Sekunde. */
    private static final double TOD_UNITS_PER_SECOND = 128.0;

    /** I048/040 RHO zaehlt in Schritten von 1/256 NM. */
    private static final double RHO_UNITS_PER_NM = 256.0;

    /** I048/040 THETA verteilt 360 Grad auf 16 Bit. */
    private static final double THETA_UNITS_PER_DEGREE = 65536.0 / 360.0;

    /** I048/090 zaehlt in Schritten von 1/4 FL. */
    private static final double FL_UNITS_PER_LEVEL = 4.0;

    private Cat048Encoder() {
    }

    public static byte[] encode(Cat048Plot plot) {
        ByteBuffer block = ByteBuffer.allocate(BLOCK_LENGTH);

        // Datenblock-Kopf: Kategorie und Gesamtlaenge.
        block.put((byte) CATEGORY);
        block.putShort((short) BLOCK_LENGTH);
        block.put((byte) FSPEC);

        // I048/010 Data Source Identifier
        block.put((byte) plot.sac());
        block.put((byte) plot.sic());

        // I048/140 Time of Day: 24 Bit, LSB = 1/128 s seit Mitternacht
        int timeOfDay = encodeTimeOfDay(plot.timeOfDay().toSecondOfDay()
                + plot.timeOfDay().getNano() / 1_000_000_000.0);
        block.put((byte) (timeOfDay >> 16));
        block.put((byte) (timeOfDay >> 8));
        block.put((byte) timeOfDay);

        // I048/020 Target Report Descriptor
        block.put((byte) TARGET_REPORT_DESCRIPTOR);

        // I048/040 Measured Position in Polar Co-ordinates
        block.putShort((short) encodeRange(plot.rangeNauticalMiles()));
        block.putShort((short) encodeAzimuth(plot.azimuthDegrees()));

        // I048/070 Mode-3/A Code: V, G, L und Spare bleiben 0, darunter 12 Bit Oktalcode
        block.putShort((short) encodeMode3a(plot.mode3aCodeOctal()));

        // I048/090 Flight Level: V und G bleiben 0, darunter 14 Bit im Zweierkomplement
        block.putShort((short) encodeFlightLevel(plot.flightLevel()));

        return block.array();
    }

    static int encodeTimeOfDay(double secondsOfDay) {
        double wrapped = secondsOfDay % SECONDS_PER_DAY;
        return (int) Math.round(wrapped * TOD_UNITS_PER_SECOND) & 0xFFFFFF;
    }

    static int encodeRange(double rangeNauticalMiles) {
        long units = Math.round(rangeNauticalMiles * RHO_UNITS_PER_NM);
        return (int) Math.clamp(units, 0, 0xFFFF);
    }

    static int encodeAzimuth(double azimuthDegrees) {
        double wrapped = ((azimuthDegrees % 360.0) + 360.0) % 360.0;
        return (int) Math.round(wrapped * THETA_UNITS_PER_DEGREE) & 0xFFFF;
    }

    /**
     * Wandelt einen in Oktalschreibweise notierten Code (z. B. 7000) in die 12 Bit um,
     * die ASTERIX erwartet. Jede Ziffer belegt genau drei Bit.
     */
    static int encodeMode3a(int codeInOctalNotation) {
        int value = Integer.parseInt(Integer.toString(codeInOctalNotation), 8);
        return value & 0x0FFF;
    }

    static int encodeFlightLevel(double flightLevel) {
        return (int) Math.round(flightLevel * FL_UNITS_PER_LEVEL) & 0x3FFF;
    }
}
