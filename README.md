# Gestor de Tareas — To-Do List en Java

Aplicación multiusuario de gestión de tareas desarrollada en **Java puro (sin frameworks)**.
Disponible en dos modos: **interfaz web** (recomendada) y **consola**.

---

## Requisitos

- **Java 17** o superior

---

## 🌐 Interfaz Web (recomendada)

Lanza un servidor HTTP integrado y abre el navegador automáticamente:

```bash
# Dentro de la carpeta del proyecto
javac *.java
java Main
```

Se abrirá **http://localhost:8080** con la interfaz gráfica.
Para detener el servidor: `Ctrl+C`.

### Características de la interfaz web

- 🔐 Login y registro de usuarios
- 📊 Dashboard con estadísticas en tiempo real (total, pendientes, en progreso, completadas, vencidas)
- 🗂 Tarjetas de tareas con colores por prioridad
- 🔍 Filtros por estado
- ➕ Crear tareas simples o con fecha límite
- 🔄 Cambiar estado directamente desde la tarjeta
- 🗑 Eliminar con confirmación

---

## 💻 Modo Consola (alternativa)

Para usar la interfaz de texto, edita `Main.java` y cambia:

```java
// De:
ServidorWeb.iniciar();
// A:
new MenuPrincipal().iniciar();
```

Luego compila y ejecuta:

```bash
javac *.java
java Main
```

---

## 💾 Persistencia de Datos

Toda la información (usuarios, contraseñas y tareas) se guarda automáticamente en `datos.csv`.
Al reiniciar el programa los datos se recuperan sin pérdida de información.

---

## Arquitectura y Jerarquía de Clases

```
Tarea  (abstract)
├── TareaSimple          → tarea básica sin fecha límite
└── TareaConFechaLimite  → añade LocalDate, detección de vencimiento y días restantes

Usuario
└── ArrayList<Tarea>     → lista propia e independiente por usuario

GestorUsuarios
└── HashMap<String, Usuario>   → base de datos en memoria (O(1) acceso)

PersistenciaArchivo → lectura/escritura de datos.csv
ServidorWeb         → servidor HTTP en puerto 8080 + API REST
Consola             → utilidad estática de I/O (DRY)
Estadisticas        → value object inmutable con barra de progreso visual
Prioridad (enum)    → ALTA | MEDIA | BAJA
Estado    (enum)    → PENDIENTE | EN_PROGRESO | COMPLETADA

MenuPrincipal  → login / registro → delega a ↓
MenuTareas     → CRUD + filtros + estadísticas (consola)
Main           → ServidorWeb.iniciar()
```

---

## Principios de POO Aplicados

| Principio           | Dónde se aplica                                                                        |
|---------------------|----------------------------------------------------------------------------------------|
| **Abstracción**     | `Tarea` define el contrato sin implementar `getTipoTarea()`/`getInfoAdicional()`       |
| **Herencia**        | `TareaSimple` y `TareaConFechaLimite` extienden `Tarea` via `super()`                 |
| **Polimorfismo**    | `Tarea.toString()` llama a `getInfoAdicional()` → se ejecuta la versión de la subclase|
| **Encapsulamiento** | Todos los campos son `private`; acceso controlado por getters/setters                  |

---

## Funcionalidades

- ✅ Registro y login de múltiples usuarios (datos aislados por usuario)
- ✅ Persistencia automática en archivo `datos.csv`
- ✅ Interfaz web moderna con dark theme
- ✅ Crear tareas simples o con fecha límite
- ✅ Listar, cambiar estado y eliminar tareas
- ✅ Filtrar por **Estado** (Pendiente / En Progreso / Completada)
- ✅ Filtrar por **Prioridad** (Alta / Media / Baja)
- ✅ Estadísticas con detección de tareas vencidas
- ✅ Alertas automáticas de vencimiento (hoy, mañana, esta semana, vencida)
