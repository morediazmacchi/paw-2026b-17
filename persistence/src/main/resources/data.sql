INSERT INTO category (name) VALUES ('Musica') ON CONFLICT (name) DO NOTHING;
INSERT INTO category (name) VALUES ('Deportes') ON CONFLICT (name) DO NOTHING;
INSERT INTO category (name) VALUES ('Arte') ON CONFLICT (name) DO NOTHING;
INSERT INTO category (name) VALUES ('Tecnologia') ON CONFLICT (name) DO NOTHING;
INSERT INTO category (name) VALUES ('Gastronomia') ON CONFLICT (name) DO NOTHING;
INSERT INTO category (name) VALUES ('Otros') ON CONFLICT (name) DO NOTHING;

-- Usuario admin semilla (solo se inserta si no existe: ON CONFLICT DO NOTHING).
-- La password NO se documenta aca: es de desarrollo y hay que cambiarla en cada
-- entorno real (UPDATE users SET password = <hash bcrypt> WHERE email = ...).
-- El valor de abajo es el hash, hecho con el mismo
-- BCryptPasswordEncoder de Spring (prefijo $2a$, no el $2y$ de htpasswd/otras libs:
-- son compatibles en la practica, pero mejor no mezclar variantes sin necesidad).
INSERT INTO users (email, name, password)
VALUES ('admin@plancito.com', 'Admin', '$2a$10$VSCqxBYGg2RnvPQaEJOxCu1oFwTPJdUBJtrqp8m07ySbUUSKm6dw6')
ON CONFLICT (email) DO NOTHING;

INSERT INTO user_role (user_id, role)
SELECT id, 'ROLE_USER' FROM users WHERE email = 'admin@plancito.com'
ON CONFLICT (user_id, role) DO NOTHING;

INSERT INTO user_role (user_id, role)
SELECT id, 'ROLE_ADMIN' FROM users WHERE email = 'admin@plancito.com'
ON CONFLICT (user_id, role) DO NOTHING;

-- Superadmin: ademas de ROLE_ADMIN (arriba, que le da todas las capacidades de
-- un admin comun), este rol lo deja otorgar o revocar ROLE_ADMIN a otros
-- usuarios (ver WebAuthConfig/AdminController). No hay UI para crear otro
-- superadmin: es intencional, solo esta cuenta semilla lo tiene.
INSERT INTO user_role (user_id, role)
SELECT id, 'ROLE_SUPERADMIN' FROM users WHERE email = 'admin@plancito.com'
ON CONFLICT (user_id, role) DO NOTHING;
