package com.uasz.alumni.alumnistudentcompany.repository;

import com.uasz.alumni.alumnistudentcompany.model.Alumni;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class AlumniSpecifications {

    private AlumniSpecifications() {
    }

    public static Specification<Alumni> search(String query, Integer graduationYear, String specialty) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query != null && !query.isBlank()) {
                String like = "%" + escape(query.trim().toLowerCase(Locale.ROOT)) + "%";
                Expression<String> fullName = cb.lower(cb.concat(cb.concat(
                        cb.coalesce(root.<String>get("firstName"), ""), " "),
                    cb.coalesce(root.<String>get("lastName"), "")));
                predicates.add(cb.or(
                    cb.like(fullName, like, '\\'),
                    cb.like(cb.lower(cb.coalesce(root.<String>get("currentCompany"), "")), like, '\\'),
                    cb.like(cb.lower(cb.coalesce(root.<String>get("currentPosition"), "")), like, '\\')));
            }
            if (graduationYear != null) {
                predicates.add(cb.equal(root.get("graduationYear"), graduationYear));
            }
            if (specialty != null && !specialty.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.<String>get("specialty")),
                    specialty.trim().toLowerCase(Locale.ROOT)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Diplômés pour le rapport d'insertion (ALUM-183) : année de diplôme et spécialité renseignées, filtres
     * facultatifs. La spécialité est comparée sans tenir compte de la casse ni des espaces en début et fin.
     */
    public static Specification<Alumni> graduates(Integer graduationYear, String specialty) {
        return (root, cq, cb) -> {
            Expression<String> normalized = cb.lower(cb.trim(root.<String>get("specialty")));
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNotNull(root.get("graduationYear")));
            predicates.add(cb.isNotNull(root.get("specialty")));
            predicates.add(cb.notEqual(normalized, ""));
            if (graduationYear != null) {
                predicates.add(cb.equal(root.get("graduationYear"), graduationYear));
            }
            if (specialty != null && !specialty.isBlank()) {
                predicates.add(cb.equal(normalized, specialty.trim().toLowerCase(Locale.ROOT)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
