/**
 * Enumeración que define los posibles estados de una tarea.
 */
public enum Estado {
    PENDIENTE, EN_PROGRESO, COMPLETADA;

    /**
     * Convierte una opción numérica del menú a un valor de Estado.
     */
    public static Estado fromInt(int opcion) {
        return switch (opcion) {
            case 1 -> PENDIENTE;
            case 2 -> EN_PROGRESO;
            case 3 -> COMPLETADA;
            default -> PENDIENTE;
        };
    }
}
