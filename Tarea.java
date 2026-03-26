import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CLASE ABSTRACTA - Pilar de la jerarquía de herencia.
 *
 * 'Tarea' define el contrato que TODAS las tareas deben cumplir.
 * No puede ser instanciada directamente (abstract), forzando el uso
 * de sus subclases concretas: TareaSimple y TareaConFechaLimite.
 *
 * Aplica ENCAPSULAMIENTO: todos los campos son privados, accesibles
 * solo a través de getters/setters con lógica controlada.
 *
 * ABSTRACCIÓN: los métodos getTipoTarea() y getInfoAdicional() son
 * abstractos → cada subclase define su propia implementación
 * (esto es POLIMORFISMO en acción al invocarlos en toString()).
 */
public abstract class Tarea {

    // Contador de IDs autoincremental y seguro entre hilos (thread-safe)
    private static final AtomicInteger contadorId = new AtomicInteger(1);

    // --- Atributos base (encapsulados como privados) ---
    private final int id;
    private String titulo;
    private String descripcion;
    private Prioridad prioridad;
    private Estado estado;
    private final LocalDateTime fechaCreacion;

    /**
     * Constructor protegido: solo las subclases pueden llamarlo via super().
     */
    protected Tarea(String titulo, String descripcion, Prioridad prioridad) {
        this.id           = contadorId.getAndIncrement();
        this.titulo       = titulo;
        this.descripcion  = descripcion;
        this.prioridad    = prioridad;
        this.estado       = Estado.PENDIENTE; // Estado inicial por defecto
        this.fechaCreacion = LocalDateTime.now();
    }

    // ─── Métodos Abstractos ────────────────────────────────────────────────
    // Las subclases OBLIGATORIAMENTE deben implementar estos métodos.

    /** Retorna una etiqueta corta que identifica el tipo de tarea. */
    public abstract String getTipoTarea();

    /** Retorna información adicional específica de cada subclase. */
    public abstract String getInfoAdicional();

    // ─── Métodos Concretos (comportamiento común heredado) ─────────────────

    /** Marca la tarea como COMPLETADA. */
    public void completar() {
        this.estado = Estado.COMPLETADA;
    }

    public boolean estaCompletada() {
        return this.estado == Estado.COMPLETADA;
    }

    // ─── Getters y Setters ─────────────────────────────────────────────────

    public int getId()                       { return id; }
    public String getTitulo()                { return titulo; }
    public void setTitulo(String titulo)     { this.titulo = titulo; }
    public String getDescripcion()           { return descripcion; }
    public void setDescripcion(String desc)  { this.descripcion = desc; }
    public Prioridad getPrioridad()          { return prioridad; }
    public void setPrioridad(Prioridad p)    { this.prioridad = p; }
    public Estado getEstado()                { return estado; }
    public void setEstado(Estado estado)     { this.estado = estado; }
    public LocalDateTime getFechaCreacion()  { return fechaCreacion; }

    /**
     * toString() invoca getInfoAdicional() → gracias al POLIMORFISMO,
     * en tiempo de ejecución se llama la versión de la subclase correcta,
     * sin que este método sepa con qué subclase está trabajando.
     */
    @Override
    public String toString() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        return String.format(
            "  ┌─[ID: %d]─[%s]──────────────────────────\n"  +
            "  │ Título      : %s\n"                          +
            "  │ Descripción : %s\n"                          +
            "  │ Prioridad   : %s\n"                          +
            "  │ Estado      : %s\n"                          +
            "  │ Creada      : %s\n"                          +
            "  │ Detalle     : %s\n"                          +
            "  └────────────────────────────────────────────",
            id, getTipoTarea(),
            titulo,
            descripcion.isBlank() ? "(sin descripción)" : descripcion,
            prioridad,
            estado,
            fechaCreacion.format(fmt),
            getInfoAdicional()
        );
    }
}
