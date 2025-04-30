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

package de.jose.web;

import de.jose.*;
import de.jose.comm.Command;
import de.jose.comm.CommandAction;
import de.jose.comm.CommandDispatcher;
import de.jose.db.JoConnection;
import de.jose.db.DBAdapter;
import de.jose.window.JoFrame;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletResponse;
import javax.servlet.ServletContext;
import javax.servlet.annotation.WebListener;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import static de.jose.comm.CommandAction.INVOKE_LATER;

/**
 * @author Peter Sch�fer
 */

@WebListener
public class WebApplication extends Application implements ServletContextListener
{
	public WebApplication()
			throws Exception
	{
		super();

		//  setup connection pool !
		JoConnection.init();
		DBAdapter adapter = JoConnection.getAdapter();
		if (adapter.getServerMode()==DBAdapter.MODE_STANDALONE) {
			Thread launcher = adapter.launchProcess();
			for(;;) try {
				launcher.join();
				break;
			} catch (InterruptedException e) {
				continue;
			}
		}
		//  read profile data for html export, etc.
		readProfile();
		//  always create a separate css file
		theUserProfile.set("xsl.css.standalone",true);
		theUserProfile.set("xsl.pdf.embed",true);	// always embed fonts into pdf
		theUserProfile.set("xsl.pdf.bookmarks",false);
		theUserProfile.getStyleContext().setFigurineFont(true);
		theUserProfile.set("xsl.html.figs","tt");	// use TrueType figurine in html
		serverMode = true;	//	use relative font path (required for web server)
		Language.setLanguage(theLanguageDirectory, theUserProfile.getString("user.language"));

		theCommandDispatcher = new CommandDispatcher();
		//theCommandDispatcher.addCommandListener(this);
	}

	public static Application open(ServletContext context, ServletResponse response)
	{
		if (Application.theApplication==null)
			synchronized (context)
			{
				try {
					if (Application.theApplication==null) {
						//  first call, load application config and database stuff
						Version.setSystemProperty("jose.splash","off");
						//File jspDir = new File(context.getRealPath(""));
						//Version.setSystemProperty("jose.workdir", jspDir.getParentFile().getParent());
						//  web directory is web/resource. Working directory is two levels above.
						new WebApplication();
					}
				} catch (Throwable ex) {
                    try {
                        ex.printStackTrace(response.getWriter());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
			}
		return theApplication;
	}

	public static void close(ServletContext context, ServletResponse response)
	{
		try {
			if (theApplication!=null)
				synchronized (context)
				{
					if (theApplication!=null) {
						//  close connection pool
						try {
							DBAdapter ad = JoConnection.getAdapter(false);
							if (ad!=null && (ad.getServerMode()==DBAdapter.MODE_STANDALONE) && JoConnection.isConnected()) {
								ad.shutDown(JoConnection.get());
							}

							JoConnection.closeAll();

						} catch (Throwable thr) {
							Application.error(thr);
						}
					}
				}
		} catch (Throwable ex) {
			if (response!=null) {
                try {
                    ex.printStackTrace(response.getWriter());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
		}
	}

	@Override
	public void setupActionMap(Map<String, CommandAction> map) {
		super.setupActionMap(map);

		CommandAction action = new CommandAction() {
			public void Do(Command cmd) throws Exception {
				String errorMessage = Language.get(cmd.code);
				throw new IllegalStateException(errorMessage);
			}
		};
		map.put("error.duplicate.database.access",action);

	}

	@Override
	public void contextInitialized(ServletContextEvent sce) {
        open(sce.getServletContext(),null);
    }

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
        close(sce.getServletContext(),null);
    }
}
