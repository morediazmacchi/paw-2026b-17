INSERT INTO category (name) VALUES ('Musica') ON CONFLICT (name) DO NOTHING;
INSERT INTO category (name) VALUES ('Deportes') ON CONFLICT (name) DO NOTHING;
INSERT INTO category (name) VALUES ('Arte') ON CONFLICT (name) DO NOTHING;
INSERT INTO category (name) VALUES ('Tecnologia') ON CONFLICT (name) DO NOTHING;
INSERT INTO category (name) VALUES ('Gastronomia') ON CONFLICT (name) DO NOTHING;
INSERT INTO category (name) VALUES ('Otros') ON CONFLICT (name) DO NOTHING;
