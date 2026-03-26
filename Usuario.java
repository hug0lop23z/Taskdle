import java.util.ArrayList;
import java.util.List;
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
    public List<Tarea> getTareas()          { return new ArrayList<>(tareas); } // Copia defensiva
    public void setContrasena(String c)     { this.contrasena = c; }
}
