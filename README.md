# Taskdle

<p align="center">
  <strong>Menos tareas en la cabeza. Más claridad para avanzar.</strong><br>
  Una aplicación web para organizar tareas personales y compartidas, planificar fechas límite y seguir el progreso desde cualquier dispositivo.
</p>

<p align="center">
  <img src="docs/images/readme-cover.svg" alt="Taskdle: organiza tareas con calma, prioriza y planifica desde un panel claro" width="1200">
</p>

Taskdle reúne lo que tienes pendiente, lo que está en marcha y lo que has terminado. Está pensada para convertir una lista de obligaciones en un plan manejable: divide el trabajo en pasos, decide qué requiere atención primero y reserva tiempo para las tareas con fecha límite.

La interfaz está en español y se adapta a escritorio, tablet y móvil. Las capturas muestran datos ficticios de demostración.

## De la idea a la tarea completada

El flujo de Taskdle ayuda a pasar de una nota rápida a un plan que puedes seguir:

<p align="center">
  <img src="docs/images/readme-flow.svg" alt="Flujo de Taskdle: captura, organiza, planifica y avanza" width="1200">
</p>

## Qué puedes hacer con Taskdle

- **Organizar el trabajo:** crea tareas, añade una descripción y asigna una prioridad y un estado.
- **Corregir tareas:** edita el título, la descripción, la prioridad, la fecha límite y las subtareas; en una serie recurrente, los cambios afectan a la ocurrencia seleccionada y a las siguientes pendientes.
- **Dividir tareas grandes:** agrega subtareas y consulta su avance sin perder de vista el objetivo principal.
- **Planificar con fechas:** establece una fecha y una hora límite sin confundirlas con el bloque de trabajo; programa inicio y fin y consulta tus tareas en calendario mensual, semanal o diario.
- **Actuar desde el calendario:** selecciona un día u hora para iniciar una tarea con esa fecha ya preparada.
- **Reorganizar sobre la marcha:** arrastra una tarea a otro día para cambiar su fecha límite; en la vista diaria puedes moverla a otra hora.
- **Programar tareas recurrentes:** repite una tarea cada día, semana o mes; mantiene 12 ocurrencias programadas y añade otra al completar una.
- **Colaborar y revisar:** comparte tareas, filtra por estado o vencimiento y gestiona varias tareas a la vez.
- **Compartir por grupos:** crea grupos como Familia o Trabajo, añade amigos registrados y comparte una tarea con todo el grupo.
- **Coordinar responsabilidades:** asigna cada tarea a su propietario o a una persona con quien ya esté compartida; consulta un historial breve de actividad.
- **Llevarte la información:** exporta la lista a CSV o imprime y guarda una copia en PDF.

## Una vista clara del trabajo

En la parte superior, el panel muestra las tareas con fecha límite de hoy y de los próximos seis días. En cada tarjeta puedes consultar el estado, la prioridad, la fecha límite y, cuando corresponda, el avance de las subtareas.

![Panel de tareas de Taskdle con prioridades, estados y subtareas](docs/images/taskdle-escritorio.png)

## Cómo se organiza el día

El calendario muestra los bloques de trabajo en su horario reservado y conserva las fechas límite como referencias independientes. Puedes cambiar entre las vistas de **mes**, **semana** y **día**; en la vista diaria, los bloques se sitúan en su franja correspondiente.

Al seleccionar una fecha —o una hora en la vista diaria— se abre el formulario para crear una tarea con la fecha elegida. Así puedes planificar y registrar una tarea en el mismo flujo.

![Calendario semanal de Taskdle con las tareas ubicadas en sus fechas límite](docs/images/taskdle-calendario.png)

## También en móvil

Taskdle adapta los controles al espacio disponible: las secciones de resumen, filtros y detalles se pueden plegar para dejar más sitio a las tareas. También puede instalarse como aplicación web progresiva (PWA) desde un navegador compatible.

El Service Worker permite cargar la aplicación y consultar tareas guardadas cuando no hay conexión. Si se realizan cambios sin conexión, el frontend los encola e intenta sincronizarlos al reconectar.

<p align="center">
  <img src="docs/images/taskdle-movil.png" alt="Panel de Taskdle adaptado a una pantalla móvil" width="312">
</p>

## Cómo funciona

Taskdle separa la interfaz, las operaciones de negocio y el almacenamiento:

