import java.io.*;
import java.net.*;

/**
 * Test connection to ChatServer
 */
public class TestConnection {
    public static void main(String[] args) {
        String[] testIPs = {"127.0.0.1", "localhost", "10.0.2.2"};
        int port = 8888;

        System.out.println("=== Testing ChatServer Connection ===\n");

        for (String ip : testIPs) {
            System.out.println("Testing: " + ip + ":" + port);
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(ip, port), 3000);

                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Send PING
                String ping = "{\"type\":\"PING\"}";
                out.println(ping);
                System.out.println("  Sent: " + ping);

                // Read response
                String response = in.readLine();
                System.out.println("  Response: " + response);
                System.out.println("  ✅ SUCCESS\n");

                socket.close();

            } catch (Exception e) {
                System.out.println("  ❌ FAILED: " + e.getMessage() + "\n");
            }
        }

        System.out.println("\n=== Testing complete ===");
        System.out.println("\nFor Android Emulator:");
        System.out.println("  - Use IP: 10.0.2.2");
        System.out.println("  - Make sure ChatServer is running (java ChatServer)");
        System.out.println("  - Check Windows Firewall allows port 8888");
    }
}
