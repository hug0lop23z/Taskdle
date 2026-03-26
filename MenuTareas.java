import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Menú de gestión de tareas. Solo es accesible para un Usuario autenticado.
 * Encapsula todas las operaciones CRUD sobre tareas y las funcionalidades
 * de filtrado y estadísticas.
 */
public class MenuTareas {

    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Usuario usuario;

    public MenuTareas(Usuario usuario) {
        this.usuario = usuario;
    }

    // ─── Bucle principal ──────────────────────────────────────────────────

    public void iniciar() {
        boolean activo = true;
        while (activo) {
            mostrarMenu();
            int opcion = Consola.leerEntero("  → Opción: ");
            switch (opcion) {
                case 1 -> listarTodasLasTareas();
                case 2 -> crearTarea();
                case 3 -> cambiarEstadoTarea();
                case 4 -> eliminarTarea();
                case 5 -> filtrarTareas();
                case 6 -> verEstadisticas();
                case 0 -> activo = false;
                default -> { Consola.error("Opción no válida."); Consola.pausar(); }
            }
        }
        Consola.info("Sesión cerrada. ¡Hasta pronto, " + usuario.getNombreUsuario() + "!");
    }

    // ─── Menú ─────────────────────────────────────────────────────────────

    private void mostrarMenu() {
        Consola.limpiarPantalla();
        Consola.titulo("SESIÓN: " + usuario.getNombreUsuario().toUpperCase());
        long pendientes = usuario.getTareas().stream()
                                 .filter(t -> t.getEstado() == Estado.PENDIENTE).count();
        System.out.printf("  Tareas totales: %d  |  Pendientes: %d%n%n",
                          usuario.getTareas().size(), pendientes);
        System.out.println("    1.  📋  Ver todas las tareas");
        System.out.println("    2.  ➕  Crear nueva tarea");
        System.out.println("    3.  🔄  Cambiar estado de una tarea");
        System.out.println("    4.  🗑   Eliminar una tarea");
        System.out.println("    5.  🔍  Filtrar tareas");
        System.out.println("    6.  📊  Ver mis estadísticas");
        System.out.println("    0.  🚪  Cerrar sesión\n");
    }

    // ─── 1. Listar ────────────────────────────────────────────────────────

    private void listarTodasLasTareas() {
        imprimirListaDeTareas(usuario.getTareas(), "TODAS LAS TAREAS");
    }

    private void imprimirListaDeTareas(List<Tarea> tareas, String encabezado) {
        Consola.limpiarPantalla();
        Consola.subtitulo(encabezado + " (" + tareas.size() + ")");
        if (tareas.isEmpty()) {
            Consola.info("No hay tareas para mostrar.");
        } else {
            tareas.forEach(t -> System.out.println("\n" + t));
        }
        Consola.pausar();
    }

    // ─── 2. Crear ─────────────────────────────────────────────────────────

    private void crearTarea() {
        Consola.limpiarPantalla();
        Consola.subtitulo("CREAR NUEVA TAREA");

        System.out.println("\n  Tipo de tarea:");
        System.out.println("    1. Tarea Simple        (sin fecha límite)");
        System.out.println("    2. Tarea con Fecha Límite");
        System.out.println("    0. Cancelar\n");
        int tipo = Consola.leerEntero("  → Tipo: ");
        if (tipo == 0) return;

        String titulo = Consola.leerLinea("\n  Título       : ");
        if (titulo.isBlank()) {
            Consola.error("El título no puede estar vacío.");
            Consola.pausar();
            return;
        }
        String descripcion = Consola.leerLinea("  Descripción  : ");

        System.out.println("\n  Prioridad:");
        System.out.println("    1. ALTA   2. MEDIA   3. BAJA");
        int prioOpc    = Consola.leerEntero("  → Prioridad: ");
        Prioridad prio = Prioridad.fromInt(prioOpc);

        Tarea nuevaTarea;

        if (tipo == 2) {
            // TareaConFechaLimite: solicita la fecha y la parsea
            LocalDate fechaLimite = leerFecha("\n  Fecha límite (dd/MM/yyyy): ");
            if (fechaLimite == null) return;
            nuevaTarea = new TareaConFechaLimite(titulo, descripcion, prio, fechaLimite);
        } else {
            // TareaSimple por defecto
            nuevaTarea = new TareaSimple(titulo, descripcion, prio);
        }

        // El usuario agrega la tarea a su propia ArrayList
        usuario.agregarTarea(nuevaTarea);
        Consola.exito("Tarea creada correctamente (ID: " + nuevaTarea.getId() + ")");
        Consola.pausar();
    }

    /**
     * Lee y valida una fecha en formato dd/MM/yyyy con reintentos.
     * @return LocalDate parseada o null si el usuario cancela.
     */
    private LocalDate leerFecha(String prompt) {
        while (true) {
            String input = Consola.leerLinea(prompt);
            try {
                return LocalDate.parse(input, FMT_FECHA);
            } catch (DateTimeParseException e) {
                Consola.error("Formato inválido. Usa dd/MM/yyyy  (ej: 31/12/2025)");
                String reintentar = Consola.leerLinea("  ¿Reintentar? (s/n): ");
                if (!reintentar.equalsIgnoreCase("s")) return null;
            }
        }
    }

