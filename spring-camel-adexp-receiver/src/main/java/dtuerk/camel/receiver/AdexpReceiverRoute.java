package dtuerk.camel.receiver;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AdexpReceiverRoute extends RouteBuilder {

    public static final String ROUTE_ID = "process-flight-route";

    private static final Logger LOG = LoggerFactory.getLogger(AdexpReceiverRoute.class);
    public static final String HEADER_TITLE = "TITLE";

    @Override
    public void configure() {
        restConfiguration()
                .component("jetty")
                .host("0.0.0.0")
                .port(8081)
                .contextPath("/api");

        rest("/adexp")
                .put()
                .id("put-adexp-update")
                .to("direct:processAdexpMsg");

        from("direct:processAdexpMsg")
                .routeId(ROUTE_ID)
                .log("PUT empfangen für ARCID: ${header.arcid}")
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    String title = AdexpParser.peekTitle(body).orElse("UNKNOWN");
                    exchange.getIn().setHeader(HEADER_TITLE, title);
                })
                .choice()
                .when(header(HEADER_TITLE).isEqualTo("IFPL"))
                .to("direct:handleIfpl")
                .when(header(HEADER_TITLE).isEqualTo("ABI"))
                .to("direct:handleAbi")
                .when(header(HEADER_TITLE).isEqualTo("ACT"))
                .to("direct:handleAct")
                .otherwise()
                .log(LoggingLevel.WARN, "Unbekannter ADEXP-Typ: ${header.HEADER_TITLE}")
                .to("direct:handleUnknown")
                .end()

                // HTTP-Response
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .setBody(constant("OK"));
    }

}
