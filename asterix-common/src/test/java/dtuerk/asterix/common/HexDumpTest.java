package dtuerk.asterix.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HexDumpTest {

    @Test
    void formatiertKurzenBlockInEinerZeile() {
        byte[] block = {0x30, 0x00, 0x0B, (byte) 0xFF};

        assertThat(HexDump.of(block)).isEqualTo("0000  30 00 0B FF");
    }

    @Test
    void bricht16ByteProZeileUm() {
        byte[] block = new byte[20];
        for (int i = 0; i < block.length; i++) {
            block[i] = (byte) i;
        }

        assertThat(HexDump.of(block).lines()).containsExactly(
                "0000  00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F",
                "0010  10 11 12 13");
    }

    @Test
    void kuerztZuLangeBloecke() {
        byte[] block = new byte[40];

        String dump = HexDump.of(block, 16);

        assertThat(dump.lines()).containsExactly(
                "0000  00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00",
                "... 24 weitere Byte gekuerzt");
    }

    @Test
    void meldetLeerenBlock() {
        assertThat(HexDump.of(new byte[0])).isEqualTo("<leer>");
    }
}
