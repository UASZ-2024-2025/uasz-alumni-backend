-- ALUM-109 : CV de l'étudiant, un par profil (students.id), remplacé en entier à chaque PUT.
-- Les sections gardent l'ordre saisi (sort_order). Son id est le cvId des candidatures (job-offer-service).
CREATE TABLE student_cvs (
    id         UUID                     PRIMARY KEY,
    student_id UUID                     NOT NULL UNIQUE REFERENCES students (id) ON DELETE CASCADE,
    title      VARCHAR(150)             NOT NULL,
    template   VARCHAR(50)              NOT NULL,
    language   VARCHAR(50)              NOT NULL,
    summary    VARCHAR(4000),
    file_url   VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE student_cv_skills (
    cv_id      UUID         NOT NULL REFERENCES student_cvs (id) ON DELETE CASCADE,
    sort_order INTEGER      NOT NULL,
    skill      VARCHAR(100) NOT NULL,
    PRIMARY KEY (cv_id, sort_order)
);

CREATE TABLE student_cv_experiences (
    cv_id        UUID    NOT NULL REFERENCES student_cvs (id) ON DELETE CASCADE,
    sort_order   INTEGER NOT NULL,
    company_name VARCHAR(150),
    job_title    VARCHAR(150),
    start_date   DATE,
    end_date     DATE,
    description  VARCHAR(2000),
    PRIMARY KEY (cv_id, sort_order)
);

CREATE TABLE student_cv_educations (
    cv_id          UUID    NOT NULL REFERENCES student_cvs (id) ON DELETE CASCADE,
    sort_order     INTEGER NOT NULL,
    institution    VARCHAR(200),
    degree         VARCHAR(150),
    field_of_study VARCHAR(150),
    start_date     DATE,
    end_date       DATE,
    PRIMARY KEY (cv_id, sort_order)
);

CREATE TABLE student_cv_languages (
    cv_id       UUID        NOT NULL REFERENCES student_cvs (id) ON DELETE CASCADE,
    sort_order  INTEGER     NOT NULL,
    language    VARCHAR(50),
    proficiency VARCHAR(50),
    PRIMARY KEY (cv_id, sort_order)
);
