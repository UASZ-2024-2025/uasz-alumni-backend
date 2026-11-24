package com.uasz.alumni.alumnistudentcompany.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

/**
 * CV d'un étudiant (ALUM-109) : un seul CV par profil, remplacé en entier à chaque mise à jour.
 * Son id est le {@code cvId} repris par les candidatures du job-offer-service.
 */
@Entity
@Table(name = "student_cvs")
public class StudentCvEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "student_id", nullable = false, unique = true)
    private UUID studentId;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "template", nullable = false, length = 50)
    private String template;

    @Column(name = "language", nullable = false, length = 50)
    private String language;

    @Column(name = "summary", length = 4000)
    private String summary;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @ElementCollection
    @CollectionTable(name = "student_cv_skills", joinColumns = @JoinColumn(name = "cv_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "skill", nullable = false, length = 100)
    @BatchSize(size = 50)
    private List<String> skills = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "student_cv_experiences", joinColumns = @JoinColumn(name = "cv_id"))
    @OrderColumn(name = "sort_order")
    @BatchSize(size = 50)
    private List<Experience> experiences = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "student_cv_educations", joinColumns = @JoinColumn(name = "cv_id"))
    @OrderColumn(name = "sort_order")
    @BatchSize(size = 50)
    private List<Education> educations = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "student_cv_languages", joinColumns = @JoinColumn(name = "cv_id"))
    @OrderColumn(name = "sort_order")
    @BatchSize(size = 50)
    private List<Language> languages = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudentCvEntity() {
        // requis par JPA
    }

    public StudentCvEntity(UUID studentId) {
        this.studentId = studentId;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getStudentId() { return studentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getFileUrl() { return fileUrl; }
    public List<String> getSkills() { return skills; }
    public List<Experience> getExperiences() { return experiences; }
    public List<Education> getEducations() { return educations; }
    public List<Language> getLanguages() { return languages; }

    /** Remplace toutes les sections : les collections restent les mêmes instances (suivies par Hibernate). */
    public void replaceSections(List<String> skills, List<Experience> experiences, List<Education> educations,
            List<Language> languages) {
        replace(this.skills, skills);
        replace(this.experiences, experiences);
        replace(this.educations, educations);
        replace(this.languages, languages);
    }

    private static <T> void replace(List<T> target, List<T> values) {
        target.clear();
        target.addAll(values);
    }

    @Embeddable
    public static class Experience {
        @Column(name = "company_name", length = 150)
        private String companyName;
        @Column(name = "job_title", length = 150)
        private String position;
        @Column(name = "start_date")
        private LocalDate startDate;
        @Column(name = "end_date")
        private LocalDate endDate;
        @Column(name = "description", length = 2000)
        private String description;

        protected Experience() {
            // requis par JPA
        }

        public Experience(String companyName, String position, LocalDate startDate, LocalDate endDate,
                String description) {
            this.companyName = companyName;
            this.position = position;
            this.startDate = startDate;
            this.endDate = endDate;
            this.description = description;
        }

        public String getCompanyName() { return companyName; }
        public String getPosition() { return position; }
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
        public String getDescription() { return description; }
    }

    @Embeddable
    public static class Education {
        @Column(name = "institution", length = 200)
        private String institution;
        @Column(name = "degree", length = 150)
        private String degree;
        @Column(name = "field_of_study", length = 150)
        private String fieldOfStudy;
        @Column(name = "start_date")
        private LocalDate startDate;
        @Column(name = "end_date")
        private LocalDate endDate;

        protected Education() {
            // requis par JPA
        }

        public Education(String institution, String degree, String fieldOfStudy, LocalDate startDate,
                LocalDate endDate) {
            this.institution = institution;
            this.degree = degree;
            this.fieldOfStudy = fieldOfStudy;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        public String getInstitution() { return institution; }
        public String getDegree() { return degree; }
        public String getFieldOfStudy() { return fieldOfStudy; }
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
    }

    @Embeddable
    public static class Language {
        @Column(name = "language", length = 50)
        private String language;
        @Column(name = "proficiency", length = 50)
        private String proficiency;

        protected Language() {
            // requis par JPA
        }

        public Language(String language, String proficiency) {
            this.language = language;
            this.proficiency = proficiency;
        }

        public String getLanguage() { return language; }
        public String getProficiency() { return proficiency; }
    }
}
