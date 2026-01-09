import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 健壮的聊天服务器 - 迭代15（添加视频同步功能）
 *
 * 核心特性：
 * 1. 持久化离线消息到 JSON 文件
 * 2. 严格的登录握手协议
 * 3. 离线消息存储与自动补发
 * 4. 优雅关闭与数据保存
 * 5. 视频列表同步（解决重装后视频消失问题）
 *
 * 运行：
 * javac -encoding UTF-8 RobustChatServer.java
 * java RobustChatServer
 */
public class RobustChatServer {

    private static final int PORT = 8888;
    private static final String DATA_FILE = "chat_data.json";
    private static final String USERS_FILE = "users.txt";
    private static final String VIDEO_LIST_FILE = "video_list.json";  //  视频列表文件

    // 用户数据库（内存）：username -> password
    //key: username   value: password
    private static final Map<String, String> userDatabase = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════");
        System.out.println("     聊天服务器");
        System.out.println("═══════════════════════════════════════");
        System.out.println("[Server] 监听端口: " + PORT);
        System.out.println();

        // 加载用户数据
        loadUsersFromFile();

        // 加载离线消息
        OfflineMessageStore.getInstance().loadFromFile(DATA_FILE);

        //  加载视频列表
        VideoStore.getInstance().loadFromFile(VIDEO_LIST_FILE);

