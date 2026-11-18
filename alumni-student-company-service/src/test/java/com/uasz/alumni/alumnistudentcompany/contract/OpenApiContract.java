package com.uasz.alumni.alumnistudentcompany.contract;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.yaml.snakeyaml.Yaml;

/**
 * Lecture du contrat OpenAPI (contracts/*.yaml) depuis les tests : les cas de test et les vérifications de
 * réponse sont dérivés du contrat lui-même, pas du code (ALUM-171, expérience 2).
 *
 * <p>Volontairement minimal : objets, tableaux, {@code $ref}, {@code required}, types et formats
 * {@code uuid}, {@code date}, {@code date-time}, {@code enum}. Pas de {@code oneOf}/{@code allOf}.
 */
public final class OpenApiContract {

    private final Map<String, Object> root;

    private OpenApiContract(Map<String, Object> root) {
        this.root = root;
    }

    /** Contrat d'un service, lu depuis le dossier contracts/ du dépôt (le test tourne dans le dossier du service). */
    public static OpenApiContract of(String service) {
        Path path = Path.of("..", "contracts", service + ".yaml");
        try (InputStream in = Files.newInputStream(path)) {
            return new OpenApiContract(new Yaml().load(in));
        } catch (IOException e) {
            throw new IllegalStateException("Contrat introuvable : " + path.toAbsolutePath(), e);
        }
    }

    /** Codes HTTP déclarés pour une opération. */
    public Set<Integer> declaredStatuses(String path, String method) {
        Map<String, Object> operation = map(map(map(root.get("paths")).get(path)).get(method.toLowerCase()));
        Map<String, Object> responses = map(operation.get("responses"));
        Set<Integer> codes = new TreeSet<>();
        responses.keySet().forEach(k -> codes.add(Integer.parseInt(String.valueOf(k))));
        return codes;
    }

    public Map<String, Object> schema(String name) {
        return map(map(map(root.get("components")).get("schemas")).get(name));
    }

    @SuppressWarnings("unchecked")
    public List<String> required(String schemaName) {
        Object required = schema(schemaName).get("required");
        return required == null ? List.of() : (List<String>) required;
    }

    public Map<String, Object> properties(String schemaName) {
        return map(schema(schemaName).get("properties"));
    }

    /** Exemple minimal valide d'un schéma, construit à partir des types et formats du contrat. */
    public Map<String, Object> example(String schemaName) {
        Map<String, Object> example = new LinkedHashMap<>();
        properties(schemaName).forEach((name, spec) -> example.put(name, sample(map(spec))));
        return example;
    }

    /** Liste des écarts entre une valeur JSON (Map/List/String...) et un schéma ; vide si conforme. */
    public List<String> violations(Object value, String schemaName) {
        List<String> errors = new ArrayList<>();
        check(value, schema(schemaName), schemaName, errors);
        return errors;
    }

    private void check(Object value, Map<String, Object> spec, String where, List<String> errors) {
        if (spec.containsKey("$ref")) {
            String ref = String.valueOf(spec.get("$ref"));
            check(value, schema(ref.substring(ref.lastIndexOf('/') + 1)), where, errors);
            return;
        }
        if (value == null) {
            return; // propriété facultative absente ou null
        }
        String type = String.valueOf(spec.getOrDefault("type", "object"));
        switch (type) {
            case "object" -> {
                if (!(value instanceof Map<?, ?> obj)) {
                    errors.add(where + " : objet attendu, reçu " + value);
                    return;
                }
                Map<String, Object> props = spec.get("properties") == null ? Map.of() : map(spec.get("properties"));
                Object required = spec.get("required");
                if (required instanceof List<?> names) {
                    names.forEach(n -> {
                        if (!obj.containsKey(n)) {
                            errors.add(where + "." + n + " : obligatoire mais absent");
                        }
                    });
                }
                obj.forEach((k, v) -> {
                    if (!props.containsKey(String.valueOf(k))) {
                        errors.add(where + "." + k + " : propriété non déclarée dans le contrat");
                    } else {
                        check(v, map(props.get(String.valueOf(k))), where + "." + k, errors);
                    }
                });
            }
            case "array" -> {
                if (!(value instanceof List<?> list)) {
                    errors.add(where + " : tableau attendu");
                    return;
                }
                for (int i = 0; i < list.size(); i++) {
                    check(list.get(i), map(spec.get("items")), where + "[" + i + "]", errors);
                }
            }
            case "string" -> {
                if (!(value instanceof String s)) {
                    errors.add(where + " : chaîne attendue, reçu " + value);
                    return;
                }
                String format = String.valueOf(spec.get("format"));
                try {
                    switch (format) {
                        case "uuid" -> UUID.fromString(s);
                        case "date" -> LocalDate.parse(s);
                        case "date-time" -> OffsetDateTime.parse(s);
                        default -> { }
                    }
                } catch (RuntimeException e) {
                    errors.add(where + " : format " + format + " invalide (" + s + ")");
                }
                if (spec.get("enum") instanceof List<?> allowed && !allowed.contains(s)) {
                    errors.add(where + " : valeur hors enum " + allowed + " (" + s + ")");
                }
            }
            case "boolean" -> {
                if (!(value instanceof Boolean)) {
                    errors.add(where + " : booléen attendu, reçu " + value);
                }
            }
            case "integer", "number" -> {
                if (!(value instanceof Number)) {
                    errors.add(where + " : nombre attendu, reçu " + value);
                }
            }
            default -> { }
        }
    }

    private Object sample(Map<String, Object> spec) {
        if (spec.containsKey("$ref")) {
            String ref = String.valueOf(spec.get("$ref"));
            Map<String, Object> target = schema(ref.substring(ref.lastIndexOf('/') + 1));
            if (target.get("enum") instanceof List<?> values) {
                return values.getFirst();
            }
            return example(ref.substring(ref.lastIndexOf('/') + 1));
        }
        if (spec.get("enum") instanceof List<?> values) {
            return values.getFirst();
        }
        return switch (String.valueOf(spec.getOrDefault("type", "string"))) {
            case "boolean" -> false;
            case "integer", "number" -> 1;
            case "array" -> List.of();
            default -> switch (String.valueOf(spec.get("format"))) {
                case "uuid" -> UUID.randomUUID().toString();
                case "date" -> "2024-01-01";
                case "date-time" -> "2024-01-01T10:00:00Z";
                case "email" -> "test@uasz.sn";
                default -> "texte";
            };
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return o == null ? Map.of() : (Map<String, Object>) o;
    }
}
