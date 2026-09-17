INSERT INTO category (name) VALUES ('Musica') ON CONFLICT (name) DO NOTHING;
INSERT INTO category (name) VALUES ('Deportes') ON CONFLICT (name) DO NOTHING;
INSERT INTO category (name) VALUES ('Arte') ON CONFLICT (name) DO NOTHING;
INSERT INTO category (name) VALUES ('Tecnologia') ON CONFLICT (name) DO NOTHING;
INSERT INTO category (name) VALUES ('Gastronomia') ON CONFLICT (name) DO NOTHING;
INSERT INTO category (name) VALUES ('Otros') ON CONFLICT (name) DO NOTHING;

-- Usuario admin de prueba. Password: admin1234, hasheada con el mismo
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
