-- Ticket « Profil étudiant » : profil étudiant, compétences, centres d'intérêt, disponibilité.
-- Un profil par utilisateur du user-service (user_id unique, pas de clé étrangère : base distincte).
CREATE TABLE students (
    id                  UUID                     PRIMARY KEY,
    user_id             UUID                     NOT NULL UNIQUE,
    first_name          VARCHAR(100)             NOT NULL,
    last_name           VARCHAR(100)             NOT NULL,
    email               VARCHAR(255)             NOT NULL,
    specialty           VARCHAR(100),
    academic_year       INTEGER,
    bio                 VARCHAR(2000),
    availability_status VARCHAR(30)              NOT NULL DEFAULT 'NOT_AVAILABLE',
    availability_date   DATE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE student_skills (
    student_id UUID        NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    skill      VARCHAR(50) NOT NULL
);

CREATE TABLE student_interests (
    student_id UUID        NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    interest   VARCHAR(50) NOT NULL
);

CREATE INDEX idx_students_availability  ON students (availability_status);
CREATE INDEX idx_students_academic_year ON students (academic_year);
CREATE INDEX idx_student_skills_student    ON student_skills (student_id);
CREATE INDEX idx_student_interests_student ON student_interests (student_id);