```mermaid
flowchart LR
  Browser["Navegador / PWA"] --> API["API REST"]
  API --> Java["Backend Java 17"]
  Java --> DB["PostgreSQL / Supabase"]
  Browser -. "caché y cola sin conexión" .-> Local["Almacenamiento local"]

  classDef client fill:#e7eeea,stroke:#52796f,color:#26332e,stroke-width:2px
  classDef api fill:#eef3f3,stroke:#5f8490,color:#26332e,stroke-width:2px
  classDef backend fill:#f5f1e9,stroke:#a77a3b,color:#26332e,stroke-width:2px
  classDef database fill:#eef4ef,stroke:#54806c,color:#26332e,stroke-width:2px
  classDef local fill:#f1f4f1,stroke:#73817a,color:#26332e,stroke-width:2px

  class Browser client
  class API api
  class Java backend
  class DB database
  class Local local
```

1. **La interfaz** se sirve como contenido estático. No necesita un proceso de compilación del frontend.
2. **El navegador** llama a la API REST para iniciar sesión, cargar tareas y enviar cambios. En desarrollo local, la API apunta a `http://localhost:8080`; en despliegue, la URL se resuelve según el host y la configuración de la aplicación.
3. **El backend Java** procesa las operaciones de usuarios, tareas, subtareas, fechas límite, repeticiones y tareas compartidas.
4. **La persistencia** se realiza en PostgreSQL cuando está configurada la variable `DATABASE_URL`. Sin ella, el backend funciona con almacenamiento en memoria, que se pierde al detener el proceso.

### Tecnologías

| Componente | Tecnología |
|---|---|
| Interfaz | HTML, CSS y JavaScript |
| API | Java 17, `HttpServer` y JDBC |
| Persistencia | PostgreSQL |
| Aplicación instalable | Web App Manifest y Service Worker |
| Despliegue previsto | Vercel, Render y Supabase |

## Ejecutar en local

### Requisitos

- Java 17 o superior
- Gradle instalado
- Python 3 para servir la interfaz localmente
- PostgreSQL si quieres conservar los datos entre reinicios

### 1. Iniciar el backend

Desde la raíz del repositorio, abre una terminal y ejecuta:

```powershell
Set-Location backend
$env:PORT = "8080"
```

Si quieres usar PostgreSQL, configura `DATABASE_URL` antes de iniciar el backend. En PowerShell:

```powershell
$env:DATABASE_URL = "postgresql://USUARIO:CONTRASENA@HOST:5432/BASE_DE_DATOS"
```

En una base de datos nueva, crea las tablas con [`backend/src/main/resources/schema.sql`](backend/src/main/resources/schema.sql). Si no configuras `DATABASE_URL`, las tareas se almacenan solo en memoria y se pierden al detener el backend.

Inicia el servidor:

```powershell
gradle run
```

### 2. Servir la interfaz

Abre otra terminal desde la raíz del repositorio:

```powershell
python -m http.server 3000 --directory frontend/public
```

Visita <http://localhost:3000>. Sirve la interfaz desde `localhost` en lugar de abrir el HTML como archivo para que pueda comunicarse con el backend local. Para probar la instalación PWA, utiliza `localhost` o HTTPS.

## Migración de una base de datos existente

### Crear la estructura de grupos

Si tu base de datos ya está creada, ejecuta una sola vez esta consulta antes de desplegar la mejora de grupos:

```sql
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
```

Las bases de datos nuevas ya crean estas tablas mediante [`backend/src/main/resources/schema.sql`](backend/src/main/resources/schema.sql).

### Añadir tareas recurrentes

Si tu base de datos ya existe, ejecuta esta migración una vez. También está incluida en `schema.sql` para instalaciones nuevas:

```sql
CREATE TABLE IF NOT EXISTS tareas_recurrentes (
    id SERIAL PRIMARY KEY,
    dueno VARCHAR(50) NOT NULL REFERENCES usuarios(nombre) ON DELETE CASCADE,
    frecuencia VARCHAR(10) NOT NULL CHECK (frecuencia IN ('DIARIA', 'SEMANAL', 'MENSUAL')),
    fecha_base TIMESTAMPTZ NOT NULL,
    zona_horaria VARCHAR(64) NOT NULL,
    activa BOOLEAN NOT NULL DEFAULT TRUE
);

ALTER TABLE tareas
    ADD COLUMN IF NOT EXISTS recurrencia_id INT
        REFERENCES tareas_recurrentes(id) ON DELETE SET NULL;
ALTER TABLE tareas
    ADD COLUMN IF NOT EXISTS indice_recurrencia INT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_tareas_recurrencia_indice
    ON tareas(recurrencia_id, indice_recurrencia)
    WHERE recurrencia_id IS NOT NULL;
```

