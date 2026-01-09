import java.sql.*;
import java.util.*;

/**
 * MySQL数据库管理器（单例）
 *
 * 功能：
 * - 连接池管理
 * - 用户认证（替代users.txt）
 * - 离线消息存储（替代chat_data.json）
 * - 视频列表管理（替代video_list.json）
 * - 会话管理
 *
 * 使用前需要：
 * 1. 安装MySQL Server
 * 2. 创建数据库：执行 init.sql
 * 3. 添加MySQL JDBC驱动：mysql-connector-java-8.0.33.jar
 * 4. 编译：javac -cp ".;mysql-connector-java-8.0.33.jar" DatabaseManager.java
 */
public class DatabaseManager {

    private static DatabaseManager instance;

    // MySQL连接配置（请根据实际情况修改）
    private static final String DB_URL = "jdbc:mysql://localhost:3306/video_social_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "20050115Baoshan!";  // 修改为MySQL密码

    private Connection connection;

    /**
     * 私有构造函数（单例模式）
     */
    private DatabaseManager() {
        try {
            // 加载MySQL驱动
            Class.forName("com.mysql.cj.jdbc.Driver");
            // 建立连接
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            System.out.println("[Database] 连接成功: " + DB_URL);
        } catch (ClassNotFoundException e) {
            System.err.println("[Database] MySQL驱动未找到，请添加 mysql-connector-java.jar");

            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("[Database] 连接失败，请检查配置");
            e.printStackTrace();
        }
    }

