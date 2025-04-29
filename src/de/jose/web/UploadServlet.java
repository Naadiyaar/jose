package de.jose.web;

import de.jose.task.io.ArchiveImport;
import de.jose.task.io.PGNImport;
import de.jose.util.file.FileUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

@WebServlet(name = "UploadServlet", value = "/upload-servlet")
@MultipartConfig(fileSizeThreshold = 1024 * 1024 * 1024,
        maxFileSize = 1024 * 1024 * 1024,
        maxRequestSize = 1024 * 1024 * 1024 * 5)
public class UploadServlet extends HttpServlet
{
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        WebApplication.open(getServletContext(), response);
        SessionUtil su = new SessionUtil(request, request.getSession());

        String uploadPath = getServletContext().getRealPath("") + "/uploads";
        File uploadDir = new File(uploadPath);
        //String uploadPath = StaticProperty.javaIoTmpDir();
        if (!uploadDir.exists()) uploadDir.mkdir();

        response.setContentType("text/html");
        ArrayList<File> files = new ArrayList<>();
        for (Part part : request.getParts()) {
            String fileName = getFileName(part);
            response.getWriter().println("uloading "+fileName+" ... ");
            String filePath = uploadPath + File.separator + fileName;
            part.write(filePath);
            files.add(new File(filePath));
            response.getWriter().println("ok <br>");
        }
        for (File file : files) try {
            response.getWriter().println("importing "+file.getName()+" ... ");
            importFile(file);
            response.getWriter().println("ok <br>");
            file.delete();
        } catch (Exception e) {
            e.printStackTrace(response.getWriter());
        }
    }

    private void importFile(File file) throws Exception {
        if (FileUtil.hasExtension(file.getName(),"jos") || FileUtil.hasExtension(file.getName(),"jose"))
        {
            ArchiveImport imprt = new ArchiveImport(file);
            imprt.start();
            imprt.join();
        }
        else
        {
            PGNImport imprt = PGNImport.openFile(file,0,Long.MAX_VALUE/2);
            imprt.join();
        }
    }

    private String getFileName(Part part) {
        for (String content : part.getHeader("content-disposition").split(";")) {
            if (content.trim().startsWith("filename"))
                return content.substring(content.indexOf("=") + 2, content.length() - 1);
        }
        return "upload-file";
    }
}
