package dtuerk.camel.receiver;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Generischer ADEXP-Parser.
 * <p>
 * Zerlegt eine ADEXP-Nachricht (beliebig mit Zeilenumbrüchen/Whitespace formatiert)
 * in eine verschachtelte Map-Struktur:
 * <p>
 * - einfache Felder            -> String
 * - wiederholte Felder         -> List<Object> (z.B. mehrere -FAC, -EETFIR, -PT)
 * - -BEGIN X ... -END X Blöcke -> Map<String, Object>
 * - inline-komposite Felder    -> Map<String, Object>
 * (z.B. "-ORIGIN -NETWORKTYPE AFTN -FAC EIDWEINU")
 * <p>
 * Da ADEXP inline-komposite Felder (ohne BEGIN/END) nicht selbsterklärend
 * abgrenzt, wird dafür ein kleines Schema gepflegt (COMPOSITE_SCHEMA):
 * es legt fest, welche Subfelder zu einem komposit-Feld gehören, damit der
 * Parser weiß, wo das komposit-Feld endet und die Elternebene weitergeht.
 * Für unbekannte komposite Felder wird konservativ nur ein einziges
 * Subfeld übernommen, um kein "Verschlucken" nachfolgender Felder zu riskieren.
 */
public class AdexpParser {

    /**
     * Bekannte inline-komposite Felder und ihre erlaubten Subfelder.
     */
    private static final Map<String, Set<String>> COMPOSITE_SCHEMA = Map.of(
            "ORIGIN", Set.of("NETWORKTYPE", "FAC"),
            "DEST", Set.of("NETWORKTYPE", "FAC"),
            "PRIORFAC", Set.of("NETWORKTYPE", "FAC"),
            "PT", Set.of("PTID", "FL", "ETO", "SPEED", "ATO", "TTL", "TO", "MACH")
    );

    private static final Pattern FIELD_MARKER = Pattern.compile("^-[A-Z][A-Z0-9]*$");

    private final List<String> tokens;
    private int pos = 0;

    private AdexpParser(List<String> tokens) {
        this.tokens = tokens;
    }

    /**
     * Parst eine komplette ADEXP-Nachricht in eine verschachtelte Map.
     */
    public static Map<String, Object> parse(String rawMessage) {
        String normalized = normalize(rawMessage);
        List<String> tokens = Arrays.asList(normalized.trim().split("\\s+"));
        AdexpParser parser = new AdexpParser(tokens);
        return parser.parseFields(null);
    }

    /**
     * Leichtgewichtiger Vorab-Check, um nur den Nachrichtentyp zu ermitteln
     * (z.B. fürs Routing), ohne die komplette Nachricht zu parsen.
     */
    public static Optional<String> peekTitle(String rawMessage) {
        Pattern p = Pattern.compile("-?TITLE\\s+(\\S+)");
        var m = p.matcher(rawMessage);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /**
     * Manche ADEXP-Quellen lassen den führenden Bindestrich beim allerersten Feld weg.
     */
    private static String normalize(String raw) {
        String trimmed = raw.strip();
        return trimmed.startsWith("-") ? trimmed : "-" + trimmed;
    }

    private boolean isFieldMarker(String token) {
        return FIELD_MARKER.matcher(token).matches();
    }

    /**
     * Parst Felder bis zum Ende der Tokens oder bis zu einem passenden -END,
     * wenn wir uns innerhalb eines -BEGIN-Blocks befinden.
     */
    private Map<String, Object> parseFields(String currentGroupName) {
        Map<String, Object> result = new LinkedHashMap<>();

        while (pos < tokens.size()) {
            String tok = tokens.get(pos);

            if (tok.equals("-END")) {
                pos++; // "-END"
                if (pos < tokens.size()) pos++; // Gruppenname
                return result;
            }

            if (tok.equals("-BEGIN")) {
                pos++; // "-BEGIN"
                String groupName = tokens.get(pos++);
                Object nested = parseFields(groupName);
                addField(result, groupName, nested);
                continue;
            }

            if (isFieldMarker(tok)) {
                String fieldName = tok.substring(1);
                pos++;
                Object value = parseValue(fieldName);
                addField(result, fieldName, value);
                continue;
            }

            // Unerwartetes Token (sollte bei valider ADEXP-Syntax nicht vorkommen)
            pos++;
        }
        return result;
    }

    /**
     * Liest den Wert eines Feldes: einfacher Text ODER verschachtelte Subfelder.
     */
    private Object parseValue(String fieldName) {
        if (pos >= tokens.size()) {
            return "";
        }

        String next = tokens.get(pos);

        // Inline-komposites Feld: direkt gefolgt von einem weiteren Feldmarker
        if (isFieldMarker(next)) {
            return parseCompositeValue(fieldName);
        }

        // Einfacher (ggf. mehrteiliger) Textwert bis zum nächsten Feldmarker
        StringBuilder sb = new StringBuilder();
        while (pos < tokens.size()) {
            String t = tokens.get(pos);
            if (isFieldMarker(t) || t.equals("-BEGIN") || t.equals("-END")) break;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(t);
            pos++;
        }
        return sb.toString();
    }

    private Map<String, Object> parseCompositeValue(String fieldName) {
        Set<String> allowedChildren = COMPOSITE_SCHEMA.get(fieldName);
        Map<String, Object> composite = new LinkedHashMap<>();
        boolean first = true;

        while (pos < tokens.size()) {
            String childTok = tokens.get(pos);

            if (childTok.equals("-BEGIN") || childTok.equals("-END")) break;
            if (!isFieldMarker(childTok)) break;

            String childName = childTok.substring(1);

            if (allowedChildren != null && !allowedChildren.contains(childName)) {
                break; // gehört nicht mehr zu diesem komposit-Feld -> zurück an Elternebene
            }

            pos++; // Subfeld-Marker konsumieren
            Object childValue = parseValue(childName);
            addField(composite, childName, childValue);

            // Unbekanntes Schema: konservativ nur genau ein Subfeld übernehmen
            if (allowedChildren == null) break;

            first = false;
        }

        if (composite.isEmpty() && !first) {
            // Defensive: sollte praktisch nicht auftreten
            throw new RuntimeException("no field found for " + fieldName);
        }
        return composite;
    }

    @SuppressWarnings("unchecked")
    private void addField(Map<String, Object> map, String name, Object value) {
        if (map.containsKey(name)) {
            Object existing = map.get(name);
            if (existing instanceof List) {
                ((List<Object>) existing).add(value);
            } else {
                List<Object> list = new ArrayList<>();
                list.add(existing);
                list.add(value);
                map.put(name, list);
            }
        } else {
            map.put(name, value);
        }
    }
}
