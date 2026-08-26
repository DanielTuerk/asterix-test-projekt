package dtuerk.camel.sender.adexp;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AdexpSenderRoute extends RouteBuilder {

    public static final String ROUTE_ID = "adexp-sender";

    private static final Logger LOG = LoggerFactory.getLogger(AdexpSenderRoute.class);

    @Override
    public void configure() {
        from("timer:adexp-sender?period={{adexp.sender.period-millis}}")
                .routeId(ROUTE_ID)
                .autoStartup("{{routes." + ROUTE_ID + ".enabled}}")
                .bean(FlightDataProvider.class, "buildRequest") // alternativ:  .setBody(simple("resource:classpath:templates/adexp-request-body.txt"))
                .setHeader(Exchange.HTTP_METHOD, constant("PUT"))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .log(LoggingLevel.INFO, LOG, "Sende: ${body}")
                .to("{{adexp.target.uri}}")
                .log("Antwort: ${body}");
    }
}
