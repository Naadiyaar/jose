/*
 * This file is part of the Jose Project
 * see http://jose-chess.sourceforge.net/
 * (c) 2002-2006 Peter Schäfer
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 */

package de.jose.db.crossover;

import de.jose.Config;
import de.jose.db.JoConnection;
import de.jose.db.Setup;
import de.jose.window.JoDialog;

import java.awt.*;
import java.sql.SQLException;

/**
 * Database cross-over for Meta Version 1009
 *
 * more indexes on Game. helpful for GIGA databases.
 *
 * @author Peter Schäfer
 */

public class Crossover1009
{
	public static int crossOver(int version, JoConnection conn, Config config) throws Exception
	{
		Dialog dlg = null;
		try {
			Setup setup = new Setup(config,"MAIN",conn);
			if (version < 1009) {


				// ----------------------------------------------------
				//  Create a bunch of Indexes on Game
				// ----------------------------------------------------

				dlg = JoDialog.createMessageDialog("Database Update",
						"jose will now update the database structure for \n"+
						"improved performance.\n" +
				        "This may take 30 minutes or more. \n"+
						"Please be patient. Don't kill this process.",
				        false);
				dlg.setVisible(true);
				dlg.paint(dlg.getGraphics());

				createIndex(conn,"Game","M1","PlyCount");
				createIndex(conn,"Game","M2","CId,WhiteELO,Id");
				createIndex(conn,"Game","M3","CId,BlackELO,Id");
				createIndex(conn,"Game","M4","CId,Result,Id");
				createIndex(conn,"Game","M5","CId,GameDate,Id");
				createIndex(conn,"Game","M6","CId,ECO,Id");
			}

			setup.setTableVersion(conn,"MAIN","Game",103);
			setup.setSchemaVersion(conn,"MAIN",version=1009);
			return version;

		} finally {
			if (dlg!=null) dlg.dispose();
		}
	}

	protected static void createIndex(JoConnection conn, String tableName, String indexName, String columns)
	{
		String create = "CREATE INDEX "+tableName+"_"+indexName+
						" ON "+tableName+" ("+columns+")";
		try {
			conn.executeUpdate(create);
		} catch (SQLException e) { }
	}
}
