import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * SUBCLASE CONCRETA #2 — Hereda de Tarea y la especializa.
 *
 * TareaConFechaLimite añade un atributo propio (fechaLimite) y lógica
 * adicional específica: detectar si la tarea está vencida y calcular
 * cuántos días faltan para la fecha límite.
 *
 * Esto demuestra HERENCIA EXTENSIVA: la subclase no solo implementa
 * los métodos abstractos, sino que agrega su propia funcionalidad
 * sin romper el contrato de la clase padre.
 */
public class TareaConFechaLimite extends Tarea {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Atributo propio de esta subclase (no existe en TareaSimple)
    private LocalDate fechaLimite;

    public TareaConFechaLimite(String titulo, String descripcion,
                               Prioridad prioridad, LocalDate fechaLimite) {
        super(titulo, descripcion, prioridad); // Llama al constructor de Tarea
        this.fechaLimite = fechaLimite;
    }

    // ─── Lógica adicional propia de esta subclase ──────────────────────────

    /**
     * Una tarea está vencida si no está completada y su fecha límite ya pasó.
     */
    public boolean estaVencida() {
        return !estaCompletada() && LocalDate.now().isAfter(fechaLimite);
    }

    /**
     * Calcula la diferencia en días entre hoy y la fecha límite.
     * Un valor negativo indica que ya venció.
     */
    public long getDiasRestantes() {
        return ChronoUnit.DAYS.between(LocalDate.now(), fechaLimite);
    }

    // ─── Getters y Setters del atributo propio ─────────────────────────────

    public LocalDate getFechaLimite()              { return fechaLimite; }
    public void setFechaLimite(LocalDate f)        { this.fechaLimite = f; }

    // ─── Implementación de métodos abstractos ──────────────────────────────

    @Override
    public String getTipoTarea() {
        return "CON FECHA";
    }

    /**
     * Sobreescribe getInfoAdicional() con lógica de fecha y alertas.
     * Aquí se ve el POLIMORFISMO: Tarea.toString() llama a este método
     * sin saber que es TareaConFechaLimite quien lo ejecuta.
     */
    @Override
    public String getInfoAdicional() {
        StringBuilder sb = new StringBuilder("Fecha límite: ").append(fechaLimite.format(FMT));

        if (estaCompletada()) {
            sb.append("  [Completada a tiempo ✔]");
        } else if (estaVencida()) {
            long diasVencida = Math.abs(getDiasRestantes());
            sb.append("  *** VENCIDA (hace ").append(diasVencida).append(" día(s)) ***");
        } else {
            long dias = getDiasRestantes();
            if (dias == 0)       sb.append("  ⚠ ¡Vence HOY!");
            else if (dias == 1)  sb.append("  ⚠ Vence MAÑANA");
            else if (dias <= 7)  sb.append("  ⚠ Quedan ").append(dias).append(" días");
            else                 sb.append("  (Quedan ").append(dias).append(" días)");
        }
        return sb.toString();
    }
}
