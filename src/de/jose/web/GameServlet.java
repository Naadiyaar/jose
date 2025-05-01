package de.jose.web;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.sql.ResultSet;
import java.io.IOException;
import java.io.File;
import java.sql.SQLException;

import de.jose.Version;
import de.jose.db.ParamStatement;
import de.jose.db.JoConnection;
import de.jose.db.JoPreparedStatement;
import de.jose.Application;
import de.jose.export.HtmlUtil;
import de.jose.pgn.SearchRecord;
import de.jose.export.ExportConfig;
import de.jose.export.ExportContext;
import de.jose.task.io.PGNExport;
import de.jose.task.io.XMLExport;
import de.jose.task.io.XSLFOExport;
import de.jose.util.FontUtil;
import de.jose.view.style.JoStyleContext;
import de.jose.task.GameSource;

@WebServlet(name = "GameServlet", value = "/game-servlet")
public class GameServlet extends HttpServlet
{
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        WebApplication.open(getServletContext(), response);
        SessionUtil su = new SessionUtil(request, request.getSession());

        try {
            printGame(su, response);
        } catch (Exception e) {
            e.printStackTrace(response.getWriter());
        }
    }

    private void printGame(SessionUtil su, HttpServletResponse response) throws Exception
    {
        int gid = su.getInt("GId",1,true);
        int row = su.getInt("row",-1,false);
        if (row>=0) {
            //  next row requested
            int row0 = su.getInt("current-row0",-1,true);
            int[] id_array = (int[]) su.get("current-ids",true);

            if (row0>=0 && id_array!=null && row>=row0 && row < (row0+id_array.length)) {
                gid = id_array[row - row0];
            }
            else {
                gid = getGameId(su, row);
            }
        }

        su.set("GId",gid);

        String output_type = (String) su.get("out",true);
        if (output_type==null) output_type = "xsl.dhtml";   //  resp. "xsl.html", "xsl.pdf", "xsl.text"
        su.set("out",output_type);

        ExportContext expContext = WebApplication.getExportContext(su);
        expContext.source = GameSource.singleGame(gid);
        //  TODO we could print multiple games, too !

        ExportConfig expConfig = Application.theApplication.getExportConfig();
        expContext.config = expConfig.getConfig(output_type);

        switch (expContext.getOutput())
        {
            case ExportConfig.OUTPUT_HTML:
                printHtml(response, expContext);
                break;

            case ExportConfig.OUTPUT_XML:
            case ExportConfig.OUTPUT_TEX:
            case ExportConfig.OUTPUT_TEXT:
                printPlainText(response, expContext);
                break;

            case ExportConfig.OUTPUT_XSL_FO:
                printPdf(response, expContext);
                break;

            case ExportConfig.OUTPUT_PGN:
                printPgn(response, expContext);
                break;

            default:
                throw new IllegalArgumentException();   //  TODO
        }
    }

    private void printHtml(HttpServletResponse response, ExportContext expContext) throws Exception
    {
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
//		response.setHeader("Content-disposition", "inline");
        expContext.target = response.getWriter();

        //  todo only after restart; no need to create it for every request
        WebApplication.createCollateral(expContext);
        //  setup XML exporter with appropriate style sheet
        XMLExport xmltask = new XMLExport(expContext);
        xmltask.setSilentTime(Integer.MAX_VALUE);
        xmltask.run();     //  wait for task to complete
    }

    private static void printPlainText(HttpServletResponse response, ExportContext expContext) throws Exception
    {
        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-disposition", "inline");
        expContext.target = response.getWriter();

        //  make sure the output text lines are wrapped
		/*
			<style>
                    pre[wrap]  { white-space: pre-wrap; }  / *for Mozilla* /
            pre  { white-space: pre-wrap; word-wrap: break-word; }  / *for IE* /
			</style>
                    <pre wrap>*/

        XMLExport task = new XMLExport(expContext);
        task.setSilentTime(Integer.MAX_VALUE);
        task.run();     //  wait for task to complete
        /* %> </pre> <% */
    }

    private static void printPgn(HttpServletResponse response, ExportContext expContext) throws Exception
    {
        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");    //  PGN standard is ISO-8859-1. But we use UTF-8 !
        response.setHeader("Content-disposition", "inline");
        expContext.target = response.getWriter();

        PGNExport pgntask = new PGNExport(expContext.target,"utf-8");
        pgntask.setSilentTime(Integer.MAX_VALUE);
        pgntask.setSource(expContext.source);
        pgntask.run();
    }

    private static void printPdf(HttpServletResponse response, ExportContext expContext) throws Exception 
    {
        response.setContentType("application/pdf");
        response.setHeader("Content-disposition", "inline");
        expContext.target = response.getOutputStream();

        //  setup XSL-FO exporter with appropriate style sheet
        Version.loadFop();
        XSLFOExport fotask = new XSLFOExport(expContext);
        fotask.setSilentTime(Integer.MAX_VALUE);
        fotask.run();   //  wait for task to complete
    }

    private static int getGameId(SessionUtil su, int row) throws SQLException 
    {
        int gid;
        int[] id_array;
        int row0;
        SearchRecord search = (SearchRecord) su.get("work-search",true);
        JoConnection conn = null;
        try {
            conn = JoConnection.get();

            row0 = Math.max(row -10,0);
            id_array = new int[20];

            su.set("current-row0",row0);
            su.set("current-ids",id_array);

            ParamStatement pstm = search.makeIdStatement();
            pstm.setLimit(row0,id_array.length);

            JoPreparedStatement stm = pstm.execute(conn, ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY);
            ResultSet res = stm.getResultSet();
            for (int i=0; i < id_array.length && res.next(); i++)
                id_array[i] = res.getInt(1);
            res.close();

            gid = id_array[row -row0];
        } finally {
            JoConnection.release(conn);
        }
        return gid;
    }
}
