/**
 * Enumeración que define los niveles de prioridad de una tarea.
 */
public enum Prioridad {
    ALTA, MEDIA, BAJA;

    /**
     * Convierte una opción numérica del menú a un valor de Prioridad.
     */
    public static Prioridad fromInt(int opcion) {
        return switch (opcion) {
            case 1 -> ALTA;
            case 2 -> MEDIA;
            case 3 -> BAJA;
            default -> MEDIA;
        };
    }
}
