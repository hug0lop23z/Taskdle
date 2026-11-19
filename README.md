# Taskdle

Taskdle es una aplicación web para organizar tareas personales y compartidas. Incluye subtareas, fechas límite con hora, calendario y sincronización entre dispositivos.

> Las imágenes muestran la interfaz con datos ficticios de demostración.

## Capturas

### Panel de tareas

![Panel de tareas de Taskdle en escritorio](docs/images/taskdle-escritorio.png)

### Calendario semanal

![Vista semanal del calendario de Taskdle](docs/images/taskdle-calendario.png)

### Vista móvil

<img src="docs/images/taskdle-movil.png" alt="Taskdle en un móvil" width="320">

## Funcionalidades

- Crear tareas simples o con fecha y hora límite.
- Añadir subtareas y seguir su progreso.
- Ver tareas en lista o en calendario mensual, semanal y diario.
- Crear una tarea directamente desde una fecha u hora del calendario.
- Filtrar tareas por estado y vencimiento, seleccionar varias y eliminarlas en bloque.
- Compartir tareas con otros usuarios.
- Consultar rachas de tareas completadas.
- Exportar tareas a CSV o imprimirlas/guardarlas como PDF.
- Usar tema claro u oscuro y una interfaz responsive.
- Continuar viendo las tareas guardadas sin conexión y sincronizar cambios pendientes al reconectar.
- Instalar la aplicación como PWA desde un navegador compatible.

## Arquitectura

```text
Navegador / PWA
      │
      ├── Frontend estático: HTML, CSS y JavaScript
      │
      └── API REST: backend Java 17
                   │
                   └── PostgreSQL (Supabase) o almacenamiento temporal en memoria
```

En despliegue, el frontend está preparado para Vercel y el backend para Render. Consulta la [guía de despliegue](DEPLOY.md) para configurarlos.

## Tecnologías

- **Frontend:** HTML, CSS y JavaScript sin framework; Service Worker y Web App Manifest.
- **Backend:** Java 17, `HttpServer` y JDBC.
- **Base de datos:** PostgreSQL y driver JDBC de PostgreSQL.
- **Despliegue:** Vercel (frontend), Render (backend) y Supabase (base de datos).

## Ejecución local

Requisitos: Java 17 o superior, Gradle instalado y Python 3 para servir el frontend.

### 1. Iniciar el backend

Desde PowerShell, en la raíz del repositorio:

```powershell
Set-Location backend
$env:PORT = "8080"
# Opcional: configura DATABASE_URL para usar PostgreSQL.
gradle run
```

Si `DATABASE_URL` no está configurada, el backend usa almacenamiento en memoria; los datos se pierden al detener el proceso.

Para usar PostgreSQL, configura `DATABASE_URL` en el entorno antes de iniciar el backend y crea las tablas ejecutando [`backend/src/main/resources/schema.sql`](backend/src/main/resources/schema.sql). No guardes credenciales en el repositorio.

### 2. Servir el frontend

En otra terminal, desde la raíz:

```powershell
python -m http.server 3000 --directory frontend/public
```

Abre <http://localhost:3000>. Al servirse desde `localhost`, el frontend usa por defecto el backend local en `http://localhost:8080`. Para probar la instalación PWA, utiliza `localhost` o sirve la aplicación mediante HTTPS.

## Base de datos existente

Las nuevas instalaciones deben usar el tipo `TIMESTAMPTZ` para `tareas.fecha_limite`, tal como define el esquema actual.

Si tu base de datos conserva la columna antigua `DATE`, ejecuta esta migración **una sola vez antes de desplegar la versión del backend que guarda la hora**. Las fechas existentes se convierten a las 23:59 de Madrid:

```sql
ALTER TABLE tareas
ALTER COLUMN fecha_limite TYPE TIMESTAMPTZ
USING (
  (fecha_limite::timestamp + TIME '23:59:00')
  AT TIME ZONE 'Europe/Madrid'
);
```

## API principal

| Método | Ruta | Uso |
|---|---|---|
| `POST` | `/api/login` | Iniciar sesión |
| `POST` | `/api/registro` | Crear una cuenta |
| `GET` | `/api/tareas?usuario=...` | Listar tareas |
| `POST` | `/api/tareas` | Crear una tarea |
| `PUT` | `/api/tareas/{id}/estado` | Cambiar el estado |
| `PUT` | `/api/tareas/{id}/subtarea` | Actualizar una subtarea |
| `POST` | `/api/tareas/{id}/compartir` | Compartir una tarea |
| `DELETE` | `/api/tareas/{id}?usuario=...` | Eliminar una tarea o dejar de compartirla |
| `GET` | `/api/racha?usuario=...` | Consultar la racha |
| `GET` | `/health` | Comprobar que el servicio está activo |

## Estructura

```text
.
├── backend/
│   ├── src/main/java/com/taskdle/
│   │   ├── api/          # API HTTP
│   │   ├── db/           # Acceso a datos PostgreSQL
│   │   ├── model/        # Usuarios, tareas y subtareas
│   │   └── service/      # Lógica de negocio
│   ├── src/main/resources/schema.sql
│   ├── build.gradle
│   └── Dockerfile
├── docs/images/          # Capturas de la interfaz
├── frontend/public/      # Aplicación web y recursos PWA
├── DEPLOY.md             # Guía de despliegue detallada
└── render.yaml           # Configuración de Render
```

## Configuración y seguridad

- `PORT`: puerto HTTP del backend; por defecto, `8080`.
- `DATABASE_URL`: URI JDBC/URI PostgreSQL usada para conectar con la base de datos.
- El frontend determina la URL de API según el host; en local utiliza `localhost:8080`.
- No publiques contraseñas, cadenas de conexión ni datos personales en Git.

Taskdle es un proyecto educativo. Antes de exponerlo a usuarios reales, revisa el modelo de autenticación y las medidas de seguridad de la API.
