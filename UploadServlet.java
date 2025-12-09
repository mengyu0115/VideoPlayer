package com.example;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import java.nio.file.*;
import org.apache.commons.fileupload2.core.*;
import org.apache.commons.fileupload2.jakarta.*;

/**
 * Video Upload Servlet for Tomcat 11 / Jakarta EE
 *
 * Features:
 * 1. Accept multipart file upload requests from clients
 * 2. Save video files to webapps/ROOT/videos/ directory
 * 3. Return video URL
 *
 * Dependencies:
 * - commons-fileupload2-jakarta-2.0.0-M1.jar
 * - commons-fileupload2-core-2.0.0-M1.jar
 * - commons-io-2.13.0.jar
 *
 * Deployment:
 * 1. Compile with Java 17+
 * 2. Copy to: webapps/ROOT/WEB-INF/classes/com/example/UploadServlet.class
 * 3. Configure web.xml
 * 4. Restart Tomcat
 */
public class UploadServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    // File upload configuration
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024;  // 100MB
    private static final long MAX_REQUEST_SIZE = 100 * 1024 * 1024;  // 100MB
    private static final String UPLOAD_DIRECTORY = "videos";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Set JSON response type
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");

        PrintWriter out = response.getWriter();

        try {
            // Check if request is multipart
            if (!JakartaServletFileUpload.isMultipartContent(request)) {
                sendErrorResponse(out, "Request must be multipart");
                return;
            }

            // Configure file upload
            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
            DiskFileItemFactory factory = DiskFileItemFactory.builder()
                .setPath(tempDir)
                .setBufferSize(1024 * 1024)
                .get();

            JakartaServletFileUpload upload = new JakartaServletFileUpload(factory);
            upload.setFileSizeMax(MAX_FILE_SIZE);
            upload.setSizeMax(MAX_REQUEST_SIZE);

            // Get upload directory real path
            String uploadPath = getServletContext().getRealPath("/") + UPLOAD_DIRECTORY;
            File uploadDir = new File(uploadPath);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
                System.out.println("[UploadServlet] Created upload directory: " + uploadPath);
            }

            // Parse upload request
            List<DiskFileItem> formItems = upload.parseRequest(request);

            if (formItems != null && formItems.size() > 0) {
                for (DiskFileItem item : formItems) {
                    if (!item.isFormField()) {
                        // Generate unique filename
                        String fileName = UUID.randomUUID().toString() + ".mp4";
                        String filePath = uploadPath + File.separator + fileName;

                        // Save file
                        File storeFile = new File(filePath);
                        item.write(storeFile.toPath());

                        System.out.println("[UploadServlet] Upload success:");
                        System.out.println("  - Original: " + item.getName());
                        System.out.println("  - Saved: " + fileName);
                        System.out.println("  - Size: " + item.getSize() + " bytes");
                        System.out.println("  - Path: " + filePath);

                        // Build file URL
                        String serverUrl = "http://" + request.getServerName() + ":" + request.getServerPort();
                        String fileUrl = serverUrl + "/" + UPLOAD_DIRECTORY + "/" + fileName;

                        // Return success response
                        sendSuccessResponse(out, fileUrl);
                        return;
                    }
                }
            } else {
                sendErrorResponse(out, "No file found");
            }

        } catch (FileUploadException e) {
            System.err.println("[UploadServlet] Upload error: " + e.getMessage());
            e.printStackTrace();
            sendErrorResponse(out, "Upload failed: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[UploadServlet] Server error: " + e.getMessage());
            e.printStackTrace();
            sendErrorResponse(out, "Server error: " + e.getMessage());
        }
    }

    /**
     * Send success response
     */
    private void sendSuccessResponse(PrintWriter out, String url) {
        String json = String.format(
            "{\"status\":\"success\",\"url\":\"%s\",\"message\":\"Upload success\"}",
            url
        );
        out.print(json);
        out.flush();
    }

    /**
     * Send error response
     */
    private void sendErrorResponse(PrintWriter out, String message) {
        String json = String.format(
            "{\"status\":\"error\",\"url\":null,\"message\":\"%s\"}",
            message
        );
        out.print(json);
        out.flush();
    }
}
