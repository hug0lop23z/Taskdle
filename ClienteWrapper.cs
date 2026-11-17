using System;
using System.Diagnostics;

class Program {
    static void Main(string[] args) {
        Console.Title = "Cliente Taskdle";
        Console.WriteLine("========================================");
        Console.WriteLine("   Cliente Taskdle - Conectarse a la Red");
        Console.WriteLine("========================================");
        Console.WriteLine("");
        Console.WriteLine("Introduce la direccion IP local del servidor (ej: 192.168.1.50).");
        Console.WriteLine("Si el servidor esta en tu propia maquina, solo presiona Enter.");
        Console.WriteLine("");
        Console.Write("IP del servidor: ");
        string ip = Console.ReadLine();
        
        if (string.IsNullOrWhiteSpace(ip)) {
            ip = "localhost";
        }
        
        string url = "http://" + ip.Trim() + ":8080";
        Console.WriteLine("\nConectando a " + url + " ...");
        
        // Abrir como aplicación de escritorio (sin barra de navegador)
        bool abierto = false;
        
        try {
            Process.Start("msedge.exe", "--app=\"" + url + "\"");
            abierto = true;
        } catch { }

        if (!abierto) {
            try {
                Process.Start("chrome.exe", "--app=\"" + url + "\"");
                abierto = true;
            } catch { }
        }

        if (!abierto) {
            // Decaimiento al navegador por defecto si no existen ni Edge ni Chrome
            try {
                ProcessStartInfo psi = new ProcessStartInfo();
                psi.FileName = url;
                psi.UseShellExecute = true;
                Process.Start(psi);
            } catch (Exception ex) {
                Console.WriteLine("\n[ERROR] Ocurrio un error al intentar abrir la app: " + ex.Message);
                Console.ReadLine();
            }
        }
    }
}
