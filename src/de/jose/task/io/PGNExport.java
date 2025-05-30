/*
 * This file is part of the Jose Project
 * see http://jose-chess.sourceforge.net/
 * (c) 2002-2006 Peter Sch�fer
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 */

package de.jose.task.io;

import de.jose.Language;
import de.jose.Util;
import de.jose.chess.*;
import de.jose.pgn.*;
import de.jose.task.GameTask;
import de.jose.task.GameIterator;
import de.jose.task.GameHandler;
import de.jose.util.CharUtil;
import de.jose.util.file.FileUtil;
import de.jose.util.file.LinePrintWriter;

import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.List;
import java.util.Iterator;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

/**
 *
 * @author Peter Sch�fer
 */

public class PGNExport
        extends GameTask
        implements PgnConstants, GameHandler
{
	// ---------------------------------------------------------
	//  inner class: BinReader
	// ---------------------------------------------------------

	class PGNExportBinReader extends BinReader
	{
	    PGNExportBinReader(Position pos) {
	        super(pos);
	    }

	    public void annotation(int nagCode) {
			PGNExport.this.annotation(nagCode);
	    }

	    public void result(int resultCode) {
	        PGNExport.this.result(resultCode);
	    }

	    public void startOfLine(int nestLevel) {
	        if (nestLevel > 0) out.print(" (");
	    }

	    public void endOfLine(int nestLevel) {
	        if (nestLevel > 0) out.print(") ");
	    }

	    public void beforeMove(Move mv, int ply, boolean displayHint) {
	        PGNExport.this.beforeMove(mv, ply, displayHint);
	    }

		public void afterMove(Move mv, int ply) {
			PGNExport.this.afterMove(mv, ply);
	    }

        public void comment(StringBuffer buf)
        {
            PGNExport.this.comment(buf);
        }
	}

	public void result(int resultCode) {
		out.print(PgnUtil.resultString(resultCode));
	}

	public void annotation(int nagCode) {
		if (nagCode >= 1 && nagCode <= 6) {
			//	!,?, etc. these MUST be defined in the translation
			out.print(Language.get("pgn.nag."+nagCode));
		}
		else {
			out.print("$");
			out.print(String.valueOf(nagCode));
		}
		out.print(" ");
	}

	public void beforeMove(Move mv, int ply, boolean displayHint) {
		if ((ply%2)==0)  {
			out.print(String.valueOf(ply/2+1));
			out.print(".");
		} else if (displayHint) {
			out.print(String.valueOf(ply/2+1));
			out.print("...");
		}
		formatter.format(mv,pos);
		/**	format must be called before the move to detect ambigutities in short formatting	*/
	}

	public void afterMove(Move mv, int ply) {
		if (mv.isCheck())
			out.print(formatter.check);
		/**	checks can only be detected after the move */
		out.print(" ");
		out.breakIf(80);
	}

	public void comment(CharSequence buf)
	{
		if (out.column() > 0) out.print(" ");
		out.print("{");

		for (int i=0; i<buf.length(); i++)
		{
			char c = buf.charAt(i);
			switch (c)
			{
				case '{':   out.print('('); break;  //  got to escape braces
				case '}':   out.print(')'); break;  //  got to escape braces
				case ' ':
				case '\t':  if (out.breakIf(80)) {
					while ((i+1) < buf.length() && buf.charAt(i+1)==' ') i++;
				}
				else
					out.print(' ');
					break;
				default:    out.print(c); break;
			}
		}

		out.print("} ");
	}

	// ---------------------------------------------------------
	//  Fields
	// ---------------------------------------------------------

	/** output file */
	protected File outputFile;
	/** Zip output stream (only used if writing ZIP file)   */
	protected ZipOutputStream zout;
	/** GZip output stream (only used if writing GZIP file)   */
	protected GZIPOutputStream gzout;
	/** Tar output stream (only used if writing GZIP file)   */
	protected TarArchiveOutputStream tarout;
	/** BZip output stream (only used if writing GZIP file)   */
	protected BZip2CompressorOutputStream bzout;
	/** output print writer   */
	protected LinePrintWriter out;
	/** replay Position */
	protected Position pos;
	/** reads binary data   */
	protected PGNExportBinReader binReader;
	/** formats moves   */
	protected PrintMoveFormatter formatter;

	protected String charSet;

	static final int HANDLER_LIMIT   = 256;
	static final int BUFFER_LIMIT    = 256;

	// ---------------------------------------------------------
	//  Ctor
	// ---------------------------------------------------------

	public PGNExport(Object output, String charSet)
			throws Exception
	{
		super("PGN export",true);
		this.charSet = charSet;
		if (output instanceof File)
			outputFile = (File)output;
		else if (output instanceof Writer)
			out = new LinePrintWriter((Writer)output,false);
		else if (output instanceof OutputStream)
		{
			OutputStreamWriter wout = new OutputStreamWriter((OutputStream)output,charSet);
			/**	PGN specifies ISO-8859-1	!	*/
			out = new LinePrintWriter(wout,false);
		}
		else
			throw new IllegalArgumentException();
	}

	public PGNExport(Writer target) throws Exception {
		this(target,"Unicode");
	}

	public void prepare() throws Exception
	{
        OutputStream fout = null;

		if (outputFile!=null) {
            fout = new BufferedOutputStream(new FileOutputStream(outputFile),4096);
            String fileName = outputFile.getName();
            String trimmedName = FileUtil.trimExtension(fileName);

            //  TODO streamline this !
            if (FileUtil.hasExtension(fileName,"zip"))
            {
                //  ZIP
                fout = zout = new ZipOutputStream(fout);
                if (!FileUtil.hasExtension(fileName,"pgn"))
                    trimmedName += ".pgn";
                ZipEntry entry = new ZipEntry(trimmedName);
                zout.putNextEntry(entry);
            }
            else if (FileUtil.hasExtension(fileName,"gzip") || FileUtil.hasExtension(fileName,"gz"))
            {
                //  GZIP
                fout = gzout = new GZIPOutputStream(fout);
            }
            else if (FileUtil.hasExtension(fileName,"bz")
		            || FileUtil.hasExtension(fileName,"bz2")
		            || FileUtil.hasExtension(fileName,"bzip2")
		            || FileUtil.hasExtension(fileName,"bzip"))
            {
                //  BZIP
                fout = bzout = FileUtil.createBZipOutputStream(fout);
            }
			else if (FileUtil.hasExtension(fileName,"zst")
					|| FileUtil.hasExtension(fileName,"zstd"))
			{
				throw new UnsupportedOperationException("zstd output not yet implemented. but could be.");
			}
			else if (FileUtil.hasExtension(fileName,"7z"))
			{
				//	todo 7zip
				//fout = sevenzout =
				SevenZFile.Builder szfb = new SevenZFile.Builder();
				szfb.setFile(outputFile);
				szfb.setDefaultName(trimmedName);
				SevenZFile szf = szfb.get();
				SevenZArchiveEntry szen = szf.getNextEntry();
				fout = szfb.getOutputStream();
			}
        }

		pos = new Position();
		pos.setOption(Position.INCREMENT_HASH, false);
		pos.setOption(Position.INCREMENT_REVERSED_HASH, false);
		pos.setOption(Position.EXPOSED_CHECK, false);
		pos.setOption(Position.STALEMATE, false);
		pos.setOption(Position.DRAW_3, false);
		pos.setOption(Position.DRAW_50, false);
		pos.setOption(Position.DRAW_MAT, false);

        if (fout!=null) {
            OutputStreamWriter wout = new OutputStreamWriter(fout,charSet);
            /**	PGN specifies ISO-8859-1	!	*/
            out = new LinePrintWriter(wout,false);
        }
        //  else: out set in ctor !
        binReader = new PGNExportBinReader(pos);
        formatter = new PrintMoveFormatter(out);
        formatter.setFormat(MoveFormatter.SHORT);
	    formatter.enPassant = "";	//	e.p. moves are not indicated in PGN
		formatter.mate		= "+";	//	mate moves are not indicated
		formatter.stalemate	= "";
		formatter.draw3		= "";
		formatter.draw50	= "";
		formatter.nullmove 	= "--";
	}

	public int work() throws Exception
	{
		prepare();

		GameIterator gi = GameIterator.newGameIterator(source,getConnection());

		while (gi.hasNext()) {
			gi.next(this);
			processedGames++;

			if (isAbortRequested()) break;
		}

		return finish();
	}

	//  implements GameHandler; callback routines from GameIterator
	public void handleObject(Game game)
	{
		printGame(game);
	}

	public void handleRow(ResultSet res) throws Exception
	{
		printGame(res);
	}

	public int finish() throws Exception
	{
		if (out!=null)
			out.flush();
		if (tarout!=null)
			try {
				//  TAR entry must be closed (before closing the stream)
				tarout.closeArchiveEntry();
				tarout.close();
			} catch (IOException ioex) {
				return ERROR;
			}
		if (zout!=null)
			try {
				zout.closeEntry();
				zout.close();
			} catch (IOException ioex) {
				return ERROR;
			}
		if (gzout!=null)
			try {
				gzout.close();
			} catch (IOException ioex) {
				return ERROR;
			}
		if (bzout!=null)
			try {
				bzout.flush();
				bzout.close();
			} catch (IOException ioex) {
				return ERROR;
			}
		if (out!=null)
			out.close();

		return SUCCESS;
	}

	private void printGame(ResultSet res)   throws SQLException
	{
/*
            +"       Game.Id,Game.CId,Game.Idx,Attributes,PlyCount, "
            +"       WhiteId, White.Name, WhiteTitle, WhiteELO, "
            +"       BlackId, Black.Name, BlackTitle, BlackELO, "
            +"       Result, GameDate, EventDate, DateFlags, "
			+"		 EventId, Event.Name, SiteId, Site.Name, Round, Board, "
            +"       Game.ECO, OpeningId, Opening.Name, AnnotatorId, Annotator.Name, FEN, MoreGame.Info, "
            +"       MoreGame.Bin, MoreGame.Comments ";

*/
	    int i = 5;  //  1 = Id, 2 = CId, 3 = Idx, 4 = Attributes

		int plycount = res.getInt(i++);

		int whiteId = res.getInt(i++);
		String whitePlayer = res.getString(i++);
		String whiteTitle = res.getString(i++);
		int whiteElo = res.getInt(i++);

		int blackId = res.getInt(i++);
		String blackPlayer = res.getString(i++);
		String blackTitle = res.getString(i++);
		int blackElo = res.getInt(i++);

		int result = res.getInt(i++);

		Date gameDate = res.getDate(i++);
		Date eventDate = res.getDate(i++);
		short dateFlags = res.getShort(i++);
		if (gameDate!=null)
			gameDate = new PgnDate(gameDate, (short)(dateFlags & 0xff));
		if (eventDate != null)
			eventDate = new PgnDate(eventDate, (short)((dateFlags>>8) & 0xff));

		int eventId = res.getInt(i++);
		String event = res.getString(i++);
		int siteId = res.getInt(i++);
		String site = res.getString(i++);

		String round = res.getString(i++);
		String board = res.getString(i++);

		String eco = res.getString(i++);
		int openingId = res.getInt(i++);
		String openingName = res.getString(i++);
		int annotatorId = res.getInt(i++);
		String annotator = res.getString(i++);

		String fen = res.getString(i++);
		String moreTags = res.getString(i++);

		//  the following tags must appear on top, and in precisely this order:
/*
			[Event "F/S Return Match"]
			[Site "Belgrade, Serbia JUG"]
			[Date "1992.11.04"]
			[Round "29"]
			[White "Fischer, Robert J."]
			[Black "Spassky, Boris V."]
			[Result "1/2-1/2"]
*/

		//  <HEADER info="Event">
		//  some PGN parser always expect the Event tag to be first
		printHeader(TAG_EVENT,          event);

		//  <HEADER info="Site">
		if (site != null)
		    printHeader(TAG_SITE,        site);

		//  <HEADER info="Date">
	    if (gameDate != null)
	        printHeader(TAG_DATE, gameDate.toString());

		//  <HEADER info="Round">
		if (round!=null)
		    printHeader(TAG_ROUND,   round);

	    //  ["White">
	    printHeader(TAG_WHITE,           whitePlayer);

	    //  <HEADER info="Black">
	    printHeader(TAG_BLACK,           blackPlayer);

		//  [Result
		printHeader(TAG_RESULT,          PgnUtil.resultString(result));

		//  these are non-standard tags:

	    //  <HEADER info="WhiteElo">
	    if (whiteElo > 0)
	        printHeader(TAG_WHITE_ELO,   String.valueOf(whiteElo));

	    //  <HEADER info="BlackElo">
	    if (blackElo > 0)
	        printHeader(TAG_BLACK_ELO,   String.valueOf(blackElo));

	    //  <HEADER info="WhiteTitle">
	    if (whiteTitle != null)
	        printHeader(TAG_WHITE_TITLE,  whiteTitle);

	    //  <HEADER info="BlackTitle">
	    if (blackTitle != null)
	        printHeader(TAG_BLACK_TITLE,  blackTitle);

	    //  <HEADER info="EventDate">
	    if (eventDate != null)
	        printHeader(TAG_EVENT_DATE,  eventDate.toString());

		//  <HEADER info="Board">
		if (board!=null)
		    printHeader(TAG_BOARD,   board);

	    //  <HEADER info="Opening">
	    if (openingName!=null && !openingName.equals("-"))
	        printHeader(TAG_OPENING,     openingName);

	    //  <HEADER info="ECO">
	    if (eco != null && !eco.equals("-"))
	        printHeader(TAG_ECO, eco);
//	    else if (oeco != null && !oeco.equals("-"))
//	        printHeader(TAG_ECO, oeco);

		//	<HEADER info="Annotator">
		if (annotator != null && !annotator.equals("-"))
			printHeader(TAG_ANNOTATOR, annotator);

	    //  <HEADER info="FEN">
	    if (fen!=null)
	        printHeader(TAG_FEN,     fen);

	    //  <HEADER info="...">
	    if (moreTags != null) {
	        StringTokenizer tok = new StringTokenizer(moreTags, "=;");
	        while (tok.hasMoreTokens()) {
	            String key = tok.nextToken();
				if (tok.hasMoreTokens()) {
					String value = tok.nextToken();
					printHeader(key,value);
				}
				else {
					printHeader(key,"");
				}
	        }
	    }
		//  [PlyCount
		if (plycount > 0)
			printHeader(TAG_PLY_COUNT,       String.valueOf(plycount));

	    out.println();
	    //  <LINE>
	    byte[] bin = res.getBytes(i++);
		int idx = res.getInt(3);
	    byte[] comments = res.getBytes(i++);

	    try {
			binReader.read(bin,0, comments,0, fen,true);
	    } catch (RuntimeException rex) {
			//	replay error
		    out.println();
		    out.print(" {");
		    out.print(rex.getMessage());
		    out.println("} ");
		    out.flush();
		    throw rex;
		}

		if (!binReader.hasResult()) {
		    out.print(" ");
		    out.print(PgnUtil.resultString(result));
		}
		else if (binReader.getResult() != result)
			;	//	"warning: game text does not match Result Tag ?"

		out.println();
	    out.println();
	}

	public void printGame(Game gm)
	{
	    int i = 3;  //  1 = Id, 2 = Idx

		int plycount = gm.getMainLine().countMoves();;
		int result = gm.getResult();
        //  the following tags must appear on top, and in precisely this order:
/*
			[Event "F/S Return Match"]
			[Site "Belgrade, Serbia JUG"]
			[Date "1992.11.04"]
			[Round "29"]
			[White "Fischer, Robert J."]
			[Black "Spassky, Boris V."]
			[Result "1/2-1/2"]
*/
        Date gameDate = (Date)gm.getTagValue(TAG_DATE);
        Date eventDate = (Date)gm.getTagValue(TAG_EVENT_DATE);

        //  <HEADER info="Event">
        String event = (String)gm.getTagValue(TAG_EVENT);
        if (event != null)
            printHeader(TAG_EVENT,       event);

        //  <HEADER info="Site">
        String site = (String)gm.getTagValue(TAG_SITE);
        if (site != null)
            printHeader(TAG_SITE,        site);

        //  <HEADER info="Date">
        if (gameDate != null)
            printHeader(TAG_DATE, gameDate.toString());

        //  <HEADER info="Round">
        String round = (String)gm.getTagValue(TAG_ROUND);
        if (round!=null)
            printHeader(TAG_ROUND,   round);

	    //  ["White">
	    printHeader(TAG_WHITE,           (String)gm.getTagValue(TAG_WHITE));
	    //  <HEADER info="Black">
	    printHeader(TAG_BLACK,           (String)gm.getTagValue(TAG_BLACK));
		//  [Result
		printHeader(TAG_RESULT,          PgnUtil.resultString(result));


	    //  <HEADER info="WhiteElo">
	    int whiteElo = Util.toint(gm.getTagValue(TAG_WHITE_ELO));
	    if (whiteElo > 0)
	        printHeader(TAG_WHITE_ELO,   String.valueOf(whiteElo));

	    //  <HEADER info="BlackElo">
	    int blackElo = Util.toint(gm.getTagValue(TAG_BLACK_ELO));
	    if (blackElo > 0)
	        printHeader(TAG_BLACK_ELO,   String.valueOf(blackElo));

	    //  <HEADER info="WhiteTitle">
	    String whiteTitle = (String)gm.getTagValue(TAG_WHITE_TITLE);
	    if (whiteTitle != null)
	        printHeader(TAG_WHITE_TITLE,  whiteTitle);

	    //  <HEADER info="BlackTitle">
	    String blackTitle = (String)gm.getTagValue(TAG_BLACK_TITLE);
	    if (blackTitle != null)
	        printHeader(TAG_BLACK_TITLE,  blackTitle);

	    //  <HEADER info="EventDate">
	    if (eventDate != null)
	        printHeader(TAG_EVENT_DATE,  eventDate.toString());

	    //  <HEADER info="Opening">
	    String eco = (String)gm.getTagValue(TAG_ECO);
	    String openingName = (String)gm.getTagValue(TAG_OPENING);
	    if (openingName!=null && !openingName.equals("-"))
	        printHeader(TAG_OPENING,     openingName);

	    //  <HEADER info="ECO">
	    if (eco != null && !eco.equals("-"))
	        printHeader(TAG_ECO, eco);

		//	<HEADER info="Annotator">
		String annotator = (String)gm.getTagValue(TAG_ANNOTATOR);
		if (annotator != null && !annotator.equals("-"))
			printHeader(TAG_ANNOTATOR, annotator);

	    //  <HEADER info="Board">
	    String board = (String)gm.getTagValue(TAG_BOARD);
	    if (board!=null)
	        printHeader(TAG_BOARD,   board);

	    //  <HEADER info="FEN">
	    String fen = (String)gm.getTagValue(TAG_FEN);
	    if (fen!=null)
	        printHeader(TAG_FEN,     fen);

	    //  <HEADER info="...">
	    List moreTags =  gm.getMoreTags();
	    if (moreTags != null) {
	        Iterator j = moreTags.iterator();
		    while (j.hasNext())
		    {
			    TagNode tnd = (TagNode)j.next();
	            String key = tnd.getKey();
	            String value = Util.toString(tnd.getValue());
	            printHeader(key,value);
	        }
	    }
		//  [PlyCount
		printHeader(TAG_PLY_COUNT,       String.valueOf(plycount));

	    out.println();
	    //  <LINE>
		BinWriter writer = gm.getBinaryData(fen);
	    byte[] bin = writer.getText();
	    byte[] comments = writer.getComments();

	    try {
			binReader.read(bin,0, comments,0, fen,true);
	    } catch (RuntimeException rex) {
			//	replay error
		    out.println();
		    out.print(" {");
		    out.print(rex.getMessage());
		    out.println("} ");
		    out.flush();
		    throw rex;
		}

		if (!binReader.hasResult()) {
		    out.print(" ");
		    out.print(PgnUtil.resultString(result));
		}
		else if (binReader.getResult() != result)
			;	//	"warning: game text does not match Result Tag ?"

		out.println();
	    out.println();
	}

	private void printHeader(String key, String value)
	{
	    out.print("[");
	    out.print(key);
	    out.print(" \"");
		out.print(escapeTagValue(value));

		/*	PGN specifies no escape mechanism for characters outside ISO-8859-1
			but some programs use \"u or something ...

			note that PGNImport is able to decode these escaped characters, maybe we should
			be able to export them too (though it is not required by the PGN spec)
		  */
	    out.println("\"]");
	}

	/**
	 * 	Print Game DOM
	 *
	 */
	public void print(Node node)
	{
		switch(node.type())
		{
			case ANNOTATION_NODE:
				AnnotationNode a = (AnnotationNode) node;
				annotation(a.getCode());
				break;
			case COMMENT_NODE:
				CommentNode c = (CommentNode) node;
				comment(c.getText());
				break;
			case DIAGRAM_NODE:
				annotation(250);
				break;
			case LINE_NODE:
				printLine((LineNode)node,true);
				break;
			case MOVE_NODE:
				MoveNode m = (MoveNode)node;
				beforeMove(m.getMove(),m.getPly(),false);
				break;
			case RESULT_NODE:
				ResultNode r = (ResultNode)node;
				result(r.getResult());
				break;
			case STATIC_TEXT_NODE:
				break;
			case TAG_NODE:
				TagNode tn = (TagNode) node;
				printHeader(tn.getKey(),tn.getValue().toString());
				break;
		}
	}

	public void printLine(LineNode l, boolean nested)
	{
		if (nested)
			out.print(" (");
		print(l.first(),l.last());
		if (nested)
			out.print(") ");
	}

	public void print(Node n1, Node n2)
	{
		while(n1!=null)
		{
			print(n1);
			out.print(" ");

			if (n1==n2) break;
			n1 = n1.next();
		}
	}


	/**
	 * PGN specifies ISO-8859-1 encoding;
	 * other chars are escaped with \"
	 *
	 * @param text
	 * @return
	 */
	protected String escapeTagValue(String text)
	{
		if (text==null)
			return "";
		else if (charSet.equalsIgnoreCase("iso-8859-1"))
			return CharUtil.escape(text,false);
		else
			return text;
		//  false = retain ASCII <= 256, escape all others
		//  true = retain ASCII <= 128, escape all diacritic chars
	}

}
