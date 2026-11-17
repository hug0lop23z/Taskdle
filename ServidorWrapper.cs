using System;
using System.Diagnostics;
using System.IO;

class Program {
    static void Main(string[] args) {
        Console.Title = "Servidor Taskdle";
        Console.WriteLine("========================================");
        Console.WriteLine("            Servidor Taskdle            ");
        Console.WriteLine("========================================");
        Console.WriteLine("Iniciando el servidor web local...");
        
        if (!File.Exists("servidor.jar")) {
            Console.WriteLine("\n[ERROR] No se encontro el archivo 'servidor.jar'.");
            Console.WriteLine("Asegurate de que estas ejecutando este archivo desde");
            Console.WriteLine("la carpeta principal del proyecto.");
            Console.ReadLine();
            return;
        }

        ProcessStartInfo psi = new ProcessStartInfo();
        psi.FileName = "java";
        psi.Arguments = "-jar servidor.jar";
        psi.UseShellExecute = false;
        
        try {
            Process p = Process.Start(psi);
            p.WaitForExit();
        } catch (Exception ex) {
            Console.WriteLine("\n[ERROR] No se pudo ejecutar Java.");
            Console.WriteLine("¿Tienes instalado Java en el sistema?");
            Console.WriteLine("Detalles técnicos:");
            Console.WriteLine(ex.Message);
            Console.ReadLine();
        }
    }
}
