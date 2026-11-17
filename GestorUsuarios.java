import java.util.Map;

/**
 * Gestiona el ciclo de vida de los usuarios (registro y autenticación).
 *
 * Usa un HashMap<String, Usuario> como base de datos en memoria:
 * - Clave   → nombre de usuario (String, único)
 * - Valor   → objeto Usuario completo con su lista de tareas
 *
 * El acceso en HashMap es O(1) para búsquedas, inserciones y eliminaciones,
 * lo que lo hace ideal para este tipo de "base de datos" ligera.
 *
 * Persistencia: los datos se cargan desde datos.csv al iniciar y se
 * guardan automáticamente tras cada modificación relevante.
 */
public class GestorUsuarios {

    // Base de datos en memoria: clave = nombreUsuario (único), valor = Usuario
    private final Map<String, Usuario> usuarios;

    public GestorUsuarios() {
        // Cargar datos persistidos desde archivo al arrancar
        this.usuarios = PersistenciaArchivo.cargar();
    }

    /**
     * Registra un nuevo usuario si el nombre no está ya tomado.
     * @return true si se creó exitosamente, false si el usuario ya existe.
     */
    public boolean registrarUsuario(String nombreUsuario, String contrasena) {
        if (usuarios.containsKey(nombreUsuario)) {
            return false;
        }
        usuarios.put(nombreUsuario, new Usuario(nombreUsuario, contrasena));
        guardarDatos(); // Persistir inmediatamente
        return true;
    }

    /**
     * Autentica credenciales y retorna el objeto Usuario si son correctas.
     * @return el Usuario autenticado, o null si las credenciales son inválidas.
     */
    public Usuario autenticar(String nombreUsuario, String contrasena) {
        Usuario usuario = usuarios.get(nombreUsuario);
        if (usuario != null && usuario.verificarContrasena(contrasena)) {
            return usuario;
        }
        return null;
    }

    public boolean existeUsuario(String nombreUsuario) {
        return usuarios.containsKey(nombreUsuario);
    }

    public int getTotalUsuarios() {
        return usuarios.size();
    }

    /**
     * Retorna la referencia interna al Usuario (para uso del servidor web).
     * No usar para autenticación; usar autenticar() para eso.
     */
    public Usuario getUsuario(String nombreUsuario) {
        return usuarios.get(nombreUsuario);
    }

    /**
     * Guarda todos los datos actuales en el archivo de persistencia.
     * Llamar después de cualquier cambio en tareas de un usuario.
     */
    public void guardarDatos() {
        PersistenciaArchivo.guardar(usuarios);
    }
}
