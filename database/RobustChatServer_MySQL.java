import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/**
 * 聊天服务器 - MySQL版本
 *
 *
 * 使用MySQL替代文件存储（users.txt, chat_data.json, video_list.json）
 * 数据持久化更可靠
 * 支持高并发查询
 * 支持SQL查询和统计
 *
 */
public class RobustChatServer_MySQL {

    private static final int PORT = 8888;

    // 替换为 DatabaseManager
    private static DatabaseManager database;

    // 会话管理器（在线用户）
    private static final SessionManager sessionManager = new SessionManager();

    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════");
        System.out.println("     聊天服务器");
        System.out.println("═══════════════════════════════════════");
        System.out.println("[Server] 监听端口: " + PORT);
        System.out.println();

        //  初始化数据库连接
        database = DatabaseManager.getInstance();

        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Shutdown] 服务器正在关闭...");
            database.close();
            System.out.println("[Shutdown] 数据库连接已关闭");
        }));

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[Server] 服务器启动成功，等待客户端连接...\n");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                String clientAddress = clientSocket.getInetAddress().getHostAddress();
                System.out.println("[Accept] 新客户端连接: " + clientAddress);

                // 为每个客户端创建处理线程
                ClientHandler handler = new ClientHandler(clientSocket, database, sessionManager);
                new Thread(handler).start();
            }

        } catch (IOException e) {
            System.err.println("[Error] 服务器启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 客户端处理器
     */
    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter writer;
        private BufferedReader reader;
        private String username;
        private DatabaseManager db;
        private SessionManager sessionMgr;

        public ClientHandler(Socket socket, DatabaseManager db, SessionManager sessionMgr) {
            this.socket = socket;
            this.db = db;
            this.sessionMgr = sessionMgr;
        }

        @Override
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

                // 第一步：等待LOGIN包
                String loginPacket = reader.readLine();
                if (loginPacket == null) {
                    System.err.println("[Login] 客户端未发送登录包，关闭连接");
                    return;
                }

                System.out.println("[Login] 收到登录包: " + loginPacket);

                // 支持两种格式：旧格式 "REGISTER:user:pass" 和新格式 JSON
                if (loginPacket.startsWith("REGISTER:")) {
                    // 处理注册请求（旧格式）
                    handleRegisterOldFormat(loginPacket);
                    return;
                }

                // 处理 JSON 格式的登录/注册
                JSONObject loginJson = new JSONObject(loginPacket);
                String type = loginJson.getString("type");

                if ("REGISTER".equals(type)) {
                    // 处理注册请求（JSON格式）
                    handleRegisterJson(loginJson);
                    return;
                } else if (!"LOGIN".equals(type)) {
                    sendResponse(500, "第一个消息必须是LOGIN或REGISTER包");
                    return;
                }

                // 处理登录
                String user = loginJson.getString("user");
                String pass = loginJson.getString("pass");

                //  使用数据库验证用户
                if (db.authenticateUser(user, pass)) {
                    username = user;
                    sendResponse(200, "登录成功");
                    System.out.println("[Login] 用户登录成功: " + username);

                    //  记录会话
                    sessionMgr.addUser(username, writer);
                    String ipAddress = socket.getInetAddress().getHostAddress();
                    db.recordUserOnline(username, ipAddress);
                    db.logOperation(username, "LOGIN", "登录成功", ipAddress);

                    //  推送离线消息
                    deliverOfflineMessages();

                    // 进入消息循环
                    handleMessages();

                } else {
                    sendResponse(401, "用户名或密码错误");
                    System.out.println("[Login] 认证失败: " + user);
                }

            } catch (Exception e) {
                System.err.println("[Error] 客户端处理异常: " + e.getMessage());
            } finally {
                cleanup();
            }
        }

        /**
         * 处理消息循环
         */
        private void handleMessages() throws IOException {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[Receive] " + username + ": " + line);

                try {
                    JSONObject json = new JSONObject(line);
                    String type = json.getString("type");

                    switch (type) {
                        case "MSG":
                            handleChatMessage(json);
                            break;

                        case "GET_VIDEOS":
                            handleGetVideos();
                            break;

                        case "PUBLISH_VIDEO":
                            handlePublishVideo(json);
                            break;

                        case "PING":
                            // 心跳包
                            db.updateHeartbeat(username);
                            sendResponse(200, "PONG");
                            break;

                        default:
                            System.out.println("[Warn] 未知消息类型: " + type);
                    }

                } catch (Exception e) {
                    System.err.println("[Error] 处理消息失败: " + e.getMessage());
                }
            }
        }

        /**
         * 处理聊天消息
         */
        private void handleChatMessage(JSONObject json) {
            String to = json.getString("to");
            String content = json.getString("content");
            long time = json.getLong("time");

            System.out.println("[Message] " + username + " -> " + to + ": " + content);

            // 转发给接收者（如果在线）
            PrintWriter targetWriter = sessionMgr.getWriter(to);
            if (targetWriter != null) {
                // 在线，直接转发
                JSONObject forward = new JSONObject();
                forward.put("type", "MSG");
                forward.put("from", username);
                forward.put("to", to);
                forward.put("content", content);
                forward.put("time", time);
                targetWriter.println(forward.toString());
                System.out.println("[Forward]  消息已转发给在线用户: " + to);

            } else {
                // 不在线，保存为离线消息
                db.saveOfflineMessage(username, to, content, time);
                System.out.println("[Offline] 已保存离线消息: " + username + " -> " + to);
            }
        }

        /**
         * 推送离线消息
         */
        private void deliverOfflineMessages() {
            List<Map<String, Object>> messages = db.getOfflineMessages(username);

            if (messages.isEmpty()) {
                System.out.println("[OfflineMsg] 无离线消息: " + username);
                return;
            }

            System.out.println("[OfflineMsg] 推送 " + messages.size() + " 条离线消息给: " + username);

            for (Map<String, Object> msg : messages) {
                JSONObject json = new JSONObject();
                json.put("type", "MSG");
                json.put("from", msg.get("from"));
                json.put("to", username);
                json.put("content", msg.get("content"));
                json.put("time", msg.get("time"));
                writer.println(json.toString());
            }

            // 标记为已送达
            db.markMessagesAsDelivered(username);
        }

        /**
         * 处理获取视频列表请求
         */
        private void handleGetVideos() {
            List<Map<String, Object>> videos = db.getAllVideos();

            JSONObject response = new JSONObject();
            response.put("type", "VIDEO_LIST");
            response.put("videos", new JSONArray(videos));

            writer.println(response.toString());
            System.out.println("[VideoList] 已返回 " + videos.size() + " 个视频给: " + username);
        }

        /**
         * 处理发布视频请求
         */
        private void handlePublishVideo(JSONObject json) {
            String id = json.getString("id");
            String title = json.getString("title");
            String description = json.optString("description", "");
            String videoUrl = json.getString("videoUrl");
            String coverUrl = json.getString("coverUrl");
            String authorId = json.getString("authorId");
            String authorName = json.getString("authorName");
            String authorAvatarUrl = json.getString("authorAvatarUrl");
            int likeCount = json.optInt("likeCount", 0);
            int commentCount = json.optInt("commentCount", 0);

            // 保存到数据库
            boolean success = db.saveVideo(id, title, description, videoUrl, coverUrl,
                                           authorId, authorName, authorAvatarUrl,
                                           likeCount, commentCount);

            if (success) {
                sendResponse(200, "视频发布成功");
                db.logOperation(username, "PUBLISH_VIDEO", "视频ID: " + id,
                                socket.getInetAddress().getHostAddress());
                System.out.println("[PublishVideo] 用户 " + username + " 发布视频: " + title);
            } else {
                sendResponse(500, "视频发布失败");
            }
        }

        /**
         * 处理注册请求（旧格式：REGISTER:username:password）
         */
        private void handleRegisterOldFormat(String registerPacket) {
            try {
                String[] parts = registerPacket.split(":", 3);
                if (parts.length != 3) {
                    writer.println("REGISTER_FAILED");
                    System.out.println("[Register] 格式错误: " + registerPacket);
                    return;
                }

                String username = parts[1];
                String password = parts[2];

                System.out.println("[Register] 收到注册请求: " + username);

                // 调用数据库注册
                boolean success = db.registerUser(username, password, username);

                if (success) {
                    writer.println("REGISTER_SUCCESS");
                    db.logOperation(username, "REGISTER", "用户注册成功",
                                    socket.getInetAddress().getHostAddress());
                    System.out.println("[Register] 用户注册成功: " + username);
                } else {
                    writer.println("REGISTER_FAILED");
                    System.out.println("[Register] 用户注册失败（可能已存在）: " + username);
                }

            } catch (Exception e) {
                writer.println("REGISTER_FAILED");
                System.err.println("[Register] 注册异常: " + e.getMessage());
                e.printStackTrace();
            }
        }

        /**
         * 处理注册请求（JSON格式）
         */
        private void handleRegisterJson(JSONObject json) {
            try {
                String username = json.getString("user");
                String password = json.getString("pass");
                String displayName = json.optString("displayName", username);

                System.out.println("[Register] 收到JSON注册请求: " + username);

                // 调用数据库注册
                boolean success = db.registerUser(username, password, displayName);

                if (success) {
                    sendResponse(200, "注册成功");
                    db.logOperation(username, "REGISTER", "用户注册成功",
                                    socket.getInetAddress().getHostAddress());
                    System.out.println("[Register] 用户注册成功: " + username);
                } else {
                    sendResponse(409, "用户名已存在");
                    System.out.println("[Register] 用户注册失败（已存在）: " + username);
                }

            } catch (Exception e) {
                sendResponse(500, "注册失败: " + e.getMessage());
                System.err.println("[Register] 注册异常: " + e.getMessage());
                e.printStackTrace();
            }
        }

        /**
         * 发送响应
         */
        private void sendResponse(int code, String msg) {
            JSONObject response = new JSONObject();
            response.put("code", code);
            response.put("msg", msg);
            writer.println(response.toString());
        }

        /**
         * 清理资源
         */
        private void cleanup() {
            try {
                if (username != null) {
                    sessionMgr.removeUser(username);
                    db.recordUserOffline(username);
                    db.logOperation(username, "LOGOUT", "用户下线",
                                    socket.getInetAddress().getHostAddress());
                    System.out.println("[Logout] 用户下线: " + username);
                }
                reader.close();
                writer.close();
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 会话管理器（内存存储在线用户）
     */
    static class SessionManager {
        private final Map<String, PrintWriter> sessions = new ConcurrentHashMap<>();

        public void addUser(String username, PrintWriter writer) {
            sessions.put(username, writer);
            System.out.println("[SessionManager] 用户上线: " + username + " (当前在线: " + sessions.size() + ")");
        }

        public void removeUser(String username) {
            sessions.remove(username);
            System.out.println("[SessionManager] 用户下线: " + username + " (当前在线: " + sessions.size() + ")");
        }

        public PrintWriter getWriter(String username) {
            return sessions.get(username);
        }

        public Set<String> getOnlineUsers() {
            return sessions.keySet();
        }
    }
}
