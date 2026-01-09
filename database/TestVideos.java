import java.util.*;

/**
 * 测试脚本：检查 MySQL 数据库中的视频数据
 */
public class TestVideos {
    public static void main(String[] args) {
        System.out.println("========== 测试视频数据 ==========\n");

        DatabaseManager db = DatabaseManager.getInstance();

        System.out.println("--- 1. 读取所有视频 ---");
        List<Map<String, Object>> videos = db.getAllVideos();
        System.out.println("视频总数: " + videos.size());

        if (videos.isEmpty()) {
            System.out.println("\n⚠️ 数据库中没有视频！");
            System.out.println("\n解决方法：");
            System.out.println("1. 客户端重新发布视频");
            System.out.println("2. 或手动插入测试数据到 MySQL");
        } else {
            System.out.println("\n视频列表：");
            for (Map<String, Object> video : videos) {
                System.out.println("  - ID: " + video.get("id"));
                System.out.println("    标题: " + video.get("title"));
                System.out.println("    作者: " + video.get("authorName"));
                System.out.println("    URL: " + video.get("videoUrl"));
                System.out.println();
            }
        }

        db.close();
        System.out.println("========== 测试完成 ==========");
    }
}