    // ─── 3. Cambiar Estado ────────────────────────────────────────────────

    private void cambiarEstadoTarea() {
        Consola.limpiarPantalla();
        Consola.subtitulo("CAMBIAR ESTADO DE TAREA");
        imprimirResumenCompacto();
        if (usuario.getTareas().isEmpty()) { Consola.pausar(); return; }

        int id = Consola.leerEntero("\n  ID de la tarea: ");
        Tarea tarea = usuario.buscarTareaPorId(id);
        if (tarea == null) {
            Consola.error("No se encontró una tarea con ID " + id + ".");
            Consola.pausar();
            return;
        }

        System.out.println("\n  Tarea      : " + tarea.getTitulo());
        System.out.println("  Estado actual: " + tarea.getEstado());
        System.out.println("\n  Nuevo estado:");
        System.out.println("    1. PENDIENTE   2. EN_PROGRESO   3. COMPLETADA");
        int opc = Consola.leerEntero("  → Estado: ");
        tarea.setEstado(Estado.fromInt(opc));
        Consola.exito("Estado actualizado a: " + tarea.getEstado());
        Consola.pausar();
    }

    // ─── 4. Eliminar ──────────────────────────────────────────────────────

    private void eliminarTarea() {
        Consola.limpiarPantalla();
        Consola.subtitulo("ELIMINAR TAREA");
        imprimirResumenCompacto();
        if (usuario.getTareas().isEmpty()) { Consola.pausar(); return; }

        int id = Consola.leerEntero("\n  ID de la tarea a eliminar: ");
        Tarea tarea = usuario.buscarTareaPorId(id);
        if (tarea == null) {
            Consola.error("No se encontró una tarea con ID " + id + ".");
            Consola.pausar();
            return;
        }

        System.out.println("\n  Tarea: " + tarea.getTitulo());
        String confirmacion = Consola.leerLinea("  ¿Confirmas la eliminación? (s/n): ");
        if (confirmacion.equalsIgnoreCase("s")) {
            usuario.eliminarTarea(id);
            Consola.exito("Tarea eliminada exitosamente.");
        } else {
            Consola.info("Operación cancelada.");
        }
        Consola.pausar();
    }

    // ─── 5. Filtrar ───────────────────────────────────────────────────────

    private void filtrarTareas() {
        Consola.limpiarPantalla();
        Consola.subtitulo("FILTRAR TAREAS");
        System.out.println("\n    1. Filtrar por Estado");
        System.out.println("    2. Filtrar por Prioridad");
        System.out.println("    0. Cancelar\n");
        int opc = Consola.leerEntero("  → Opción: ");

        switch (opc) {
            case 1 -> {
                System.out.println("\n  Estado: 1=PENDIENTE  2=EN_PROGRESO  3=COMPLETADA");
                Estado estado = Estado.fromInt(Consola.leerEntero("  → Estado: "));
                imprimirListaDeTareas(usuario.filtrarPorEstado(estado), "FILTRO: " + estado);
            }
            case 2 -> {
                System.out.println("\n  Prioridad: 1=ALTA  2=MEDIA  3=BAJA");
                Prioridad prio = Prioridad.fromInt(Consola.leerEntero("  → Prioridad: "));
                imprimirListaDeTareas(usuario.filtrarPorPrioridad(prio), "FILTRO: PRIORIDAD " + prio);
            }
            case 0 -> { /* cancelar */ }
            default -> { Consola.error("Opción no válida."); Consola.pausar(); }
        }
    }

    // ─── 6. Estadísticas ─────────────────────────────────────────────────

    private void verEstadisticas() {
        Consola.limpiarPantalla();
        Consola.subtitulo("ESTADÍSTICAS DE " + usuario.getNombreUsuario().toUpperCase());
        System.out.println(usuario.getEstadisticas());
        Consola.pausar();
    }

    // ─── Utilidad ─────────────────────────────────────────────────────────

    /** Lista compacta (una línea por tarea) para menús de selección. */
    private void imprimirResumenCompacto() {
        List<Tarea> tareas = usuario.getTareas();
        if (tareas.isEmpty()) {
            Consola.info("No tienes tareas registradas todavía.");
            return;
        }
        System.out.println();
        Consola.separador();
        System.out.printf("  %-6s %-10s %-30s %-12s %s%n",
                          "ID", "TIPO", "TÍTULO", "PRIORIDAD", "ESTADO");
        Consola.separador();
        tareas.forEach(t -> System.out.printf("  %-6d %-10s %-30s %-12s %s%n",
                t.getId(),
                t.getTipoTarea(),
                truncar(t.getTitulo(), 28),
                t.getPrioridad(),
                t.getEstado()));
        Consola.separador();
    }

    private String truncar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 2) + "…";
    }
}
