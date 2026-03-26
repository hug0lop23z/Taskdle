/**
 * Clase de valor (Value Object) que encapsula las estadísticas calculadas
 * de un usuario. Inmutable: una vez creada, sus datos no cambian.
 */
public class Estadisticas {

    private final long total;
    private final long completadas;
    private final long pendientes;
    private final long enProgreso;
    private final long vencidas;
    private final double porcentajeCompletadas;

    public Estadisticas(long total, long completadas, long pendientes,
                        long enProgreso, long vencidas, double porcentajeCompletadas) {
        this.total                 = total;
        this.completadas           = completadas;
        this.pendientes            = pendientes;
        this.enProgreso            = enProgreso;
        this.vencidas              = vencidas;
        this.porcentajeCompletadas = porcentajeCompletadas;
    }

    public long getTotal()                      { return total; }
    public long getCompletadas()                { return completadas; }
    public long getPendientes()                 { return pendientes; }
    public long getEnProgreso()                 { return enProgreso; }
    public long getVencidas()                   { return vencidas; }
    public double getPorcentajeCompletadas()    { return porcentajeCompletadas; }

    @Override
    public String toString() {
        // Barra de progreso visual
        int bloques = (int) (porcentajeCompletadas / 5); // 20 bloques = 100%
        String barra = "█".repeat(bloques) + "░".repeat(20 - bloques);

        return String.format(
            "\n" +
            "  ╔══════════════════════════════════════╗\n" +
            "  ║           ESTADÍSTICAS               ║\n" +
            "  ╠══════════════════════════════════════╣\n" +
            "  ║  Total de tareas   : %-16d║\n" +
            "  ║  ✔  Completadas    : %-16d║\n" +
            "  ║  ▶  En Progreso    : %-16d║\n" +
            "  ║  ○  Pendientes     : %-16d║\n" +
            "  ║  ⚠  Vencidas       : %-16d║\n" +
            "  ╠══════════════════════════════════════╣\n" +
            "  ║  Progreso: [%s] %.1f%%  ║\n" +
            "  ╚══════════════════════════════════════╝\n",
            total, completadas, enProgreso, pendientes, vencidas,
            barra, porcentajeCompletadas
        );
    }
}
