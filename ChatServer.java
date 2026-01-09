import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 聊天服务器
 *
 * 功能：
 * 1. 用户登录验证
 * 2. 消息转发
 * 3. 多线程处理客户端连接
 *
 * 运行：
 * javac -encoding UTF-8 ChatServer.java
 * java ChatServer
 */
public class ChatServer {

    private static final int PORT = 8888;
    private static final String DB_FILE = "users.txt";  //  用户数据持久化文件

    // 用户数据库（内存）：username -> password
    private static final Map<String, String> userDatabase = new ConcurrentHashMap<>();

    // 在线用户：username -> ClientHandler
    private static final Map<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>();

    // 离线消息队列：username -> List<String>
    private static final Map<String, List<String>> offlineMessages = new ConcurrentHashMap<>();

    static {
        // 初始化测试用户
        userDatabase.put("user1", "123");
        userDatabase.put("user2", "123");
        userDatabase.put("admin", "admin");
        System.out.println("[Server] 初始化用户数据库: " + userDatabase.keySet());
    }

    public static void main(String[] args) {
        System.out.println("[Server] 聊天服务器启动中...");
        System.out.println("[Server] 监听端口: " + PORT);

        //  启动时加载用户数据
        loadUsersFromFile();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[Server] 服务器已启动，等待客户端连接...\n");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                String clientAddress = clientSocket.getInetAddress().getHostAddress();
                System.out.println("[Server] 新客户端连接: " + clientAddress);

                // 为每个客户端创建一个新线程
                ClientHandler handler = new ClientHandler(clientSocket);
                new Thread(handler).start();
            }

        } catch (IOException e) {
            System.err.println("[Server] 服务器启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     *  保存用户数据到文件
     */
    private static void saveUsersToFile() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(DB_FILE))) {
            for (Map.Entry<String, String> entry : userDatabase.entrySet()) {
                writer.println(entry.getKey() + ":" + entry.getValue());
            }
            System.out.println("[Database] 用户数据已保存到 " + DB_FILE + "，共 " + userDatabase.size() + " 个用户");
        } catch (IOException e) {
            System.err.println("[Error] 保存用户数据失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 从文件加载用户数据
     */
    private static void loadUsersFromFile() {
        File file = new File(DB_FILE);
        if (!file.exists()) {
            System.out.println("[Database] 用户数据文件不存在，使用默认用户");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int loadedCount = 0;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    userDatabase.put(parts[0], parts[1]);
                    loadedCount++;
                }
            }
            System.out.println("[Database] 已从文件加载 " + loadedCount + " 个用户");
            System.out.println("[Database] 用户列表: " + userDatabase.keySet());
        } catch (IOException e) {
            System.err.println("[Error] 加载用户数据失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 客户端处理线程
     */
    static class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                // 初始化输入输出流
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("[Received] " + line);
                    handleMessage(line);
                }

            } catch (IOException e) {
                System.err.println("[Error] 客户端连接异常: " + e.getMessage());
            } finally {
                cleanup();
            }
        }

        /**
         * 处理客户端消息（JSON解析）
         */
        private void handleMessage(String message) {
            try {
                // Check if it's a REGISTER command (format: REGISTER:username:password)
                if (message.startsWith("REGISTER:")) {
                    handleRegister(message);
                    return;
                }

                String type = extractValue(message, "type");

                if ("LOGIN".equals(type)) {
                    handleLogin(message);
                } else if ("MSG".equals(type)) {
                    handleChatMessage(message);
                } else if ("PING".equals(type)) {
                    sendResponse(200, "PONG");
                } else {
                    sendResponse(400, "Unknown message type: " + type);
                }

            } catch (Exception e) {
                System.err.println("[Error] 消息处理失败: " + e.getMessage());
                sendResponse(500, "Server error: " + e.getMessage());
            }
        }

        /**
         * 处理注册请求
         */
        private void handleRegister(String message) {
            try {
                // Parse: REGISTER:username:password
                String[] parts = message.split(":", 3);
                if (parts.length != 3) {
                    out.println("REGISTER_FAIL:Invalid format");
                    return;
                }

                String user = parts[1];
                String pass = parts[2];

                System.out.println("[Register] Registration attempt: " + user);

                // Validate username length
                if (user.length() < 3 || user.length() > 20) {
                    out.println("REGISTER_FAIL:Username length must be 3-20 characters");
                    return;
                }

                // Validate password length
                if (pass.length() < 6) {
                    out.println("REGISTER_FAIL:Password must be at least 6 characters");
                    return;
                }

                // Check if user already exists
                if (userDatabase.containsKey(user)) {
                    System.out.println("[Register] User already exists: " + user);
                    out.println("REGISTER_FAIL:Username already exists");
                    return;
                }

                // Register new user
                userDatabase.put(user, pass);
                System.out.println("[Register] Registration successful: " + user);
                System.out.println("[Database] Current users: " + userDatabase.keySet());

                // 注册成功后立即保存到文件
                saveUsersToFile();

                out.println("REGISTER_SUCCESS");

            } catch (Exception e) {
                System.err.println("[Error] Registration failed: " + e.getMessage());
                out.println("REGISTER_FAIL:Server error");
            }
        }

        /**
         * 处理登录请求
         */
        private void handleLogin(String message) {
            String user = extractValue(message, "user");
            String pass = extractValue(message, "pass");

            System.out.println("[Login] 用户尝试登录: " + user);

            // 验证用户名和密码
            if (!userDatabase.containsKey(user)) {
                System.out.println("[Login] 用户不存在: " + user);
                sendResponse(401, "用户不存在");
                return;
            }

            if (!userDatabase.get(user).equals(pass)) {
                System.out.println("[Login] 密码错误: " + user);
                sendResponse(401, "密码错误");
                return;
            }

            // 登录成功
            this.username = user;
            onlineUsers.put(user, this);
            System.out.println("[Login] 登录成功: " + user);
            System.out.println("[Online] 当前在线用户: " + onlineUsers.keySet());

            sendResponse(200, "登录成功");

            // 发送离线消息
            sendOfflineMessages();
        }

        /**
         * 处理聊天消息
         */
        private void handleChatMessage(String message) {
            if (username == null) {
                sendResponse(403, "请先登录");
                return;
            }

            String from = extractValue(message, "from");
            String to = extractValue(message, "to");
            String content = extractValue(message, "content");

            System.out.println("[Message] " + from + " -> " + to + ": " + content);

            // 转发消息给目标用户
            ClientHandler targetHandler = onlineUsers.get(to);

            if (targetHandler != null) {
                // 目标用户在线，直接转发
                targetHandler.sendMessage(message);
                System.out.println("[Forward] 消息已转发给 " + to);
                sendResponse(200, "消息已发送");
            } else {
                // 目标用户离线，存储离线消息
                System.out.println("[Offline] 用户 " + to + " 离线，消息已暂存");
                offlineMessages.computeIfAbsent(to, k -> new ArrayList<>()).add(message);
                sendResponse(200, "对方离线，消息已暂存");
            }
        }

        /**
         * 发送离线消息
         */
        private void sendOfflineMessages() {
            List<String> messages = offlineMessages.remove(username);
            if (messages != null && !messages.isEmpty()) {
                System.out.println("[Offline] 发送 " + messages.size() + " 条离线消息给 " + username);
                for (String msg : messages) {
                    sendMessage(msg);
                }
            }
        }

        /**
         * 发送响应
         */
        private void sendResponse(int code, String message) {
            String response = "{\"type\":\"ACK\",\"code\":" + code + ",\"msg\":\"" + message + "\"}";
            out.println(response);
        }

        /**
         * 发送消息
         */
        private void sendMessage(String message) {
            out.println(message);
        }

        /**
         * JSON值提取
         */
        private String extractValue(String json, String key) {
            String pattern = "\"" + key + "\"\\s*:\\s*\"?([^\"\\,}]+)\"?";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
            return "";
        }

        /**
         * 清理资源
         */
        private void cleanup() {
            if (username != null) {
                onlineUsers.remove(username);
                System.out.println("[Logout] 用户下线: " + username);
                System.out.println("[Online] 当前在线用户: " + onlineUsers.keySet());
            }

            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                System.err.println("[Error] 关闭连接失败: " + e.getMessage());
            }
        }
    }
}
