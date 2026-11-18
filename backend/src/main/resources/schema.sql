-- Taskdle Schema for Supabase PostgreSQL
CREATE TABLE IF NOT EXISTS usuarios (
    nombre VARCHAR(50) PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL,
    racha_actual INT NOT NULL DEFAULT 0,
    mejor_racha INT NOT NULL DEFAULT 0,
    ultima_fecha_completada DATE
);

CREATE TABLE IF NOT EXISTS tareas (
    id SERIAL PRIMARY KEY,
    dueno VARCHAR(50) NOT NULL REFERENCES usuarios(nombre) ON DELETE CASCADE,
    tipo VARCHAR(20) NOT NULL DEFAULT 'SIMPLE',
    titulo VARCHAR(255) NOT NULL,
    descripcion TEXT DEFAULT '',
    prioridad VARCHAR(10) NOT NULL DEFAULT 'MEDIA',
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    fecha_creacion TIMESTAMP NOT NULL DEFAULT NOW(),
    fecha_limite DATE
);

CREATE TABLE IF NOT EXISTS subtareas (
    id SERIAL PRIMARY KEY,
    tarea_id INT NOT NULL REFERENCES tareas(id) ON DELETE CASCADE,
    titulo VARCHAR(255) NOT NULL,
    completada BOOLEAN NOT NULL DEFAULT FALSE,
    orden INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tareas_compartidas (
    tarea_id INT NOT NULL REFERENCES tareas(id) ON DELETE CASCADE,
    usuario VARCHAR(50) NOT NULL REFERENCES usuarios(nombre) ON DELETE CASCADE,
    PRIMARY KEY (tarea_id, usuario)
);
