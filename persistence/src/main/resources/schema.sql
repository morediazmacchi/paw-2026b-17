CREATE TABLE IF NOT EXISTS categoria (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS evento (
    id SERIAL PRIMARY KEY,
    titulo VARCHAR(150) NOT NULL,
    descripcion TEXT,
    fecha_evento TIMESTAMP NOT NULL,
    direccion VARCHAR(200) NOT NULL,
    barrio VARCHAR(80),
    imagen_url VARCHAR(300),
    precio NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (precio >= 0),
    cupo_maximo INTEGER CHECK (cupo_maximo > 0),
    creador_email VARCHAR(150) NOT NULL,
    categoria_id INTEGER NOT NULL REFERENCES categoria(id),
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS inscripcion (
    id SERIAL PRIMARY KEY,
    evento_id INTEGER NOT NULL REFERENCES evento(id) ON DELETE CASCADE,
    email VARCHAR(150) NOT NULL,
    nombre VARCHAR(150),
    fecha_inscripcion TIMESTAMP NOT NULL DEFAULT now(),
    estado VARCHAR(20) NOT NULL DEFAULT 'confirmada' CHECK (estado IN ('confirmada','espera')),
    UNIQUE(evento_id, email)
);
