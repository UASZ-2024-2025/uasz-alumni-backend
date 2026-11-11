package com.uasz.alumni.alumnistudentcompany.repository;

import com.uasz.alumni.alumnistudentcompany.model.StudentProfileEntity;
import com.uasz.alumni.alumnistudentcompany.spi.model.AvailabilityStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Critères de recherche multi-filtres des profils étudiants (tous optionnels, combinés en ET). */
public final class StudentSpecifications {

    private static final char ESCAPE = '\\';

    private StudentSpecifications() {
    }

    /**
     * @param query          texte libre : chaque mot doit correspondre au prénom, nom, spécialité,
     *                       une compétence ou un centre d'intérêt (insensible à la casse, partiel)
     * @param specialty      contient (insensible à la casse)
     * @param graduationYear égalité avec academicYear (seul champ « année » du contrat)
     * @param availability   égalité exacte avec le statut de disponibilité
     */
    public static Specification<StudentProfileEntity> search(
            String query, String specialty, Integer graduationYear, AvailabilityStatus availability) {

        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query != null && !query.isBlank()) {
                for (String token : query.trim().split("\\s+")) {
                    String pattern = like(token);
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.<String>get("firstName")), pattern, ESCAPE),
                            cb.like(cb.lower(root.<String>get("lastName")), pattern, ESCAPE),
                            cb.like(cb.lower(root.<String>get("specialty")), pattern, ESCAPE),
                            cb.exists(elementMatches(root, cq.subquery(Integer.class), "skills", pattern, cb)),
                            cb.exists(elementMatches(root, cq.subquery(Integer.class), "interests", pattern, cb))));
                }
            }
            if (specialty != null && !specialty.isBlank()) {
                predicates.add(cb.like(cb.lower(root.<String>get("specialty")), like(specialty.trim()), ESCAPE));
            }
            if (graduationYear != null) {
                predicates.add(cb.equal(root.get("academicYear"), graduationYear));
            }
            if (availability != null) {
                predicates.add(cb.equal(root.get("availabilityStatus"), availability));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** EXISTS (SELECT 1 FROM students s JOIN s.<collection> e WHERE s.id = root.id AND lower(e) LIKE pattern). */
    private static Subquery<Integer> elementMatches(
            Root<StudentProfileEntity> root, Subquery<Integer> sub, String collection, String pattern,
            CriteriaBuilder cb) {
        Root<StudentProfileEntity> sr = sub.from(StudentProfileEntity.class);
        Join<StudentProfileEntity, String> element = sr.join(collection);
        return sub.select(cb.literal(1))
                .where(cb.equal(sr.get("id"), root.get("id")),
                        cb.like(cb.lower(element), pattern, ESCAPE));
    }

    /** %motif% en minuscules, avec échappement de \ % _ saisis par l'utilisateur. */
    private static String like(String text) {
        String escaped = text.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
