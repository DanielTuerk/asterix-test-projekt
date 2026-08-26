package dtuerk.camel.receiver.ifpl;

import dtuerk.camel.common.FlightPlan;
import dtuerk.camel.receiver.AdexpParser;
import org.apache.camel.Converter;
import org.apache.camel.TypeConverters;
import org.springframework.stereotype.Component;

@Component
public class FlightPlanTypeConverter implements TypeConverters {

    @Converter
    public FlightPlan toFlightPlan(String body) {
        return FlightPlan.build(AdexpParser.parse(body));
    }
}
