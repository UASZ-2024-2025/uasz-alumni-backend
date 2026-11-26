-- ALUM-255 : photo du CV, une par profil étudiant (students.id), stockée en base.
-- JPEG ou PNG de 2 Mo au plus (contrôlé par le service) ; lue seulement par l'étudiant et l'administrateur.
CREATE TABLE student_cv_photos (
    student_id   UUID                     PRIMARY KEY REFERENCES students (id) ON DELETE CASCADE,
    content_type VARCHAR(20)              NOT NULL,
    data         BYTEA                    NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
