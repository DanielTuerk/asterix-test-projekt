package dtuerk.camel.sender.adexp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class FlightDataProvider {

    private final String flightPlan;

    public FlightDataProvider(@Value("classpath:adexp/flight_plan_msg.txt") Resource flightPlanResource) throws IOException {
        this.flightPlan = StreamUtils.copyToString(
                flightPlanResource.getInputStream(), StandardCharsets.UTF_8);
    }

    public String buildRequest() {
        return flightPlan;
    }
}
