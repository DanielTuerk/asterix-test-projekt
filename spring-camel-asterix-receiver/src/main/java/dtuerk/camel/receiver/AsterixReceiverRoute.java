package dtuerk.camel.receiver;

import dtuerk.camel.common.HexDump;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Liest ASTERIX-Datenbloecke von der konfigurierten Quelle ({@code asterix.source.uri}),
 * loggt den Body als Hexdump und legt ihn anschliessend unveraendert auf das Kafka-Topic
 * ({@code asterix.sink.uri}).
 *
 * <p>Bewusst findet weder Parsing noch Transformation statt: was im UDP-Datagramm ankommt,
 * geht Byte fuer Byte identisch an Kafka weiter.</p>
 */
@Component
public class AsterixReceiverRoute extends RouteBuilder {

    public static final String ROUTE_ID = "asterix-receiver";

    private static final Logger LOG = LoggerFactory.getLogger(AsterixReceiverRoute.class);

    @Override
    public void configure() {
        from("{{asterix.source.uri}}")
                .routeId(ROUTE_ID)
                // Vereinheitlicht nur den Typ (UDP liefert byte[], eine Datei einen Stream) -
                // der Inhalt bleibt unangetastet.
                .convertBodyTo(byte[].class)
                .process(this::logBody)
                // Body bleibt byte[]; dazu passt der ByteArraySerializer in der Sink-URI.
                .to("{{asterix.sink.uri}}");
    }

    private void logBody(Exchange exchange) {
        byte[] body = exchange.getMessage().getBody(byte[].class);
        LOG.info("ASTERIX-Block empfangen von {} - {} Byte{}{}",
                exchange.getMessage().getHeader("CamelNettyRemoteAddress", "n/a", String.class),
                body.length,
                System.lineSeparator(),
                HexDump.of(body));

        if (body.length == 18) {
            if (body[0] == 48) {
                // 1 und 2 ist LEN
                // 3 is FSPEC
                int sic = body[4];
                int sac = body[5];
                byte dataItem = body[6];

                var typ = TYP.from(((dataItem & 0xFF) >> 5) & 0x07);


                LOG.info("sic: {}, sac: {}, typ: {}", sic, sac, typ);
                if (isBitSet(dataItem, 0)) {
                    // Extension into first extend
                } else {
                }

                // I048/030 missing from sender
                var measured_Position = body[7];
            }
        }
    }

    public static boolean isBitNotSet(byte value, int bit) {
        return !isBitSet(value, bit);
    }

    public static boolean isBitSet(byte value, int bit) {
        return ((value & 0xFF) & (1 << bit)) != 0;
    }

    enum TYP {
        No_detection(0),
        Single_PSR_detection(1),
        Single_SSR_Detection(2),
        SSR_PSR_detection(3),
        Single_ModeS_AllCall(4),
        Single_ModeSRoll_Call(5),
        ModeS_AllCall_PSR(6),
        ModeS_RollCall_PSR(7);

        final int value;

        TYP(int value) {
            this.value = value;
        }

        public static TYP from(int value) {
            return switch (value) {
                case 0 -> No_detection;
                case 1 -> Single_PSR_detection;
                case 2 -> Single_SSR_Detection;
                case 3 -> SSR_PSR_detection;
                case 4 -> Single_ModeS_AllCall;
                case 5 -> Single_ModeSRoll_Call;
                case 6 -> ModeS_AllCall_PSR;
                case 7 -> ModeS_RollCall_PSR;

                default -> throw new IllegalStateException("Unexpected value: " + value);
            };
        }
    }
}
