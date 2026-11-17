/**
 * Punto de entrada a la interfaz de usuario.
 * Gestiona el flujo de autenticación: login y registro.
 * Una vez autenticado, delega el control a MenuTareas.
 */
public class MenuPrincipal {

    private final GestorUsuarios gestorUsuarios;

    public MenuPrincipal() {
        this.gestorUsuarios = new GestorUsuarios();
    }

    // ─── Bucle principal ──────────────────────────────────────────────────

    public void iniciar() {
        boolean activo = true;
        while (activo) {
            mostrarMenu();
            int opcion = Consola.leerEntero("  → Opción: ");
            switch (opcion) {
                case 1 -> login();
                case 2 -> registro();
                case 0 -> activo = false;
                default -> { Consola.error("Opción no válida."); Consola.pausar(); }
            }
        }
        Consola.limpiarPantalla();
        System.out.println("  ¡Gracias por usar el Gestor de Tareas! Hasta pronto.\n");
    }

    // ─── Menú ─────────────────────────────────────────────────────────────

    private void mostrarMenu() {
        Consola.limpiarPantalla();
        Consola.titulo("GESTOR DE TAREAS — TO-DO LIST");
        System.out.printf("  Usuarios registrados: %d%n%n", gestorUsuarios.getTotalUsuarios());
        System.out.println("    1.  🔐  Iniciar sesión");
        System.out.println("    2.  📝  Crear cuenta nueva");
        System.out.println("    0.  🚪  Salir\n");
    }

    // ─── Login ────────────────────────────────────────────────────────────

    private void login() {
        Consola.limpiarPantalla();
        Consola.subtitulo("INICIO DE SESIÓN");

        String nombreUsuario = Consola.leerLinea("\n  Usuario    : ");
        String contrasena    = Consola.leerLinea("  Contraseña : ");

        Usuario usuario = gestorUsuarios.autenticar(nombreUsuario, contrasena);
        if (usuario != null) {
            Consola.exito("¡Bienvenido, " + usuario.getNombreUsuario() + "!");
            Consola.pausar();
            // Delegar control al menú de tareas de este usuario
            new MenuTareas(usuario, gestorUsuarios).iniciar();
        } else {
            Consola.error("Usuario o contraseña incorrectos. Inténtalo de nuevo.");
            Consola.pausar();
        }
    }

    // ─── Registro ─────────────────────────────────────────────────────────

    private void registro() {
        Consola.limpiarPantalla();
        Consola.subtitulo("CREAR CUENTA NUEVA");

        String nombreUsuario = Consola.leerLinea("\n  Nombre de usuario  : ");
        if (nombreUsuario.isBlank()) {
            Consola.error("El nombre de usuario no puede estar vacío.");
            Consola.pausar();
            return;
        }
        if (nombreUsuario.length() < 3) {
            Consola.error("El nombre de usuario debe tener al menos 3 caracteres.");
            Consola.pausar();
            return;
        }
        if (gestorUsuarios.existeUsuario(nombreUsuario)) {
            Consola.error("El usuario '" + nombreUsuario + "' ya existe. Elige otro nombre.");
            Consola.pausar();
            return;
        }

        String contrasena = Consola.leerLinea("  Contraseña (mín. 4 caracteres): ");
        if (contrasena.length() < 4) {
            Consola.error("La contraseña debe tener al menos 4 caracteres.");
            Consola.pausar();
            return;
        }

        String confirmar = Consola.leerLinea("  Confirmar contraseña            : ");
        if (!contrasena.equals(confirmar)) {
            Consola.error("Las contraseñas no coinciden. Inténtalo de nuevo.");
            Consola.pausar();
            return;
        }

        gestorUsuarios.registrarUsuario(nombreUsuario, contrasena);
        Consola.exito("¡Cuenta creada exitosamente! Ya puedes iniciar sesión.");
        Consola.pausar();
    }
}
