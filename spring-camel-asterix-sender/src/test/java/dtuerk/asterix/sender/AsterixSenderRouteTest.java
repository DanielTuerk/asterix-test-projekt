package dtuerk.asterix.sender;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Das Ziel wird fuer den Test von UDP auf ein mock:-Endpoint umgebogen,
 * damit kein Empfaenger laufen muss.
 */
@SpringBootTest(properties = {
        "asterix.target.uri=mock:asterix",
        "asterix.sender.period-millis=50",
        "camel.main.main-run-controller=false"
})
class AsterixSenderRouteTest {

    @Autowired
    private CamelContext camelContext;

    @Test
    void sendetFortlaufendCat048Bloecke() throws Exception {
        MockEndpoint target = camelContext.getEndpoint("mock:asterix", MockEndpoint.class);
        target.expectedMinimumMessageCount(2);

        target.assertIsSatisfied(5_000);

        byte[] block = target.getExchanges().getFirst().getMessage().getBody(byte[].class);
        assertThat(block).hasSize(Cat048Encoder.BLOCK_LENGTH);
        assertThat(block[0]).isEqualTo((byte) Cat048Encoder.CATEGORY);
    }

    @Test
    void bewegtDasZielZwischenZweiPlots() throws Exception {
        MockEndpoint target = camelContext.getEndpoint("mock:asterix", MockEndpoint.class);
        target.expectedMinimumMessageCount(2);
        target.assertIsSatisfied(5_000);

        byte[] first = target.getExchanges().get(0).getMessage().getBody(byte[].class);
        byte[] second = target.getExchanges().get(1).getMessage().getBody(byte[].class);

        // Offset 12/13: THETA aus I048/040 - muss sich zwischen zwei Plots geaendert haben.
        assertThat(new byte[] {second[12], second[13]})
                .isNotEqualTo(new byte[] {first[12], first[13]});
    }
}
