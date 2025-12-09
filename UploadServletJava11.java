package com.example;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;
import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.disk.*;
import org.apache.commons.fileupload.servlet.*;

/**
 * Video Upload Servlet (for Java 11 + Tomcat 9)
 */
public class UploadServletJava11 extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024;  // 100MB
    private static final long MAX_REQUEST_SIZE = 100 * 1024 * 1024;
    private static final String UPLOAD_DIRECTORY = "videos";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");

        PrintWriter out = response.getWriter();

        try {
            if (!ServletFileUpload.isMultipartContent(request)) {
                sendErrorResponse(out, "Request must be multipart");
                return;
            }

            DiskFileItemFactory factory = new DiskFileItemFactory();
            factory.setSizeThreshold(1024 * 1024);
            factory.setRepository(new File(System.getProperty("java.io.tmpdir")));

            ServletFileUpload upload = new ServletFileUpload(factory);
            upload.setFileSizeMax(MAX_FILE_SIZE);
            upload.setSizeMax(MAX_REQUEST_SIZE);

            String uploadPath = getServletContext().getRealPath("/") + UPLOAD_DIRECTORY;
            File uploadDir = new File(uploadPath);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
                System.out.println("[UploadServlet] Created upload directory: " + uploadPath);
            }

            List<FileItem> formItems = upload.parseRequest(request);

            if (formItems != null && formItems.size() > 0) {
                for (FileItem item : formItems) {
                    if (!item.isFormField()) {
                        String fileName = UUID.randomUUID().toString() + ".mp4";
                        String filePath = uploadPath + File.separator + fileName;

                        File storeFile = new File(filePath);
                        item.write(storeFile);

                        System.out.println("[UploadServlet] Upload success:");
                        System.out.println("  - Original: " + item.getName());
                        System.out.println("  - Saved: " + fileName);
                        System.out.println("  - Size: " + item.getSize() + " bytes");
                        System.out.println("  - Path: " + filePath);

                        String serverUrl = "http://" + request.getServerName() + ":" + request.getServerPort();
                        String fileUrl = serverUrl + "/" + UPLOAD_DIRECTORY + "/" + fileName;

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

    private void sendSuccessResponse(PrintWriter out, String url) {
        String json = String.format(
            "{\"status\":\"success\",\"url\":\"%s\",\"message\":\"Upload success\"}",
            url
        );
        out.print(json);
        out.flush();
    }

    private void sendErrorResponse(PrintWriter out, String message) {
        String json = String.format(
            "{\"status\":\"error\",\"url\":null,\"message\":\"%s\"}",
            message
        );
        out.print(json);
        out.flush();
    }
}
