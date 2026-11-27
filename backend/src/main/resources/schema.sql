-- Taskdle Schema for Supabase PostgreSQL
CREATE TABLE IF NOT EXISTS usuarios (
    nombre VARCHAR(50) PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS tareas_recurrentes (
    id SERIAL PRIMARY KEY,
    dueno VARCHAR(50) NOT NULL REFERENCES usuarios(nombre) ON DELETE CASCADE,
    frecuencia VARCHAR(10) NOT NULL CHECK (frecuencia IN ('DIARIA', 'SEMANAL', 'MENSUAL')),
    fecha_base TIMESTAMPTZ NOT NULL,
    zona_horaria VARCHAR(64) NOT NULL,
    activa BOOLEAN NOT NULL DEFAULT TRUE
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
    fecha_limite TIMESTAMPTZ,
    recurrencia_id INT REFERENCES tareas_recurrentes(id) ON DELETE SET NULL,
    indice_recurrencia INT
);

ALTER TABLE tareas ADD COLUMN IF NOT EXISTS recurrencia_id INT REFERENCES tareas_recurrentes(id) ON DELETE SET NULL;
ALTER TABLE tareas ADD COLUMN IF NOT EXISTS indice_recurrencia INT;
ALTER TABLE tareas ADD COLUMN IF NOT EXISTS inicio_planificado TIMESTAMPTZ;
ALTER TABLE tareas ADD COLUMN IF NOT EXISTS fin_planificado TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS idx_tareas_recurrencia_indice
    ON tareas(recurrencia_id, indice_recurrencia)
    WHERE recurrencia_id IS NOT NULL;

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

CREATE TABLE IF NOT EXISTS grupos (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    propietario VARCHAR(50) NOT NULL REFERENCES usuarios(nombre) ON DELETE CASCADE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(propietario, nombre)
);

CREATE TABLE IF NOT EXISTS grupo_miembros (
    grupo_id INT NOT NULL REFERENCES grupos(id) ON DELETE CASCADE,
    usuario VARCHAR(50) NOT NULL REFERENCES usuarios(nombre) ON DELETE CASCADE,
    PRIMARY KEY (grupo_id, usuario)
);

CREATE INDEX IF NOT EXISTS idx_grupo_miembros_usuario
    ON grupo_miembros(usuario);

CREATE INDEX IF NOT EXISTS idx_grupos_propietario
    ON grupos(propietario);
