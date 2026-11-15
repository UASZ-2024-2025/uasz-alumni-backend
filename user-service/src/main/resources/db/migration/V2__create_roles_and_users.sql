-- Migration V2 : Création des tables roles, role_permissions et users
CREATE TABLE roles (
    name VARCHAR(50) PRIMARY KEY,
    description VARCHAR(255)
);

CREATE TABLE role_permissions (
    role_name VARCHAR(50) NOT NULL REFERENCES roles(name) ON DELETE CASCADE,
    permission VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_name, permission)
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    last_name VARCHAR(100) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role_name VARCHAR(50) NOT NULL REFERENCES roles(name),
    status VARCHAR(30) NOT NULL DEFAULT 'EN_ATTENTE',
    phone VARCHAR(50),
    gender VARCHAR(20),
    birth_date DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_role_status ON users(role_name, status);
CREATE INDEX idx_users_email ON users(email);

-- Rôles fondamentaux
INSERT INTO roles (name, description) VALUES
    ('ADMINISTRATEUR', 'Administrateur de la plateforme Alumni UASZ'),
    ('ALUMNI', 'Diplômé de l''université Assane Seck de Ziguinchor'),
    ('ETUDIANT', 'Étudiant inscrit à l''université Assane Seck de Ziguinchor'),
    ('ENSEIGNANT', 'Enseignant ou personnel académique');

-- Permissions associées
INSERT INTO role_permissions (role_name, permission) VALUES
    ('ADMINISTRATEUR', 'USER_READ'),
    ('ADMINISTRATEUR', 'USER_WRITE'),
    ('ADMINISTRATEUR', 'USER_DELETE'),
    ('ADMINISTRATEUR', 'USER_STATUS_UPDATE'),
    ('ADMINISTRATEUR', 'ROLE_READ'),
    ('ALUMNI', 'USER_READ_SELF'),
    ('ALUMNI', 'USER_WRITE_SELF'),
    ('ETUDIANT', 'USER_READ_SELF'),
    ('ETUDIANT', 'USER_WRITE_SELF'),
    ('ENSEIGNANT', 'USER_READ_SELF'),
    ('ENSEIGNANT', 'USER_WRITE_SELF');

-- Administrateur initial (admin@uasz.sn / Admin1234!)
INSERT INTO users (id, last_name, first_name, email, password_hash, role_name, status, created_at, updated_at)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'Administrateur',
    'Système',
    'admin@uasz.sn',
    '$2a$10$wE9K2j02qG9Y/T4iV3sB2ecQhTsmg7i1K8PjDk/0W76w8j040pL3O',
    'ADMINISTRATEUR',
    'ACTIF',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

