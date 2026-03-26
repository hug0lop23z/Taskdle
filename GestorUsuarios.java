import java.util.HashMap;
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
 */
public class GestorUsuarios {

    // Base de datos en memoria: clave = nombreUsuario (único), valor = Usuario
    private final Map<String, Usuario> usuarios;

    public GestorUsuarios() {
        this.usuarios = new HashMap<>();
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
}
