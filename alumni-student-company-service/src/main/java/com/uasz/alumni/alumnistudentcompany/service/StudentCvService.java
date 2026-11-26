package com.uasz.alumni.alumnistudentcompany.service;

import com.uasz.alumni.alumnistudentcompany.exceptions.ApiException;
import com.uasz.alumni.alumnistudentcompany.model.StudentCvEntity;
import com.uasz.alumni.alumnistudentcompany.model.StudentProfileEntity;
import com.uasz.alumni.alumnistudentcompany.repository.StudentCvRepository;
import com.uasz.alumni.alumnistudentcompany.repository.StudentProfileRepository;
import com.uasz.alumni.alumnistudentcompany.security.AuthenticatedUser;
import com.uasz.alumni.alumnistudentcompany.security.CurrentUserProvider;
import com.uasz.alumni.alumnistudentcompany.spi.model.CvEducation;
import com.uasz.alumni.alumnistudentcompany.spi.model.CvExperience;
import com.uasz.alumni.alumnistudentcompany.spi.model.CvLanguage;
import com.uasz.alumni.alumnistudentcompany.spi.model.CvRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.CvResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CV de l'étudiant (ALUM-109). {@code id} est l'identifiant du profil étudiant ou celui de son compte (le front ne
 * connaît que le second). Lecture : tout utilisateur connecté (recruteur d'une candidature, Alumni, annuaire).
 * Écriture : l'étudiant lui-même ou un administrateur ; un PUT remplace tout le CV précédent.
 * {@code hasPhoto} indique si l'appelant peut afficher la photo du CV (ALUM-255, voir StudentCvPhotoService).
 */
@Service
public class StudentCvService {

    static final int MAX_SKILLS = 50;
    static final int MAX_SECTION_ITEMS = 20;
    private static final String ROLE_ADMIN = "ADMINISTRATEUR";

    private final StudentProfileRepository students;
    private final StudentCvRepository cvs;
    private final CurrentUserProvider currentUserProvider;
    private final StudentCvPhotoService photos;

    public StudentCvService(StudentProfileRepository students, StudentCvRepository cvs,
            CurrentUserProvider currentUserProvider, StudentCvPhotoService photos) {
        this.students = students;
        this.cvs = cvs;
        this.currentUserProvider = currentUserProvider;
        this.photos = photos;
    }

    @Transactional(readOnly = true)
    public CvResponse get(UUID id) {
        StudentProfileEntity student = findStudent(id);
        CvResponse cv = cvs.findByStudentId(student.getId()).map(StudentCvService::toDto).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "CV_NOT_FOUND", "Cet étudiant n'a pas encore de CV"));
        return cv.hasPhoto(photos.visibleTo(student, currentUserProvider.require()));
    }

    @Transactional
    public CvResponse update(UUID id, CvRequest request) {
        StudentProfileEntity student = findStudent(id);
        AuthenticatedUser user = currentUserProvider.require();
        if (!ROLE_ADMIN.equals(user.role()) && !user.id().equals(student.getUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Seul l'étudiant concerné ou un administrateur peut modifier ce CV");
        }

        StudentCvEntity cv = cvs.findByStudentId(student.getId()).orElseGet(() -> new StudentCvEntity(student.getId()));
        cv.setTitle(required("title", request.getTitle(), 150));
        cv.setTemplate(required("template", request.getTemplate(), 50));
        cv.setLanguage(required("language", request.getLanguage(), 50));
        cv.setSummary(optional("summary", request.getSummary(), 4000));
        cv.replaceSections(
                skills(request.getSkills()),
                section("experiences", request.getExperiences(), StudentCvService::experience),
                section("educations", request.getEducations(), StudentCvService::education),
                section("languages", request.getLanguages(), StudentCvService::language));
        return toDto(cvs.save(cv)).hasPhoto(photos.visibleTo(student, user));
    }

    private StudentProfileEntity findStudent(UUID id) {
        return students.findById(id).or(() -> students.findByUserId(id)).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Profil étudiant introuvable : " + id));
    }

    // ---------- validation ----------

    private static String required(String field, String value, int max) {
        String cleaned = optional(field, value, max);
        if (cleaned == null) {
            throw badRequest(field + " est obligatoire");
        }
        return cleaned;
    }

    private static String optional(String field, String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw badRequest(field + " : " + max + " caractères maximum");
        }
        return trimmed;
    }

    /** Trim, vides et doublons (insensibles à la casse) retirés, ordre conservé. */
    private static List<String> skills(List<String> values) {
        List<String> cleaned = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String value : values == null ? List.<String>of() : values) {
            String skill = optional("skills", value, 100);
            if (skill != null && seen.add(skill.toLowerCase(Locale.ROOT))) {
                cleaned.add(skill);
            }
        }
        if (cleaned.size() > MAX_SKILLS) {
            throw badRequest("skills : " + MAX_SKILLS + " éléments maximum");
        }
        return cleaned;
    }

    /** Entrées entièrement vides ignorées (ligne ajoutée puis laissée vide dans le formulaire). */
    private static <I, O> List<O> section(String field, List<I> values, Function<I, O> mapper) {
        List<O> items = (values == null ? List.<I>of() : values).stream()
                .filter(Objects::nonNull).map(mapper).filter(Objects::nonNull).toList();
        if (items.size() > MAX_SECTION_ITEMS) {
            throw badRequest(field + " : " + MAX_SECTION_ITEMS + " éléments maximum");
        }
        return items;
    }

    private static StudentCvEntity.Experience experience(CvExperience e) {
        String company = optional("experiences.companyName", e.getCompanyName(), 150);
        String position = optional("experiences.position", e.getPosition(), 150);
        String description = optional("experiences.description", e.getDescription(), 2000);
        if (company == null && position == null && description == null
                && e.getStartDate() == null && e.getEndDate() == null) {
            return null;
        }
        checkDates("experiences", e.getStartDate(), e.getEndDate());
        return new StudentCvEntity.Experience(company, position, e.getStartDate(), e.getEndDate(), description);
    }

    private static StudentCvEntity.Education education(CvEducation e) {
        String institution = optional("educations.institution", e.getInstitution(), 200);
        String degree = optional("educations.degree", e.getDegree(), 150);
        String field = optional("educations.fieldOfStudy", e.getFieldOfStudy(), 150);
        if (institution == null && degree == null && field == null
                && e.getStartDate() == null && e.getEndDate() == null) {
            return null;
        }
        checkDates("educations", e.getStartDate(), e.getEndDate());
        return new StudentCvEntity.Education(institution, degree, field, e.getStartDate(), e.getEndDate());
    }

    private static StudentCvEntity.Language language(CvLanguage l) {
        String language = optional("languages.language", l.getLanguage(), 50);
        String proficiency = optional("languages.proficiency", l.getProficiency(), 50);
        return language == null ? null : new StudentCvEntity.Language(language, proficiency);
    }

    private static void checkDates(String field, LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw badRequest(field + " : la date de fin précède la date de début");
        }
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    // ---------- mapping ----------

    static CvResponse toDto(StudentCvEntity cv) {
        return new CvResponse()
                .id(cv.getId())
                .studentId(cv.getStudentId())
                .title(cv.getTitle())
                .template(cv.getTemplate())
                .language(cv.getLanguage())
                .summary(cv.getSummary())
                .fileUrl(cv.getFileUrl())
                .skills(new ArrayList<>(cv.getSkills()))
                .experiences(cv.getExperiences().stream().map(e -> new CvExperience()
                        .companyName(e.getCompanyName()).position(e.getPosition())
                        .startDate(e.getStartDate()).endDate(e.getEndDate()).description(e.getDescription()))
                        .toList())
                .educations(cv.getEducations().stream().map(e -> new CvEducation()
                        .institution(e.getInstitution()).degree(e.getDegree()).fieldOfStudy(e.getFieldOfStudy())
                        .startDate(e.getStartDate()).endDate(e.getEndDate()))
                        .toList())
                .languages(cv.getLanguages().stream().map(l -> new CvLanguage()
                        .language(l.getLanguage()).proficiency(l.getProficiency()))
                        .toList());
    }
}
