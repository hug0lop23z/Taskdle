import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Representa a un usuario del sistema con su propia lista de tareas.
 * Cada instancia de Usuario es completamente independiente de las demás
 * (su ArrayList de tareas es privado e individual).
 *
 * Aplica ENCAPSULAMIENTO: los datos internos solo se modifican a través
 * de métodos que validan y controlan el acceso.
 */
public class Usuario {

    private final String nombreUsuario;
    private String contrasena;             // En producción: usar hash (BCrypt, etc.)
    private final List<Tarea> tareas;      // Lista de tareas propia de este usuario

    public Usuario(String nombreUsuario, String contrasena) {
        this.nombreUsuario = nombreUsuario;
        this.contrasena    = contrasena;
        this.tareas        = new ArrayList<>();
    }

    // ─── Gestión de Tareas ────────────────────────────────────────────────

    public void agregarTarea(Tarea tarea) {
        tareas.add(tarea);
    }

    /**
     * Elimina la tarea con el ID dado.
     * @return true si se encontró y eliminó, false si no existía.
     */
    public boolean eliminarTarea(int id) {
        return tareas.removeIf(t -> t.getId() == id);
    }

    /**
     * Busca una tarea por su ID único.
     * @return la Tarea encontrada, o null si no existe.
     */
    public Tarea buscarTareaPorId(int id) {
        return tareas.stream()
                     .filter(t -> t.getId() == id)
                     .findFirst()
                     .orElse(null);
    }

    // ─── Filtros ─────────────────────────────────────────────────────────

    public List<Tarea> filtrarPorEstado(Estado estado) {
        return tareas.stream()
                     .filter(t -> t.getEstado() == estado)
                     .collect(Collectors.toList());
    }

    public List<Tarea> filtrarPorPrioridad(Prioridad prioridad) {
        return tareas.stream()
                     .filter(t -> t.getPrioridad() == prioridad)
                     .collect(Collectors.toList());
    }

    // ─── Ordenación con TreeSet ──────────────────────────────────────────

    /**
     * Retorna las tareas del usuario ordenadas mediante un TreeSet.
     *
     * Criterio de orden (Comparator personalizado):
     *   1. Prioridad: ALTA → MEDIA → BAJA  (orden de urgencia)
     *   2. Desempate: fecha de creación ascendente (la más antigua primero)
     *   3. Desempate final: ID (garantiza que no se pierde ninguna tarea en el Set)
     *
     * El TreeSet mantiene los elementos siempre ordenados gracias al
     * árbol rojo-negro interno; la inserción y búsqueda son O(log n).
     *
     * @return nueva List con todas las tareas en orden de prioridad/fecha.
     */
    public List<Tarea> getTareasOrdenadas() {
        Comparator<Tarea> comparador = Comparator
                // 1. Prioridad: el ordinal del enum ya está en el orden ALTA=0, MEDIA=1, BAJA=2
                .<Tarea, Integer>comparing(t -> t.getPrioridad().ordinal())
                // 2. Dentro de la misma prioridad, la más antigua primero
                .thenComparing(Tarea::getFechaCreacion)
                // 3. Desempate final por ID (evita que el TreeSet descarte duplicados)
                .thenComparingInt(Tarea::getId);

        TreeSet<Tarea> treeset = new TreeSet<>(comparador);
        treeset.addAll(tareas);
        return new ArrayList<>(treeset);
    }

    // ─── Estadísticas ─────────────────────────────────────────────────────

    /**
     * Calcula y retorna un objeto Estadisticas con los datos actuales
     * del usuario. Usa instanceof para detectar TareaConFechaLimite,
     * aprovechando el polimorfismo de la jerarquía de herencia.
     */
    public Estadisticas getEstadisticas() {
        long total      = tareas.size();
        long completadas = tareas.stream().filter(Tarea::estaCompletada).count();
        long pendientes  = tareas.stream().filter(t -> t.getEstado() == Estado.PENDIENTE).count();
        long enProgreso  = tareas.stream().filter(t -> t.getEstado() == Estado.EN_PROGRESO).count();
        long vencidas    = tareas.stream()
                                 .filter(t -> t instanceof TareaConFechaLimite tcfl && tcfl.estaVencida())
                                 .count();
        double porcentaje = total > 0 ? (completadas * 100.0 / total) : 0.0;
        return new Estadisticas(total, completadas, pendientes, enProgreso, vencidas, porcentaje);
    }

    // ─── Autenticación ────────────────────────────────────────────────────

    public boolean verificarContrasena(String contrasena) {
        return this.contrasena.equals(contrasena);
    }

    // ─── Getters y Setters ────────────────────────────────────────────────

    public String getNombreUsuario()        { return nombreUsuario; }
    public String getContrasena()           { return contrasena; }
    public List<Tarea> getTareas()          { return new ArrayList<>(tareas); } // Copia defensiva
    public void setContrasena(String c)     { this.contrasena = c; }
}
