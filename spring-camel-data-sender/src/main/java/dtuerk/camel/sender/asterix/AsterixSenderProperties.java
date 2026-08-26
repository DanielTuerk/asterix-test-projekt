package dtuerk.camel.sender.asterix;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Eigenschaften des simulierten Radars bzw. Ziels.
 *
 * @param sac          System Area Code des sendenden Radars (I048/010)
 * @param sic          System Identification Code des sendenden Radars (I048/010)
 * @param mode3aCode   Mode-3/A-Code des Ziels in Oktalschreibweise, z. B. 7000 (I048/070)
 * @param flightLevel  Flugflaeche des Ziels, z. B. 300.0 fuer FL300 (I048/090)
 */
@ConfigurationProperties("asterix.sender")
public record AsterixSenderProperties(int sac, int sic, int mode3aCode, double flightLevel) {
}