La repetición mensual conserva el día original y utiliza el último día disponible en meses más cortos. Al detener una serie se conservan las tareas ya completadas y la ocurrencia seleccionada; se eliminan las siguientes que aún no estén completadas.

El esquema actual define `tareas.fecha_limite` como `TIMESTAMPTZ` para guardar tanto la fecha como la hora. Si tu tabla aún tiene esa columna como `DATE`, ejecuta la siguiente migración **una sola vez y antes de desplegar la versión del backend que guarda la hora**. Las fechas previas se convertirán a las 23:59 de Madrid:

```sql
ALTER TABLE tareas
ALTER COLUMN fecha_limite TYPE TIMESTAMPTZ
USING (
  (fecha_limite::timestamp + TIME '23:59:00')
  AT TIME ZONE 'Europe/Madrid'
);
```

Las columnas `inicio_planificado`, `fin_planificado` y `responsable`, además de la tabla `tareas_historial`, se añaden automáticamente al iniciar el backend mediante `schema.sql`. Los bloques de trabajo se consultan y reorganizan desde el calendario interno de Taskdle. El historial conserva eventos de creación, asignación, edición, estado, fechas y subtareas; se muestran los 20 más recientes.

## API principal

| Método | Ruta | Operación |
|---|---|---|
| `POST` | `/api/login` | Iniciar sesión |
| `POST` | `/api/registro` | Crear una cuenta |
| `GET` | `/api/tareas?usuario=...` | Consultar tareas |
| `POST` | `/api/tareas` | Crear una tarea |
| `PUT` | `/api/tareas/{id}` | Editar una tarea propia y sus subtareas |
| `PUT` | `/api/tareas/{id}/fecha` | Cambiar la fecha y hora límite de una tarea propia |
| `PUT` | `/api/tareas/{id}/responsable` | Asignar o retirar un responsable de una tarea propia |
| `GET` | `/api/tareas/{id}/colaboradores?usuario=...` | Consultar quién tiene acceso a la tarea |
| `GET` | `/api/tareas/{id}/historial?usuario=...` | Consultar la actividad reciente de una tarea compartida |
| `PUT` | `/api/tareas/{id}/estado` | Cambiar el estado |
| `PUT` | `/api/tareas/{id}/repeticion/detener` | Detener una serie recurrente propia |
| `PUT` | `/api/tareas/{id}/subtarea` | Actualizar una subtarea |
| `POST` | `/api/tareas/{id}/compartir` | Compartir una tarea |
| `DELETE` | `/api/tareas/{id}?usuario=...` | Eliminar una tarea o dejar de compartirla |
| `GET` | `/api/grupos?usuario=...` | Consultar los grupos propios y compartidos |
| `POST` | `/api/grupos` | Crear un grupo |
| `POST` | `/api/grupos/{id}/miembros` | Añadir un amigo al grupo |
| `DELETE` | `/api/grupos/{id}/miembros` | Retirar un amigo del grupo |
| `POST` | `/api/grupos/{id}/tareas/{tarea}` | Compartir una tarea con todos los miembros |
| `GET` | `/health` | Comprobar que el backend responde |

## Estructura del proyecto

```text
.
├── backend/
│   ├── src/main/java/com/taskdle/
│   │   ├── api/          # API HTTP
│   │   ├── db/           # Acceso a datos
│   │   ├── model/        # Usuarios, tareas y subtareas
│   │   └── service/      # Lógica de negocio
│   ├── src/main/resources/schema.sql
│   ├── build.gradle
│   └── Dockerfile
├── docs/images/          # Capturas incluidas en este README
├── frontend/public/      # Interfaz y recursos PWA
├── DEPLOY.md             # Instrucciones de despliegue
└── render.yaml
```

Para desplegar los servicios en Vercel, Render y Supabase, consulta la [guía de despliegue](DEPLOY.md). No guardes contraseñas, cadenas de conexión ni datos personales en el repositorio.
