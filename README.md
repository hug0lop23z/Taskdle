# Gestor de Tareas — To-Do List en Java

Aplicación de consola desarrollada en **Java puro (sin frameworks)** que implementa
un sistema multiusuario de gestión de tareas con principios sólidos de POO.

---

## Requisitos

- **Java 17** o superior (usa switch expressions y pattern matching de instanceof)

---

## Compilar y Ejecutar

```bash
# Dentro de la carpeta TodoApp/
javac *.java
java Main
```

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

Consola            → utilidad estática de I/O (DRY)
Estadisticas       → value object inmutable con barra de progreso visual
Prioridad (enum)   → ALTA | MEDIA | BAJA
Estado    (enum)   → PENDIENTE | EN_PROGRESO | COMPLETADA

MenuPrincipal  → login / registro → delega a ↓
MenuTareas     → CRUD + filtros + estadísticas (solo tras autenticación)
Main           → new MenuPrincipal().iniciar()
```

---

## Principios de POO Aplicados

| Principio        | Dónde se aplica                                                                |
|------------------|--------------------------------------------------------------------------------|
| **Abstracción**  | `Tarea` define el contrato sin implementar `getTipoTarea()`/`getInfoAdicional()` |
| **Herencia**     | `TareaSimple` y `TareaConFechaLimite` extienden `Tarea` via `super()`          |
| **Polimorfismo** | `Tarea.toString()` llama a `getInfoAdicional()` → se ejecuta la versión de la subclase |
| **Encapsulamiento** | Todos los campos son `private`; acceso controlado por getters/setters      |

---

## Funcionalidades

- ✅ Registro y login de múltiples usuarios (datos aislados por usuario)
- ✅ Crear tareas simples o con fecha límite
- ✅ Listar, cambiar estado y eliminar tareas
- ✅ Filtrar por **Estado** (Pendiente / En Progreso / Completada)
- ✅ Filtrar por **Prioridad** (Alta / Media / Baja)
- ✅ Estadísticas con barra de progreso visual y detección de tareas vencidas
- ✅ Alertas automáticas de vencimiento (hoy, mañana, esta semana, vencida)
