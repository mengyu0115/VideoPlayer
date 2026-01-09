-- ===================================================
-- 短视频社交平台 - MySQL数据库设计
-- ===================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS video_social_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE video_social_db;

-- ===================================================
-- 1. 用户表 (替代 users.txt)
-- ===================================================
CREATE TABLE users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名（登录ID）',
    password VARCHAR(255) NOT NULL COMMENT '密码（生产环境建议加密）',
    display_name VARCHAR(100) DEFAULT NULL COMMENT '显示名称',
    avatar_url VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    last_login_at TIMESTAMP NULL DEFAULT NULL COMMENT '最后登录时间',
    status ENUM('active', 'inactive', 'banned') DEFAULT 'active' COMMENT '账号状态',

    INDEX idx_username (username),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ===================================================
-- 2. 视频表 (替代 video_list.json)
-- ===================================================
CREATE TABLE videos (
    id VARCHAR(100) PRIMARY KEY COMMENT '视频ID（UUID）',
    title VARCHAR(255) NOT NULL COMMENT '视频标题',
    description TEXT COMMENT '视频描述',
    video_url VARCHAR(500) NOT NULL COMMENT '视频URL',
    cover_url VARCHAR(500) COMMENT '封面URL',
    author_id VARCHAR(50) NOT NULL COMMENT '作者用户名',
    author_name VARCHAR(100) COMMENT '作者显示名称',
    author_avatar_url VARCHAR(500) COMMENT '作者头像URL',
    like_count INT DEFAULT 0 COMMENT '点赞数',
    comment_count INT DEFAULT 0 COMMENT '评论数',
    view_count INT DEFAULT 0 COMMENT '播放次数',
    duration INT DEFAULT 0 COMMENT '视频时长（秒）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    status ENUM('published', 'draft', 'deleted') DEFAULT 'published' COMMENT '视频状态',

    INDEX idx_author_id (author_id),
    INDEX idx_created_at (created_at),
    INDEX idx_status (status),
    INDEX idx_like_count (like_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频表';

-- ===================================================
-- 3. 离线消息表 (替代 chat_data.json)
-- ===================================================
CREATE TABLE offline_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    from_user VARCHAR(50) NOT NULL COMMENT '发送者用户名',
    to_user VARCHAR(50) NOT NULL COMMENT '接收者用户名',
    content TEXT NOT NULL COMMENT '消息内容',
    timestamp BIGINT NOT NULL COMMENT '发送时间戳（毫秒）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    delivered BOOLEAN DEFAULT FALSE COMMENT '是否已送达',
    delivered_at TIMESTAMP NULL DEFAULT NULL COMMENT '送达时间',

    INDEX idx_to_user (to_user, delivered),
    INDEX idx_from_user (from_user),
    INDEX idx_timestamp (timestamp),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='离线消息表';

-- ===================================================
-- 4. 在线会话表（可选，用于管理在线用户）
-- ===================================================
CREATE TABLE online_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    ip_address VARCHAR(45) COMMENT 'IP地址（支持IPv6）',
    login_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    last_heartbeat TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '最后心跳时间',

    INDEX idx_username (username),
    INDEX idx_last_heartbeat (last_heartbeat)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='在线会话表';

-- ===================================================
-- 5. 操作日志表（可选，用于审计）
-- ===================================================
CREATE TABLE operation_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) COMMENT '操作用户',
    operation VARCHAR(50) NOT NULL COMMENT '操作类型（LOGIN/LOGOUT/SEND_MESSAGE/PUBLISH_VIDEO）',
    detail TEXT COMMENT '操作详情（JSON格式）',
    ip_address VARCHAR(45) COMMENT 'IP地址',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',

    INDEX idx_username (username),
    INDEX idx_operation (operation),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- ===================================================
-- 初始化数据：迁移现有用户
-- ===================================================

-- 从 users.txt 迁移用户数据
INSERT INTO users (username, password, display_name, avatar_url) VALUES
    ('user1', '123', 'user1', 'https://picsum.photos/seed/user1/200/200'),
    ('user2', '123', 'user2', 'https://picsum.photos/seed/user2/200/200'),
    ('admin1', '123456', 'admin1', 'https://picsum.photos/seed/admin1/200/200'),
    ('admin', 'admin', 'admin', 'https://picsum.photos/seed/admin/200/200'),
    ('test001', '123456', 'test001', 'https://picsum.photos/seed/test001/200/200');

-- ===================================================
-- 查询示例
-- ===================================================

-- 查询所有活跃用户
SELECT username, display_name, created_at
FROM users
WHERE status = 'active'
ORDER BY created_at DESC;

-- 查询某用户的所有视频
SELECT id, title, like_count, view_count, created_at
FROM videos
WHERE author_id = 'user1' AND status = 'published'
ORDER BY created_at DESC;

-- 查询用户的离线消息
SELECT from_user, content, timestamp
FROM offline_messages
WHERE to_user = 'user1' AND delivered = FALSE
ORDER BY timestamp ASC;

-- 查询在线用户（最近5分钟有心跳）
SELECT username, ip_address, login_at, last_heartbeat
FROM online_sessions
WHERE last_heartbeat > DATE_SUB(NOW(), INTERVAL 5 MINUTE)
ORDER BY last_heartbeat DESC;

-- ===================================================
-- 性能优化建议
-- ===================================================

-- 1. 定期清理已送达的离线消息（可选）
-- DELETE FROM offline_messages WHERE delivered = TRUE AND delivered_at < DATE_SUB(NOW(), INTERVAL 30 DAY);

-- 2. 定期清理过期会话
-- DELETE FROM online_sessions WHERE last_heartbeat < DATE_SUB(NOW(), INTERVAL 10 MINUTE);

-- 3. 定期归档操作日志
-- CREATE TABLE operation_logs_archive LIKE operation_logs;
-- INSERT INTO operation_logs_archive SELECT * FROM operation_logs WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY);
-- DELETE FROM operation_logs WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY);

-- ===================================================
-- 查看表结构
-- ===================================================
SHOW TABLES;
DESC users;
DESC videos;
DESC offline_messages;
DESC online_sessions;
DESC operation_logs;