    /**
     * 获取单例
     */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * 获取连接
     */
    public Connection getConnection() {
        try {
            // 检查连接是否有效
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                System.out.println("[Database] 重新连接成功");
            }
        } catch (SQLException e) {
            System.err.println("[Database] 重新连接失败");
            e.printStackTrace();
        }
        return connection;
    }

    // ========================================
    // 用户管理（替代 users.txt）
    // ========================================

    /**
     * 验证用户登录
     *
     * @param username 用户名
     * @param password 密码
     * @return 验证是否成功
     */
    public boolean authenticateUser(String username, String password) {
        String sql = "SELECT password FROM users WHERE username = ? AND status = 'active'";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String dbPassword = rs.getString("password");
                boolean success = dbPassword.equals(password);

                if (success) {
                    // 更新最后登录时间
                    updateLastLogin(username);
                    System.out.println("[Auth] 用户登录成功: " + username);
                } else {
                    System.out.println("[Auth] 密码错误: " + username);
                }
                return success;
            } else {
                System.out.println("[Auth] 用户不存在: " + username);
                return false;
            }

        } catch (SQLException e) {
            System.err.println("[Auth] 数据库查询失败");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 更新最后登录时间
     */
    private void updateLastLogin(String username) {
        String sql = "UPDATE users SET last_login_at = NOW() WHERE username = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 注册新用户
     */
    public boolean registerUser(String username, String password, String displayName) {
        String sql = "INSERT INTO users (username, password, display_name, avatar_url) VALUES (?, ?, ?, ?)";
        String avatarUrl = "https://picsum.photos/seed/" + username + "/200/200";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.setString(3, displayName != null ? displayName : username);
            pstmt.setString(4, avatarUrl);

            pstmt.executeUpdate();
            System.out.println("[Register] 用户注册成功: " + username);
            return true;

        } catch (SQLException e) {
            System.err.println("[Register] 注册失败: " + e.getMessage());
            return false;
        }
    }

    // ========================================
    // 离线消息管理（替代 chat_data.json）
    // ========================================

    /**
     * 保存离线消息
     */
    public void saveOfflineMessage(String fromUser, String toUser, String content, long timestamp) {
        String sql = "INSERT INTO offline_messages (from_user, to_user, content, timestamp) VALUES (?, ?, ?, ?)";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, fromUser);
            pstmt.setString(2, toUser);
            pstmt.setString(3, content);
            pstmt.setLong(4, timestamp);

            pstmt.executeUpdate();
            System.out.println("[OfflineMsg] 已保存离线消息: " + fromUser + " -> " + toUser);

        } catch (SQLException e) {
            System.err.println("[OfflineMsg] 保存失败");
            e.printStackTrace();
        }
    }

    /**
     * 获取用户的所有离线消息
     */
    public List<Map<String, Object>> getOfflineMessages(String username) {
        String sql = "SELECT id, from_user, content, timestamp FROM offline_messages " +
                     "WHERE to_user = ? AND delivered = FALSE ORDER BY timestamp ASC";

        List<Map<String, Object>> messages = new ArrayList<>();

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Map<String, Object> message = new HashMap<>();
                message.put("id", rs.getLong("id"));
                message.put("from", rs.getString("from_user"));
                message.put("content", rs.getString("content"));
                message.put("time", rs.getLong("timestamp"));
                messages.add(message);
            }

            System.out.println("[OfflineMsg] 读取到 " + messages.size() + " 条离线消息: " + username);

        } catch (SQLException e) {
            System.err.println("[OfflineMsg] 读取失败");
            e.printStackTrace();
        }

        return messages;
    }

    /**
     * 标记消息为已送达
     */
    public void markMessagesAsDelivered(String username) {
        String sql = "UPDATE offline_messages SET delivered = TRUE, delivered_at = NOW() WHERE to_user = ? AND delivered = FALSE";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            int count = pstmt.executeUpdate();
            System.out.println("[OfflineMsg] 已标记 " + count + " 条消息为已送达: " + username);

        } catch (SQLException e) {
            System.err.println("[OfflineMsg] 标记失败");
            e.printStackTrace();
        }
    }

    // ========================================
    // 视频管理（替代 video_list.json）
    // ========================================

    /**
     * 保存视频
     */
    public boolean saveVideo(String id, String title, String description, String videoUrl,
                             String coverUrl, String authorId, String authorName,
                             String authorAvatarUrl, int likeCount, int commentCount) {
        String sql = "INSERT INTO videos (id, title, description, video_url, cover_url, " +
                     "author_id, author_name, author_avatar_url, like_count, comment_count) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE " +
                     "title = VALUES(title), description = VALUES(description)";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.setString(2, title);
            pstmt.setString(3, description);
            pstmt.setString(4, videoUrl);
            pstmt.setString(5, coverUrl);
            pstmt.setString(6, authorId);
            pstmt.setString(7, authorName);
            pstmt.setString(8, authorAvatarUrl);
            pstmt.setInt(9, likeCount);
            pstmt.setInt(10, commentCount);

            pstmt.executeUpdate();
            System.out.println("[Video] 视频已保存: " + id + " - " + title);
            return true;

        } catch (SQLException e) {
            System.err.println("[Video] 保存失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取所有视频列表
     */
    public List<Map<String, Object>> getAllVideos() {
        String sql = "SELECT * FROM videos WHERE status = 'published' ORDER BY created_at DESC";
        List<Map<String, Object>> videos = new ArrayList<>();

        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Map<String, Object> video = new HashMap<>();
                video.put("id", rs.getString("id"));
                video.put("title", rs.getString("title"));
                video.put("description", rs.getString("description"));
                video.put("videoUrl", rs.getString("video_url"));
                video.put("coverUrl", rs.getString("cover_url"));
                video.put("authorId", rs.getString("author_id"));
                video.put("authorName", rs.getString("author_name"));
                video.put("authorAvatarUrl", rs.getString("author_avatar_url"));
                video.put("likeCount", rs.getInt("like_count"));
                video.put("commentCount", rs.getInt("comment_count"));
                videos.add(video);
            }

            System.out.println("[Video] 读取到 " + videos.size() + " 个视频");

        } catch (SQLException e) {
            System.err.println("[Video] 读取失败");
            e.printStackTrace();
        }

        return videos;
    }

    // ========================================
    // 会话管理
    // ========================================

    /**
     * 记录用户上线
     */
    public void recordUserOnline(String username, String ipAddress) {
        String sql = "INSERT INTO online_sessions (username, ip_address, login_at, last_heartbeat) " +
                     "VALUES (?, ?, NOW(), NOW()) " +
                     "ON DUPLICATE KEY UPDATE ip_address = VALUES(ip_address), " +
                     "login_at = NOW(), last_heartbeat = NOW()";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, ipAddress);
            pstmt.executeUpdate();
            System.out.println("[Session] 用户上线: " + username + " (" + ipAddress + ")");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 更新心跳
     */
    public void updateHeartbeat(String username) {
        String sql = "UPDATE online_sessions SET last_heartbeat = NOW() WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 记录用户下线
     */
    public void recordUserOffline(String username) {
        String sql = "DELETE FROM online_sessions WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.executeUpdate();
            System.out.println("[Session] 用户下线: " + username);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取在线用户列表
     */
    public List<String> getOnlineUsers() {
        // 5分钟内有心跳的用户视为在线
        String sql = "SELECT username FROM online_sessions " +
                     "WHERE last_heartbeat > DATE_SUB(NOW(), INTERVAL 5 MINUTE)";
        List<String> users = new ArrayList<>();

        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                users.add(rs.getString("username"));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return users;
    }

    // ========================================
    // 操作日志（可选）
    // ========================================

    /**
     * 记录操作日志
     */
    public void logOperation(String username, String operation, String detail, String ipAddress) {
        String sql = "INSERT INTO operation_logs (username, operation, detail, ip_address) VALUES (?, ?, ?, ?)";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, operation);
            pstmt.setString(3, detail);
            pstmt.setString(4, ipAddress);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            // 日志失败不影响业务
            e.printStackTrace();
        }
    }

    // ========================================
    // 资源释放
    // ========================================

    /**
     * 关闭连接
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("[Database] 连接已关闭");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ========================================
    // 测试方法
    // ========================================

    public static void main(String[] args) {
        System.out.println("=== 测试 DatabaseManager ===\n");

        DatabaseManager db = DatabaseManager.getInstance();

        // 测试1: 用户认证
        System.out.println("--- 测试用户认证 ---");
        boolean auth1 = db.authenticateUser("user1", "123");
        System.out.println("user1认证结果: " + auth1 + "\n");

        // 测试2: 保存离线消息
        System.out.println("--- 测试离线消息 ---");
        db.saveOfflineMessage("user1", "user2", "测试消息", System.currentTimeMillis());

        // 测试3: 读取离线消息
        List<Map<String, Object>> messages = db.getOfflineMessages("user2");
        System.out.println("user2的离线消息: " + messages.size() + " 条\n");

        // 测试4: 读取视频列表
        System.out.println("--- 测试视频列表 ---");
        List<Map<String, Object>> videos = db.getAllVideos();
        System.out.println("视频总数: " + videos.size() + "\n");

        // 测试5: 在线用户
        System.out.println("--- 测试在线用户 ---");
        db.recordUserOnline("user1", "127.0.0.1");
        List<String> onlineUsers = db.getOnlineUsers();
        System.out.println("在线用户: " + onlineUsers + "\n");

        db.close();
        System.out.println("=== 测试完成 ===");
    }
}
