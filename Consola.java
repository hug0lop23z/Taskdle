import java.util.Scanner;

/**
 * Clase utilitaria que centraliza toda la entrada/salida de la consola.
 * Aplica el principio DRY (Don't Repeat Yourself): en lugar de crear
 * Scanners y try-catch dispersos por el código, toda la lógica de I/O
 * vive aquí y es reutilizable por cualquier clase de menú.
 *
 * Los métodos son estáticos (no necesita instancia) ya que no mantiene
 * estado propio: solo actúa como "canal" hacia System.in / System.out.
 */
public class Consola {

    // Un único Scanner compartido para toda la aplicación
    private static final Scanner SCANNER = new Scanner(System.in);

    // ─── Entrada ──────────────────────────────────────────────────────────

    public static String leerLinea(String prompt) {
        System.out.print(prompt);
        return SCANNER.nextLine().trim();
    }

    /**
     * Lee un entero del usuario, re-preguntando si la entrada no es válida.
     */
    public static int leerEntero(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(SCANNER.nextLine().trim());
            } catch (NumberFormatException e) {
                error("Entrada no válida. Por favor, ingresa un número.");
            }
        }
    }

    public static void pausar() {
        System.out.print("\n  Presiona ENTER para continuar...");
        SCANNER.nextLine();
    }

    // ─── Salida y Formato ─────────────────────────────────────────────────

    public static void limpiarPantalla() {
        // Simula limpiar pantalla con saltos de línea (funciona en cualquier OS)
        System.out.println("\n\n\n\n\n");
    }

    public static void titulo(String texto) {
        int ancho = 46;
        String borde = "═".repeat(ancho);
        int pad = Math.max(0, (ancho - texto.length()) / 2);
        System.out.println("\n  ╔" + borde + "╗");
        System.out.printf("  ║%" + (pad + texto.length()) + "s%" + (ancho - pad - texto.length()) + "s║%n", texto, "");
        System.out.println("  ╚" + borde + "╝\n");
    }

    public static void subtitulo(String texto) {
        int largo = Math.max(0, 44 - texto.length());
        System.out.println("\n  ┌── " + texto + " " + "─".repeat(largo));
    }

    public static void exito(String msg)   { System.out.println("\n  ✔  " + msg); }
    public static void error(String msg)   { System.out.println("\n  ✘  " + msg); }
    public static void info(String msg)    { System.out.println("\n  ℹ  " + msg); }
    public static void separador()         { System.out.println("  " + "─".repeat(48)); }
}
