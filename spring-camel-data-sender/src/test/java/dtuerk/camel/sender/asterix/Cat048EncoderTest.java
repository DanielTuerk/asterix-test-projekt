package dtuerk.camel.sender.asterix;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class Cat048EncoderTest {

    private static final HexFormat HEX = HexFormat.ofDelimiter(" ").withUpperCase();

    @Test
    void kodiertVollstaendigenDatenblock() {
        Cat048Plot plot = new Cat048Plot(
                25,                      // SAC
                11,                      // SIC
                LocalTime.of(12, 0, 0),  // 43200 s * 128 = 0x546000
                100.5,                   // RHO: 100,5 NM * 256 = 0x6480
                45.0,                    // THETA: 45 Grad = 0x2000
                7000,                    // Mode-3/A oktal 7000 = 0x0E00
                300.0);                  // FL300 * 4 = 0x04B0

        byte[] block = Cat048Encoder.encode(plot);

        assertThat(HEX.formatHex(block))
                .isEqualTo("30 00 12 FC 19 0B 54 60 00 60 64 80 20 00 0E 00 04 B0");
    }

    @Test
    void datenblockTraegtKategorieUndLaengeImKopf() {
        byte[] block = Cat048Encoder.encode(
                new Cat048Plot(1, 2, LocalTime.MIDNIGHT, 0.0, 0.0, 0, 0.0));

        assertThat(block).hasSize(Cat048Encoder.BLOCK_LENGTH);
        assertThat(block[0]).isEqualTo((byte) Cat048Encoder.CATEGORY);
        assertThat((block[1] & 0xFF) << 8 | (block[2] & 0xFF)).isEqualTo(Cat048Encoder.BLOCK_LENGTH);
    }

    @Test
    void azimut360EntsprichtAzimut0() {
        assertThat(Cat048Encoder.encodeAzimuth(360.0)).isEqualTo(Cat048Encoder.encodeAzimuth(0.0));
        assertThat(Cat048Encoder.encodeAzimuth(-90.0)).isEqualTo(Cat048Encoder.encodeAzimuth(270.0));
    }

    @Test
    void interpretiertMode3aCodeAlsOktalzahl() {
        assertThat(Cat048Encoder.encodeMode3a(7000)).isEqualTo(0b111_000_000_000);
        assertThat(Cat048Encoder.encodeMode3a(1234)).isEqualTo(0b001_010_011_100);
    }

    @Test
    void begrenztEntfernungAufWertebereich() {
        assertThat(Cat048Encoder.encodeRange(1000.0)).isEqualTo(0xFFFF);
        assertThat(Cat048Encoder.encodeRange(-5.0)).isZero();
    }
}
