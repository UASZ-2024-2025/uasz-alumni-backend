package com.uasz.alumni.analyticsmatching.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Normalisation des compétences et des intérêts avant comparaison (ALUM-179).
 *
 * <p>Deux libellés désignent la même compétence quand leurs clés {@link #key(String)} sont égales :
 * <ol>
 *   <li>accents retirés et minuscules : « Génie Logiciel » → « genie logiciel » ;</li>
 *   <li>tirets, soulignés, barres obliques et espaces multiples remplacés par un espace ;</li>
 *   <li>espaces et points supprimés : « Spring Boot », « spring-boot » et « SpringBoot » → « springboot »,
 *       « Node.js » → « nodejs » ; « + » et « # » sont gardés (C++, C#) ;</li>
 *   <li>alias courants ramenés à un nom unique : « JS » → « javascript », « k8s » → « kubernetes ».</li>
 * </ol>
 */
public final class SkillNormalizer {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s_\\-/]+");
    private static final Pattern NOT_KEY_CHAR = Pattern.compile("[^a-z0-9+#]");

    /** Alias → nom unique, tous deux sous forme de clé. */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("js", "javascript"),
            Map.entry("ts", "typescript"),
            Map.entry("reactjs", "react"),
            Map.entry("vuejs", "vue"),
            Map.entry("angularjs", "angular"),
            Map.entry("node", "nodejs"),
            Map.entry("k8s", "kubernetes"),
            Map.entry("postgres", "postgresql"),
            Map.entry("golang", "go"),
            Map.entry("py", "python"),
            Map.entry("csharp", "c#"),
            Map.entry("cpp", "c++"),
            Map.entry("ml", "machinelearning"),
            Map.entry("ia", "intelligenceartificielle"),
            Map.entry("ai", "intelligenceartificielle"));

    private SkillNormalizer() { }

    /**
     * Forme lisible normalisée : sans accents, en minuscules, séparateurs ramenés à un espace.
     * Sert à chercher un intérêt dans le texte d'une offre.
     */
    public static String text(String raw) {
        if (raw == null) {
            return "";
        }
        String noAccents = DIACRITICS.matcher(Normalizer.normalize(raw, Normalizer.Form.NFD)).replaceAll("");
        return SEPARATORS.matcher(noAccents.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }

    /** Clé de comparaison d'une compétence ; chaîne vide si le libellé est vide. */
    public static String key(String raw) {
        String compact = NOT_KEY_CHAR.matcher(text(raw)).replaceAll("");
        return ALIASES.getOrDefault(compact, compact);
    }
}
