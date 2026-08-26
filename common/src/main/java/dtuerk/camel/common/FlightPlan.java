package dtuerk.camel.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typisierte, unveränderliche Sicht auf eine geparste ADEXP-IFPL-Nachricht.
 */
public record FlightPlan(
        String title,
        String arcid,
        String adep,
        String ades,
        String arctyp,
        String eobd,
        String eobt,
        String rfl,
        String speed,
        String route,
        String ifplId,
        List<String> addresses,
        String originFac,
        List<RoutePoint> routePoints,
        Map<String, String> eetPerFir
) {

    public static FlightPlan build(Map<String, Object> raw) {
        return new FlightPlan(
                getString(raw, "TITLE"),
                getString(raw, "ARCID"),
                getString(raw, "ADEP"),
                getString(raw, "ADES"),
                getString(raw, "ARCTYP"),
                getString(raw, "EOBD"),
                getString(raw, "EOBT"),
                getString(raw, "RFL"),
                getString(raw, "SPEED"),
                getString(raw, "ROUTE"),
                getString(raw, "IFPLID"),
                extractAddresses(raw),
                extractOriginFac(raw),
                extractRoutePoints(raw),
                extractEetPerFir(raw)
        );
    }

    private static String getString(Map<String, Object> raw, String key) {
        Object v = raw.get(key);
        return v instanceof String ? (String) v : null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractAddresses(Map<String, Object> raw) {
        Object addr = raw.get("ADDR");
        if (!(addr instanceof Map)) return List.of();
        return toStringList(((Map<String, Object>) addr).get("FAC"));
    }

    @SuppressWarnings("unchecked")
    private static String extractOriginFac(Map<String, Object> raw) {
        Object origin = raw.get("ORIGIN");
        if (!(origin instanceof Map)) return null;
        Object fac = ((Map<String, Object>) origin).get("FAC");
        return fac instanceof String ? (String) fac : null;
    }

    @SuppressWarnings("unchecked")
    private static List<RoutePoint> extractRoutePoints(Map<String, Object> raw) {
        Object rtepts = raw.get("RTEPTS");
        if (!(rtepts instanceof Map)) return List.of();

        Object ptObj = ((Map<String, Object>) rtepts).get("PT");
        List<Object> ptList = asList(ptObj);

        List<RoutePoint> result = new ArrayList<>();
        for (Object o : ptList) {
            if (o instanceof Map) {
                Map<String, Object> pt = (Map<String, Object>) o;
                result.add(new RoutePoint(
                        (String) pt.get("PTID"),
                        (String) pt.get("FL"),
                        (String) pt.get("ETO")
                ));
            }
        }
        return result;
    }

    private static Map<String, String> extractEetPerFir(Map<String, Object> raw) {
        List<String> entries = toStringList(raw.get("EETFIR"));
        Map<String, String> result = new LinkedHashMap<>();
        for (String entry : entries) {
            String[] parts = entry.split("\\s+", 2);
            if (parts.length == 2) {
                result.put(parts[0], parts[1]);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        if (value == null) return List.of();
        if (value instanceof List) return (List<Object>) value;
        return List.of(value);
    }

    private static List<String> toStringList(Object value) {
        List<String> result = new ArrayList<>();
        for (Object o : asList(value)) {
            if (o instanceof String) result.add((String) o);
        }
        return result;
    }

    public record RoutePoint(String ptid, String fl, String eto) {
    }


}