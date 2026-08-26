package dtuerk.camel.receiver;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Quelle und Ziel werden fuer den Test umgebogen: kein echter Netzwerk-Port,
 * kein echter Broker. Geprueft wird, dass der Block unveraendert am Ziel ankommt.
 */
@SpringBootTest(properties = {
        "asterix.source.uri=direct:asterix-source",
        "asterix.sink.uri=mock:kafka",
        "camel.main.main-run-controller=false"
})
class AsterixReceiverRouteTest {

    /**
     * Beispielblock: CAT 048 (0x30), Laenge 11 Byte.
     */
    private static final byte[] SAMPLE_BLOCK = {
            0x30, 0x00, 0x0B, (byte) 0xF5, 0x02, 0x0D, (byte) 0xE0, 0x1A, (byte) 0xB4, 0x2F, 0x08
    };

    @EndpointInject("mock:kafka")
    private MockEndpoint kafka;

    @Autowired
    private ProducerTemplate producerTemplate;

    @Test
    void legtEingehendenAsterixBlockUnveraendertAufDasZiel() throws InterruptedException {
        kafka.expectedMessageCount(1);
        kafka.expectedBodiesReceived((Object) SAMPLE_BLOCK);

        producerTemplate.sendBody("direct:asterix-source", SAMPLE_BLOCK);

        kafka.assertIsSatisfied();
    }
}
