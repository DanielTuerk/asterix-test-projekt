package dtuerk.asterix.sender;

import java.time.LocalTime;

import org.springframework.stereotype.Component;

/**
 * Erzeugt fortlaufend Plots eines einzelnen Ziels, das sich langsam im Kreis um das
 * Radar bewegt. Reicht, um beim Empfaenger wechselnde Werte zu sehen.
 */
@Component
public class SimulatedTarget {

    /** Azimutzuwachs je Plot in Grad - entspricht grob einer Radarumdrehung pro Minute. */
    private static final double AZIMUTH_STEP_DEGREES = 6.0;

    private static final double MIN_RANGE_NM = 20.0;
    private static final double MAX_RANGE_NM = 120.0;
    private static final double RANGE_STEP_NM = 0.25;

    private final AsterixSenderProperties properties;

    private double rangeNauticalMiles = MAX_RANGE_NM;
    private double azimuthDegrees = 45.0;

    public SimulatedTarget(AsterixSenderProperties properties) {
        this.properties = properties;
    }

    /**
     * Rueckt das Ziel einen Schritt weiter und liefert den zugehoerigen Plot.
     * Das Ziel fliegt auf das Radar zu und beginnt nach Erreichen der Mindestentfernung
     * wieder von aussen.
     */
    public synchronized Cat048Plot nextPlot() {
        azimuthDegrees = (azimuthDegrees + AZIMUTH_STEP_DEGREES) % 360.0;
        rangeNauticalMiles -= RANGE_STEP_NM;
        if (rangeNauticalMiles < MIN_RANGE_NM) {
            rangeNauticalMiles = MAX_RANGE_NM;
        }

        return new Cat048Plot(
                properties.sac(),
                properties.sic(),
                LocalTime.now(),
                round(rangeNauticalMiles),
                round(azimuthDegrees),
                properties.mode3aCode(),
                properties.flightLevel());
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
