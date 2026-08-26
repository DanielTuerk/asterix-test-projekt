package dtuerk.camel.sender.asterix;

import java.time.LocalTime;

/**
 * Ein einzelner Plot (Zielmeldung) eines Monoradars, so wie ihn ASTERIX CAT 048 beschreibt.
 *
 * @param sac                 System Area Code (I048/010)
 * @param sic                 System Identification Code (I048/010)
 * @param timeOfDay           Zeitstempel seit Mitternacht UTC (I048/140)
 * @param rangeNauticalMiles  Schraegentfernung RHO in NM (I048/040)
 * @param azimuthDegrees      Azimut THETA in Grad, 0 = Nord (I048/040)
 * @param mode3aCodeOctal     Mode-3/A-Code in Oktalschreibweise, z. B. 7000 (I048/070)
 * @param flightLevel         Flugflaeche in FL, z. B. 300.0 (I048/090)
 */
public record Cat048Plot(
        int sac,
        int sic,
        LocalTime timeOfDay,
        double rangeNauticalMiles,
        double azimuthDegrees,
        int mode3aCodeOctal,
        double flightLevel) {
}
