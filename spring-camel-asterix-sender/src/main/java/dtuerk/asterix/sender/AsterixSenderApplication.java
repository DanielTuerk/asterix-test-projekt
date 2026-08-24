package dtuerk.asterix.sender;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AsterixSenderApplication {

    public static void main(String[] args) {
        SpringApplication.run(AsterixSenderApplication.class, args);
    }
}
