INSERT INTO categoria (nombre) VALUES ('Musica') ON CONFLICT (nombre) DO NOTHING;
INSERT INTO categoria (nombre) VALUES ('Deportes') ON CONFLICT (nombre) DO NOTHING;
INSERT INTO categoria (nombre) VALUES ('Arte') ON CONFLICT (nombre) DO NOTHING;
INSERT INTO categoria (nombre) VALUES ('Tecnologia') ON CONFLICT (nombre) DO NOTHING;
INSERT INTO categoria (nombre) VALUES ('Gastronomia') ON CONFLICT (nombre) DO NOTHING;
INSERT INTO categoria (nombre) VALUES ('Otros') ON CONFLICT (nombre) DO NOTHING;