        // 注册关闭钩子，保存数据
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Shutdown] 服务器正在关闭，保存数据...");
            // 保存离线消息
            OfflineMessageStore.getInstance().saveToFile(DATA_FILE);
            VideoStore.getInstance().saveToFile(VIDEO_LIST_FILE);  //  保存视频列表
            System.out.println("[Shutdown] 数据已保存，服务器已关闭");
        }));

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[Server]  服务器启动成功，等待客户端连接...\n");

            while (true) {
                // 接受客户端连接
                Socket clientSocket = serverSocket.accept();
                String clientAddress = clientSocket.getInetAddress().getHostAddress();
                System.out.println("[Accept] 新客户端连接: " + clientAddress);

                // 为每个客户端创建处理线程
                ClientHandler handler = new ClientHandler(clientSocket);
                new Thread(handler).start();
            }

        } catch (IOException e) {
            System.err.println("[Error] 服务器启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 从文件加载用户数据
     */
    private static void loadUsersFromFile() {
        File file = new File(USERS_FILE);
        if (!file.exists()) {
            // 初始化默认用户
            userDatabase.put("user1", "123");
            userDatabase.put("user2", "123");
            userDatabase.put("admin", "admin");
            System.out.println("[Database] 用户文件不存在，使用默认用户");
            saveUsersToFile();
        } else {
            //  从文件中加载用户数据
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                //  逐行读取，格式为：username:password
                String line;
                int loadedCount = 0;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        userDatabase.put(parts[0], parts[1]);
                        loadedCount++;
                    }
                }
                System.out.println("[Database] 已加载 " + loadedCount + " 个用户");
            } catch (IOException e) {
                System.err.println("[Error] 加载用户数据失败: " + e.getMessage());
            }
        }
        System.out.println("[Database] 用户列表: " + userDatabase.keySet());
    }

    /**
     * 保存用户数据到文件
     */
    private static void saveUsersToFile() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(USERS_FILE))) {
            for (Map.Entry<String, String> entry : userDatabase.entrySet()) {
                writer.println(entry.getKey() + ":" + entry.getValue());
            }
            System.out.println("[Database] 用户数据已保存，共 " + userDatabase.size() + " 个用户");
        } catch (IOException e) {
            System.err.println("[Error] 保存用户数据失败: " + e.getMessage());
        }
    }

    /**
     * 客户端处理线程
     */
    static class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String userId;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                // 初始化输入输出流
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

                //  严格握手：第一条消息必须是 LOGIN
                String firstLine = in.readLine();
                if (firstLine == null) {
                    System.err.println("[Error] 客户端未发送数据，断开连接");
                    return;
                }

                System.out.println("[Handshake] 收到: " + firstLine);

                // 检查是否是冒号分隔格式的REGISTER消息
                if (firstLine.startsWith("REGISTER:")) {
                    // 处理冒号分隔格式的注册请求
                    handleRegisterSimple(firstLine);
                } else {
                    // 解析 JSON 格式的 LOGIN 消息
                    String type = extractValue(firstLine, "type");

                    if ("LOGIN".equals(type)) {
                        // 处理登录
                        handleLogin(firstLine);
                    } else {
                        System.err.println("[Error] 首条消息不是 LOGIN 或 REGISTER，断开连接");
                        sendError("First message must be LOGIN or REGISTER");
                        return;
                    }
                }

                if (userId == null) {
                    System.err.println("[Error] 登录/注册失败，断开连接");
                    return;
                }

                // 登录成功，开始接收消息
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
         * 处理登录请求（验证用户名和密码）
         */
        private void handleLogin(String loginMsg) {
            try {
                // 提取用户名和密码（字段名匹配客户端：user、pass）
                String user = extractValue(loginMsg, "user");
                String pass = extractValue(loginMsg, "pass");

                System.out.println("[Login] 用户尝试登录: " + user);

                // 验证用户名是否为空
                if (user == null || user.isEmpty()) {
                    System.err.println("[Login] ✗ 用户名为空");
                    sendAuthError("用户名不能为空");
                    return;
                }

                // 验证用户名是否存在
                if (!userDatabase.containsKey(user)) {
                    System.err.println("[Login] ✗ 用户不存在: " + user);
                    sendAuthError("用户不存在");
                    return;
                }

                // 验证密码是否正确
                String correctPassword = userDatabase.get(user);
                if (!correctPassword.equals(pass)) {
                    System.err.println("[Login] ✗ 密码错误: " + user);
                    sendAuthError("密码错误");
                    return;
                }

                // ===== 登录成功 =====
                this.userId = user;
                SessionManager.getInstance().addSession(user, this);
                System.out.println("[Login] 用户登录成功: " + user);
                System.out.println("[Online] 当前在线用户: " + SessionManager.getInstance().getOnlineUsers());

                // 发送登录成功响应
                sendSuccess("登录成功");

                //  补发离线消息
                deliverOfflineMessages();

            } catch (Exception e) {
                System.err.println("[Error] 登录处理失败: " + e.getMessage());
                e.printStackTrace();
                sendAuthError("登录失败: " + e.getMessage());
            }
        }

        /**
         * 处理简单格式的注册请求（REGISTER:username:password）
         */
        private void handleRegisterSimple(String registerMsg) {
            try {
                // 解析冒号分隔的格式: REGISTER:username:password
                String[] parts = registerMsg.split(":", 3);
                if (parts.length != 3) {
                    System.err.println("[Register] 注册消息格式错误: " + registerMsg);
                    out.println("REGISTER_FAILED");
                    return;
                }

                String command = parts[0];  // "REGISTER"
                String uid = parts[1];
                String pwd = parts[2];

                // 检查用户是否已存在
                if (userDatabase.containsKey(uid)) {
                    System.err.println("[Register] 用户已存在: " + uid);
                    out.println("REGISTER_FAILED");
                    return;
                }

                // 创建新用户
                userDatabase.put(uid, pwd);
                saveUsersToFile();
                System.out.println("[Register]  新用户注册成功: " + uid);

                // 自动登录新用户
                this.userId = uid;
                SessionManager.getInstance().addSession(uid, this);
                System.out.println("[Register]  用户已自动登录: " + uid);
                System.out.println("[Online] 当前在线用户: " + SessionManager.getInstance().getOnlineUsers());

                // 发送注册成功响应（简单文本格式）
                out.println("REGISTER_SUCCESS");

            } catch (Exception e) {
                System.err.println("[Error] 注册处理失败: " + e.getMessage());
                out.println("REGISTER_FAILED");
            }
        }

        /**
         * 补发离线消息
         */
        private void deliverOfflineMessages() {
            // 获取离线消息
            OfflineMessageStore store = OfflineMessageStore.getInstance();
            List<String> messages = store.getAndClear(userId);

            if (messages.isEmpty()) {
                System.out.println("[Offline] 用户 " + userId + " 没有离线消息");
                return;
            }

            System.out.println("[Offline] 开始补发 " + messages.size() + " 条离线消息给 " + userId);
            for (String msg : messages) {
                out.println(msg);
                System.out.println("[Offline] 已发送: " + msg);
            }
            System.out.println("[Offline]  离线消息补发完成");
        }

        /**
         * 处理消息
         */
        private void handleMessage(String message) {
            try {
                String type = extractValue(message, "type");

                switch (type) {
                    case "MSG":  //  聊天消息
                        handleChatMessage(message);
                        break;
                    case "PING": //  PING
                        sendSuccess("PONG");
                        break;
                    case "PUBLISH_VIDEO":  //  发布视频
                        handlePublishVideo(message);
                        break;
                    case "GET_VIDEOS":  //  获取视频列表
                        handleGetVideos();
                        break;
                    default:
                        sendError("未知消息类型: " + type);
                }

            } catch (Exception e) {
                System.err.println("[Error] 消息处理失败: " + e.getMessage());
                sendError("消息处理失败: " + e.getMessage());
            }
        }

        /**
         * 处理聊天消息
         */
        private void handleChatMessage(String rawMessage) {
            String from = extractValue(rawMessage, "from");
            String to = extractValue(rawMessage, "to");
            String content = extractValue(rawMessage, "content");

            System.out.println("[Message] " + from + " -> " + to + ": " + content);

            // 转发消息给目标用户
            ClientHandler targetHandler = SessionManager.getInstance().getHandler(to);

            if (targetHandler != null) {
                // 目标用户在线，直接转发
                try {
                    //  使用目标用户的PrintWriter（避免重复创建）
                    targetHandler.out.println(rawMessage);
                    System.out.println("[Forward]  消息已转发给 " + to);
                    sendSuccess("消息已发送");
                } catch (Exception e) {
                    System.err.println("[Error] 转发消息失败: " + e.getMessage());
                    sendError("消息转发失败");
                }
            } else {
                //  目标用户离线，存储离线消息
                System.out.println("[Offline] User offline, msg queued for " + to);
                OfflineMessageStore.getInstance().addMessage(to, rawMessage);
                sendSuccess("对方离线，消息已暂存");
            }
        }

        /**
         *  处理发布视频请求
         */
        private void handlePublishVideo(String message) {
            try {
                VideoStore.getInstance().addVideo(message);
                System.out.println("[Video]  视频已发布: " + message);
                sendSuccess("视频发布成功");
            } catch (Exception e) {
                System.err.println("[Error] 发布视频失败: " + e.getMessage());
                sendError("发布视频失败: " + e.getMessage());
            }
        }

        /**
         *  处理获取视频列表请求
         */
        private void handleGetVideos() {
            try {
                List<String> videos = VideoStore.getInstance().getAllVideos();
                System.out.println("[Video] 返回视频列表，共 " + videos.size() + " 个视频");

                // 构建响应JSON
                StringBuilder response = new StringBuilder();
                response.append("{\"type\":\"VIDEO_LIST\",\"videos\":[");
                for (int i = 0; i < videos.size(); i++) {
                    if (i > 0) response.append(",");
                    response.append(videos.get(i));
                }
                response.append("]}");

                out.println(response.toString());
            } catch (Exception e) {
                System.err.println("[Error] 获取视频列表失败: " + e.getMessage());
                sendError("获取视频列表失败: " + e.getMessage());
            }
        }

        /**
         * 发送成功响应
         */
        private void sendSuccess(String message) {
            String response = "{\"type\":\"ACK\",\"code\":200,\"msg\":\"" + message + "\"}";
            out.println(response);
        }

        /**
         * 发送错误响应（通用错误码 400）
         */
        private void sendError(String message) {
            String response = "{\"type\":\"ACK\",\"code\":400,\"msg\":\"" + message + "\"}";
            out.println(response);
        }

        /**
         * 发送认证错误响应（错误码 401）
         */
        private void sendAuthError(String message) {
            String response = "{\"type\":\"ACK\",\"code\":401,\"msg\":\"" + message + "\"}";
            out.println(response);
        }

        /**
         * 清理资源
         */
        private void cleanup() {
            if (userId != null) {
                SessionManager.getInstance().removeSession(userId);
                System.out.println("[Logout] 用户下线: " + userId);
                System.out.println("[Online] 当前在线用户: " + SessionManager.getInstance().getOnlineUsers());
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

    /**
     * 会话管理器（单例）
     */
    static class SessionManager {
        private static final SessionManager instance = new SessionManager();
        private final Map<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>();

        private SessionManager() {}

        public static SessionManager getInstance() {
            return instance;
        }

        public void addSession(String userId, ClientHandler handler) {
            onlineUsers.put(userId, handler);
        }

        public void removeSession(String userId) {
            onlineUsers.remove(userId);
        }

        public ClientHandler getHandler(String userId) {
            return onlineUsers.get(userId);
        }

        public Socket getSocket(String userId) {
            ClientHandler handler = onlineUsers.get(userId);
            return handler != null ? handler.socket : null;
        }

        public Set<String> getOnlineUsers() {
            return onlineUsers.keySet();
        }
    }

    /**
     * 离线消息存储（单例）
     */
    static class OfflineMessageStore {
        private static final OfflineMessageStore instance = new OfflineMessageStore();
        private final Map<String, List<String>> offlineMessages = new ConcurrentHashMap<>();

        private OfflineMessageStore() {}

        public static OfflineMessageStore getInstance() {
            return instance;
        }

        /**
         * 添加离线消息
         */
        public void addMessage(String userId, String message) {
            offlineMessages.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(message);
            System.out.println("[Store] 离线消息已存储，用户: " + userId + ", 总数: " + offlineMessages.get(userId).size());
        }

        /**
         * 获取并清空离线消息
         */
        public List<String> getAndClear(String userId) {
            List<String> messages = offlineMessages.remove(userId);
            return messages != null ? messages : Collections.emptyList();
        }

        /**
         * 从文件加载离线消息
         */
        public void loadFromFile(String filename) {
            File file = new File(filename);
            if (!file.exists()) {
                System.out.println("[Load] 离线消息文件不存在，跳过加载");
                return;
            }

            try {
                String content = new String(Files.readAllBytes(Paths.get(filename)), "UTF-8");
                if (content.trim().isEmpty()) {
                    System.out.println("[Load] 离线消息文件为空");
                    return;
                }

                // 简单解析 JSON（手动解析）
                int totalMessages = 0;
                content = content.trim();
                if (content.startsWith("{") && content.endsWith("}")) {
                    // 移除首尾大括号
                    content = content.substring(1, content.length() - 1);

                    // 按逗号分割（简化处理，假设消息内容不含逗号）
                    String[] userBlocks = content.split("\",\"");

                    for (String block : userBlocks) {
                        block = block.trim();
                        if (block.isEmpty()) continue;

                        // 提取用户ID和消息数组
                        int colonIndex = block.indexOf("\":[");
                        if (colonIndex > 0) {
                            String userId = block.substring(1, colonIndex);
                            String messagesStr = block.substring(colonIndex + 3);

                            // 移除结尾的 "]
                            if (messagesStr.endsWith("]")) {
                                messagesStr = messagesStr.substring(0, messagesStr.length() - 1);
                            }

                            // 分割消息
                            List<String> msgList = new CopyOnWriteArrayList<>();
                            String[] messages = messagesStr.split("\",\"");
                            for (String msg : messages) {
                                msg = msg.trim();
                                if (msg.startsWith("\"")) msg = msg.substring(1);
                                if (msg.endsWith("\"")) msg = msg.substring(0, msg.length() - 1);
                                if (!msg.isEmpty()) {
                                    msgList.add(msg);
                                }
                            }

                            if (!msgList.isEmpty()) {
                                offlineMessages.put(userId, msgList);
                                totalMessages += msgList.size();
                            }
                        }
                    }
                }

                System.out.println("[Load]  已加载 " + offlineMessages.size() + " 个用户的离线消息，共 " + totalMessages + " 条");

            } catch (Exception e) {
                System.err.println("[Error] 加载离线消息失败: " + e.getMessage());
                e.printStackTrace();
            }
        }

        /**
         * 保存离线消息到文件
         */
        public void saveToFile(String filename) {
            try {
                StringBuilder json = new StringBuilder();
                json.append("{\n");

                int totalMessages = 0;
                boolean first = true;

                for (Map.Entry<String, List<String>> entry : offlineMessages.entrySet()) {
                    if (!first) {
                        json.append(",\n");
                    }
                    first = false;

                    json.append("  \"").append(entry.getKey()).append("\": [");

                    List<String> messages = entry.getValue();
                    for (int i = 0; i < messages.size(); i++) {
                        if (i > 0) {
                            json.append(", ");
                        }
                        // 转义引号
                        String escaped = messages.get(i).replace("\"", "\\\"");
                        json.append("\"").append(escaped).append("\"");
                    }

                    json.append("]");
                    totalMessages += messages.size();
                }

                json.append("\n}");

                Files.write(Paths.get(filename), json.toString().getBytes("UTF-8"));
                System.out.println("[Save]  已保存 " + offlineMessages.size() + " 个用户的离线消息，共 " + totalMessages + " 条");

            } catch (Exception e) {
                System.err.println("[Error] 保存离线消息失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     *  视频存储（单例）- 迭代15
     *
     * 管理所有用户发布的视频元数据
     */
    static class VideoStore {
        private static final VideoStore instance = new VideoStore();
        // 视频列表
        private final List<String> videos = new CopyOnWriteArrayList<>();

        private VideoStore() {}

        public static VideoStore getInstance() {
            return instance;
        }

        /**
         * 添加视频
         */
        public void addVideo(String videoJson) {
            videos.add(videoJson);
            System.out.println("[VideoStore] 视频已添加，总数: " + videos.size());
        }

        /**
         * 获取所有视频
         */
        public List<String> getAllVideos() {
            return new ArrayList<>(videos);
        }

        /**
         * 从文件加载视频列表
         */
        public void loadFromFile(String filename) {
            File file = new File(filename);
            if (!file.exists()) {
                System.out.println("[VideoLoad] 视频列表文件不存在，跳过加载");
                return;
            }

            try {
                // 读取文件内容
                String content = new String(Files.readAllBytes(Paths.get(filename)), "UTF-8");
                if (content.trim().isEmpty() || content.trim().equals("[]")) {
                    System.out.println("[VideoLoad] 视频列表文件为空");
                    return;
                }

                // 简单解析JSON数组
                content = content.trim();
                if (content.startsWith("[") && content.endsWith("]")) {
                    content = content.substring(1, content.length() - 1).trim();

                    if (!content.isEmpty()) {
                        // 分割视频对象（简化处理）
                        int depth = 0;
                        int start = 0;
                        StringBuilder currentVideo = new StringBuilder();

                        for (int i = 0; i < content.length(); i++) {
                            char c = content.charAt(i);
                            if (c == '{') depth++;
                            if (c == '}') depth--;

                            currentVideo.append(c);

                            if (depth == 0 && c == '}') {
                                videos.add(currentVideo.toString().trim());
                                currentVideo = new StringBuilder();
                                // 跳过逗号和空格
                                while (i + 1 < content.length() &&
                                       (content.charAt(i + 1) == ',' ||
                                        Character.isWhitespace(content.charAt(i + 1)))) {
                                    i++;
                                }
                            }
                        }
                    }
                }

                System.out.println("[VideoLoad]  已加载 " + videos.size() + " 个视频");

            } catch (Exception e) {
                System.err.println("[Error] 加载视频列表失败: " + e.getMessage());
                e.printStackTrace();
            }
        }

        /**
         * 保存视频列表到文件
         */
        public void saveToFile(String filename) {
            try {
                StringBuilder json = new StringBuilder();
                json.append("[\n");
                // 遍历视频列表
                for (int i = 0; i < videos.size(); i++) {
                    if (i > 0) {
                        json.append(",\n");
                    }
                    json.append("  ").append(videos.get(i));
                }

                json.append("\n]");
                // 写入文件
                Files.write(Paths.get(filename), json.toString().getBytes("UTF-8"));
                System.out.println("[VideoSave]  已保存 " + videos.size() + " 个视频");

            } catch (Exception e) {
                System.err.println("[Error] 保存视频列表失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
