# Taskdle

<p align="center">
  <strong>Menos tareas en la cabeza. Más claridad para avanzar.</strong><br>
  Una aplicación web para organizar tareas personales y compartidas, planificar fechas límite y seguir el progreso desde cualquier dispositivo.
</p>

Taskdle reúne en un mismo lugar lo que tienes pendiente, lo que ya está en marcha y lo que has terminado. Está pensada para convertir una lista de obligaciones en un plan manejable: divide el trabajo en pasos, decide qué requiere atención primero y reserva tiempo para las tareas con fecha límite.

La interfaz está en español y se adapta a escritorio, tablet y móvil. Las imágenes de este documento muestran datos ficticios de demostración.

## Qué puedes hacer con Taskdle

- **Organizar el trabajo:** crea tareas, añade una descripción y asigna una prioridad y un estado.
- **Dividir tareas grandes:** agrega subtareas y consulta su avance sin perder de vista el objetivo principal.
- **Planificar con fechas:** establece una fecha y una hora límite; consulta tus tareas en calendario mensual, semanal o diario.
- **Actuar desde el calendario:** selecciona un día u hora para iniciar una tarea con esa fecha ya preparada.
- **Colaborar y revisar:** comparte tareas, filtra por estado o vencimiento y gestiona varias tareas a la vez.
- **Mantener el ritmo:** consulta el resumen de actividad y la racha de tareas completadas.
- **Llevarte la información:** exporta la lista a CSV o imprime y guarda una copia en PDF.

## Una vista clara del trabajo

El panel reúne el resumen de tareas, los filtros y las acciones frecuentes. En cada tarjeta puedes consultar el estado, la prioridad, la fecha límite y, cuando corresponda, el avance de las subtareas.

![Panel de tareas de Taskdle con prioridades, estados y subtareas](docs/images/taskdle-escritorio.png)

## Cómo se organiza el día

El calendario convierte las fechas límite en una planificación visual. Puedes cambiar entre las vistas de **mes**, **semana** y **día**; en la vista diaria, las tareas con hora se sitúan en su franja correspondiente.

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

```text
Navegador / PWA
  ├─ Interfaz estática: HTML, CSS y JavaScript
  ├─ Caché local y cola de cambios sin conexión
  └─ API REST
       └─ Backend Java 17
            └─ PostgreSQL (por ejemplo, Supabase)
```

1. **La interfaz** se sirve como contenido estático. No necesita un proceso de compilación del frontend.
2. **El navegador** llama a la API REST para iniciar sesión, cargar tareas y enviar cambios. En desarrollo local, la API apunta a `http://localhost:8080`; en despliegue, la URL se resuelve según el host y la configuración de la aplicación.
3. **El backend Java** procesa las operaciones de usuarios, tareas, subtareas, fechas límite y tareas compartidas.
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

El esquema actual define `tareas.fecha_limite` como `TIMESTAMPTZ` para guardar tanto la fecha como la hora. Si tu tabla aún tiene esa columna como `DATE`, ejecuta la siguiente migración **una sola vez y antes de desplegar la versión del backend que guarda la hora**. Las fechas previas se convertirán a las 23:59 de Madrid:

```sql
ALTER TABLE tareas
ALTER COLUMN fecha_limite TYPE TIMESTAMPTZ
USING (
  (fecha_limite::timestamp + TIME '23:59:00')
  AT TIME ZONE 'Europe/Madrid'
);
```

## API principal

| Método | Ruta | Operación |
|---|---|---|
| `POST` | `/api/login` | Iniciar sesión |
| `POST` | `/api/registro` | Crear una cuenta |
| `GET` | `/api/tareas?usuario=...` | Consultar tareas |
| `POST` | `/api/tareas` | Crear una tarea |
| `PUT` | `/api/tareas/{id}/estado` | Cambiar el estado |
| `PUT` | `/api/tareas/{id}/subtarea` | Actualizar una subtarea |
| `POST` | `/api/tareas/{id}/compartir` | Compartir una tarea |
| `DELETE` | `/api/tareas/{id}?usuario=...` | Eliminar una tarea o dejar de compartirla |
| `GET` | `/api/racha?usuario=...` | Consultar la racha |
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
