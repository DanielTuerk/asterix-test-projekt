package dtuerk.camel.receiver;

import org.apache.camel.EndpointInject;
import org.apache.camel.Message;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Echter Netzwerkpfad: die Route haengt am Netty-UDP-Consumer und bekommt ihr Datagramm
 * von einem gewoehnlichen {@link DatagramSocket} - also von ausserhalb, nicht ueber Camel.
 * Damit ist genau das abgedeckt, was {@link AsterixReceiverRouteTest} mit seiner
 * {@code direct:}-Quelle auslaesst: der Bind auf den Port, der Empfang des Datagramms und
 * der {@code udpByteArrayCodec}, der die rohen Bytes ohne Java-Serialisierung durchreicht.
 *
 * <p>Gebunden wird bewusst Port 8601 statt des Defaults 8600: 8600 haelt im Normalfall der
 * lokal aus der IDE gestartete Receiver. Ein Test darauf wuerde ihm entweder den Port
 * wegnehmen oder selbst an "Address already in use" scheitern. 8601 muss frei sein, sonst
 * schlaegt schon der Start des Spring-Kontexts fehl.</p>
 *
 * <p>Kafka bleibt hier aussen vor - das deckt {@link AsterixReceiverKafkaIntegrationTest}
 * ab. Die Strecke endet auf einem Mock, damit ein Fehlschlag eindeutig am UDP-Teil liegt
 * und der Test ohne Docker auskommt.</p>
 */
@SpringBootTest(properties = {
        "asterix.sink.uri=mock:kafka",
        "camel.main.main-run-controller=false"
})
class AsterixReceiverUdpIntegrationTest {

    /**
     * Ausweichport, siehe Klassenkommentar.
     */
    private static final int UDP_PORT = 8601;

    /**
     * Beispielblock: CAT 048 (0x30), Laenge 11 Byte.
     */
    private static final byte[] SAMPLE_BLOCK = {
            0x30, 0x00, 0x0B, (byte) 0xF5, 0x02, 0x0D, (byte) 0xE0, 0x1A, (byte) 0xB4, 0x2F, 0x08
    };

    /**
     * UDP ist fire-and-forget: geht ein Datagramm verloren, wird einfach erneut gesendet.
     */
    private static final int MAX_VERSUCHE = 20;

    @EndpointInject("mock:kafka")
    private MockEndpoint kafka;

    /**
     * Setzt die Quell-URI programmatisch. In {@code @SpringBootTest(properties = ...)} ginge
     * nur ein String-Literal - so steht die Portnummer an genau einer Stelle.
     */
    @DynamicPropertySource
    static void udpQuelle(DynamicPropertyRegistry registry) {
        registry.add("asterix.source.uri", () -> "netty:udp://127.0.0.1:" + UDP_PORT
                + "?sync=false&udpByteArrayCodec=true&receiveBufferSize=65535");
    }

    @Test
    void nimmtBlockPerUdpEntgegenUndReichtIhnUnveraendertWeiter() throws Exception {
        // Minimum statt exakt 1: ein Wiederholungsversuch darf nicht als Fehler gelten.
        kafka.expectedMinimumMessageCount(1);

        sendeBisEmpfangen(SAMPLE_BLOCK);

        kafka.assertIsSatisfied();
        Message empfangen = kafka.getExchanges().get(0).getMessage();

        // Prueft beides: den Inhalt und dass der Codec nicht auf Puffergroesse auffuellt.
        assertThat(empfangen.getBody(byte[].class)).isEqualTo(SAMPLE_BLOCK);
        // Den Absender setzt nur der Netty-Consumer - Beleg, dass der Block wirklich
        // ueber den Socket kam und nicht an der Quelle vorbei eingespeist wurde.
        assertThat(empfangen.getHeader("CamelNettyRemoteAddress", String.class))
                .contains("127.0.0.1");
    }

    private void sendeBisEmpfangen(byte[] block) throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            DatagramPacket paket = new DatagramPacket(
                    block, block.length, InetAddress.getLoopbackAddress(), UDP_PORT);
            for (int versuch = 0; versuch < MAX_VERSUCHE; versuch++) {
                socket.send(paket);
                if (kafka.await(250, TimeUnit.MILLISECONDS)) {
                    return;
                }
            }
        }
    }
}
