-- Ejecutar una vez en Supabase para eliminar los datos históricos de rachas.
ALTER TABLE public.usuarios
    DROP COLUMN IF EXISTS racha_actual,
    DROP COLUMN IF EXISTS mejor_racha,
    DROP COLUMN IF EXISTS ultima_fecha_completada;
