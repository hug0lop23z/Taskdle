/**
 * SUBCLASE CONCRETA #1 — Hereda de Tarea.
 *
 * TareaSimple es la forma más básica de una tarea: sin fecha límite
 * ni lógica adicional de vencimiento. Implementa los dos métodos
 * abstractos heredados proporcionando su propia versión.
 *
 * HERENCIA: extiende 'Tarea' y adquiere todos sus atributos y
 * comportamientos (id, título, prioridad, estado, completar(), etc.)
 * sin necesidad de redefinirlos.
 */
public class TareaSimple extends Tarea {

    /**
     * Delega la inicialización base al constructor de Tarea via super().
     */
    public TareaSimple(String titulo, String descripcion, Prioridad prioridad) {
        super(titulo, descripcion, prioridad);
    }

    // ─── Implementación de métodos abstractos ──────────────────────────────

    @Override
    public String getTipoTarea() {
        return "SIMPLE";
    }

    @Override
    public String getInfoAdicional() {
        return "Sin fecha límite establecida.";
    }
}
