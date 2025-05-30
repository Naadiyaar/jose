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

package de.jose;

import com.formdev.flatlaf.FlatLaf;
import de.jose.book.BookQuery;
import de.jose.chess.*;
import de.jose.comm.Command;
import de.jose.comm.CommandAction;
import de.jose.comm.CommandDispatcher;
import de.jose.comm.CommandListener;
import de.jose.comm.msg.DeferredMessageListener;
import de.jose.db.DBAdapter;
import de.jose.db.DBRepairTool;
import de.jose.db.JoConnection;
import de.jose.db.MySQLAdapter;
import de.jose.eboard.ChessNutConnector;
import de.jose.eboard.EBoardConnector;
import de.jose.export.ExportConfig;
import de.jose.export.ExportContext;
import de.jose.export.HtmlUtil;
import de.jose.help.HelpSystem;
import de.jose.image.Surface;
import de.jose.image.TextureCache;
import de.jose.pgn.Collection;
import de.jose.pgn.*;
import de.jose.plugin.EnginePlugin;
import de.jose.plugin.InputListener;
import de.jose.plugin.Plugin;
import de.jose.plugin.Score;
import de.jose.profile.FrameProfile;
import de.jose.profile.LayoutProfile;
import de.jose.profile.UserProfile;
import de.jose.task.DBTask;
import de.jose.task.GameSource;
import de.jose.task.GameTask;
import de.jose.task.db.*;
import de.jose.task.io.*;
import de.jose.util.*;
import de.jose.util.file.FileUtil;
import de.jose.util.file.ResourceClassLoader;
import de.jose.util.print.PrintableDocument;
import de.jose.view.*;
import de.jose.view.input.LookAndFeelList;
import de.jose.view.input.WriteModeDialog;
import de.jose.view.input.JoStyledLabel;
import de.jose.view.style.JoStyleContext;
import de.jose.window.*;
import de.jose.book.OpeningLibrary;
import de.jose.book.BookEntry;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.io.*;
import java.net.*;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static de.jose.book.OpeningLibrary.SELECT_GAME_COUNT;
import static de.jose.chess.Board.XFEN;
import static de.jose.comm.CommandAction.INVOKE_LATER;
import static de.jose.db.DBAdapter.*;
import static de.jose.plugin.Plugin.*;

/**
 *	the main application class
 *
 *  (it is NOT intended to be run as an Applet; this is just for derived classes like FlyBy)
 */

public class Application
        extends AbstractApplication
		implements ActionListener, AWTEventListener, DeferredMessageListener, InputListener, ClipboardOwner
{
	//-------------------------------------------------------------------------------
	//	Constants
	//-------------------------------------------------------------------------------


    /**	window icon
	 */
	public static final String ICON_IMAGE = "images"+File.separator+"ico"+File.separator+"jose128.png";

	public static final String USER_PROFILE = ".jose.user.preferences";

	public static final String DEFAULT_DATABASE = "MySQL";

	/**	Game Mode	*/
	public enum AppMode {
		unused(0),
		/**	User input, no engine analysis (default)	*/
		USER_INPUT (1),
		/**	User input with background analysis	*/
		ANALYSIS (2),
		/**	User vs. Engine	*/
		USER_ENGINE	(3),
		/**	Engine 1 vs. Rngine 2 (not implemented)	*/
		ENGINE_ENGINE (4);

		AppMode(int numval) { this.numval = numval; }
		public final int numval;

		static AppMode valueOf(Object val) {
			if (val instanceof Number)
				return values() [((Number)val).intValue()];
			else
				return AppMode.valueOf(val.toString());
		}
	}
	public enum PlayState {
		//	none of the below; user can move
		//	(includes background analysis)
		NEUTRAL,
		//	BOOK_PLAY query has been sent
		BOOK,
		//	plugin.THINKING
		ENGINE
	}

	//-------------------------------------------------------------------------------
	//	Fields
	//-------------------------------------------------------------------------------

	public static Application	theApplication;

	public boolean				isHeadless=false;

	/**	directory where language.properties files are stored	 */
	public File					theLanguageDirectory;

	/**	database Id	 */
	public String				theDatabaseId;
//	public int					theDatabaseMode;

	/**	game mode	*/
	public AppMode				theMode;
	public PlayState			thePlayState = PlayState.NEUTRAL;

	/**	database directory (for embeded databases only)
	 * 	default is <working directory> / database
	 */
	public static File			theDatabaseDirectory;

	/**
	 * Application is running in a server context, e.g. WebApplication
	 * - no gui
	 * - export.collateral refers to a server-side directory
	 * - html url paths must be relative to the above
	 */
	public static boolean		serverMode = false;

    /** game history    */
    public static History       theHistory;

	/**	global context menu	 */
//	public ContextMenu			theContextMenu;

	/**	globals configuration	 */
	public Config				theConfig;

	/** export/print config; created on demand */
	public ExportConfig         theExportConfig;

    /** the component that has the keyboard focus (may be null) */
    public Component            theFocus;

	/**	ECO classificator	*/
	public ECOClassificator		theClassificator;
	static Object loadClassificator = new Object(); // lock

	/** Move Announcements (by sound)   */
	public SoundMoveFormatter   theSoundFormatter;

	/** thread pool for general use */
	public static ExecutorService theExecutorService = Executors.newFixedThreadPool(4);

	/** opening books   */
	public OpeningLibrary theOpeningLibrary;

    private ApplicationListener applListener;

	/**	context menu (when user clicks right mouse button)	*/
	private ContextMenu			contextMenu;

	/** performs a clean shutdown on System.exit()
	 *  e.g. when called externally
	 */
	private Thread              shutdownHook;

	/** help system */
	protected HelpSystem        helpSystem;
	protected Rectangle			helpBounds;

	public EBoardConnector	eboard=null;

	protected boolean shownFRCWarning=false;


	//-------------------------------------------------------------------------------
	//	Constructor
	//-------------------------------------------------------------------------------


	public Application()
		throws Exception
	{
		super();

		if (theApplication != null)
			throw new IllegalStateException("Application must not be initialized twice");

		theApplication	        = this;
		isHeadless				= Version.getSystemProperty("java.awt.headless",false);

        theWorkingDirectory     = getWorkingDirectory();
//		System.out.println("working dir="+theWorkingDirectory);
/*
		try {
			//  chess fonts that are distributed with jose
			File joseFontPath = new File(theWorkingDirectory,"fonts");
			//	IMPORTANT: make this call before the graphics system is initialized
			ClassPathUtil.setUserFontPath(joseFontPath.getAbsolutePath());
			//  @deprecated custom fonts are loaded on demand by FontEncoding
		} catch (Exception e) {
			error(e);
		}
*/
		if (!Version.getSystemProperty("jose.console.output",false))
		{	//  send standard output to error.log
			PrintStream out = new PrintStream(new FileOutputStream("error.log"));
			System.setOut(out);
			System.setErr(out);
		}

		theIconImage = Toolkit.getDefaultToolkit().createImage(
							theWorkingDirectory+File.separator+ICON_IMAGE);

        if (Version.getSystemProperty("jose.splash",true))
        {
            SplashScreen splash = SplashScreen.open();
            splash.setImageDir(new File(theWorkingDirectory,"images"));
            WinUtils.setTopMost(splash);
        }

		if (!Version.getSystemProperty("jose.3d",true))
			Version.disable3d();

//		theDatabaseMode		= JoConnection.READ_WRITE;
		boolean showSystemProperties    = Version.getSystemProperty("jose.debug.properties",false);

        theDatabaseId           = Version.getSystemProperty("jose.db",theDatabaseId);

        String dataDir          = Version.getSystemProperty("jose.datadir",null);
        if (dataDir != null)    theDatabaseDirectory = new File(dataDir).getCanonicalFile();

        //showFrameRate           = Version.getSystemProperty("jose.framerate",false);
        logErrors               = Version.getSystemProperty("jose.log",true);
		showErrors				= Version.getSystemProperty("jose.show.errors",true);

		theLanguageDirectory = new File(theWorkingDirectory,"config");

		theConfig = new Config(new File(theWorkingDirectory,"config"));

		//  improved screen repainting for JDK 1.6 (?)
		Version.setSystemProperty("sun.awt.noerasebackground", "true");
		//  improved text antialising for JDK 1.6
//		Version.setSystemProperty("swing.aatext","true");
//		antialiasing can be set by the user. don't override it.
		//  improved drag & drop gestures, since JDK 1.5
		Version.setSystemProperty("sun.swing.enableImprovedDragGesture","true");
		//  some Type1 fonts cause crashes
		Version.setSystemProperty("sun.java2d.noType1Font","true");

        if (Version.mac) {
            //  Mac OS X properties

            //	set Mac OS menu bar (menu bar on top of screen, not inside each window)
            Version.setDefaultSystemProperty("apple.laf.useScreenMenuBar","true");

            //  show grow box in lower-right corner ?
            //  I think this is the default for Aqua lnf, so we let it be...
            Version.setDefaultSystemProperty("apple.awt.showGrowBox","true");

			// adapt window title bars
			System.setProperty("apple.awt.application.appearance","system");
			//	application menu name
			System.setProperty("apple.awt.application.name", "jose");

            Version.setDefaultSystemProperty("apple.awt.brushMetalLook","false");
            Version.setDefaultSystemProperty("apple.awt.graphics.EnableLazyDrawing","true");

            //	turn on dynamic repainting during resize (still needed ?)
            Version.setDefaultSystemProperty("com.apple.mrj.application.live-resize","true");

            //  does this work ?
            Version.setSystemProperty("com.apple.mrj.application.apple.menu.about.name",
                    Version.getSystemProperty("jose.about.name","jose"));

            //  avoid too many disk reads in File Chooser
            Version.setDefaultSystemProperty("Quaqua.FileChooser.autovalidate","false");

//            Version.setDefaultSystemProperty("Quaqua.design","jaguar");
//            Version.setDefaultSystemProperty("Quaqua.TabbedPane.design","jaguar");
            Version.setDefaultSystemProperty("Quaqua.List.style","striped");
            Version.setDefaultSystemProperty("Quaqua.Table.style","striped");

            //  hint to file chooser: navigate into application bundles
            //  important since, among others, engines are stored in jose's bundle
            UIManager.put("JFileChooser.appBundleIsTraversable","always");
//          UIManager.put("JFileChooser.packageIsTraversable","always"); // don't navigate in packages
        }

		if (theDatabaseId == null)
			theDatabaseId = theConfig.getDefaultDataSource();
		if (theDatabaseId == null)
			theDatabaseId = DEFAULT_DATABASE;
		if (theDatabaseDirectory==null)
			theDatabaseDirectory = new File(theWorkingDirectory,"database");

//		splashToFront();

		if (showSystemProperties) {
			System.getProperties().list(System.out);
//			System.out.println(System.getProperty("java.awt.fonts"));
//			UIDefaults def = UIManager.getLookAndFeelDefaults();
//			System.out.println(def);
		}

		if (Version.getSystemProperty("jose.debug.sql",false))
//			DriverManager.setLogWriter(new PrintWriter(new FileWriter(new File(theWorkingDirectory,"sql.log")),true));
			DriverManager.setLogWriter(new PrintWriter(System.err,true));

		if (!isHeadless)
		{
			Toolkit.getDefaultToolkit().setDynamicLayout(true);
			/** register for FcousEvents   */
			Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.FOCUS_EVENT_MASK);

			/**	light weight menus do not mix well with Java3D
			 *	(the menus appear behind the J3D canvas :-(
			 */
			JPopupMenu.setDefaultLightWeightPopupEnabled(
					Version.getSystemProperty("jose.2d.light.popup",
							!Version.hasJava3d(false,false)));
		}
	}

    public static void parseProperties(String[] args)
    {
	    StringBuffer files = new StringBuffer();

        for (int i=0; i<args.length; i++)
        {
            String key = args[i];
            String value = null;

	        if (FileUtil.exists(key) || Util.isValidURL(key)) {
	            if (files.length() > 0) files.append(File.pathSeparator);
		        files.append(key);
		        continue;
	        }

            if (key.startsWith("-D") || key.startsWith("-d"))
                key = key.substring(2);
            else {
				if (key.startsWith("-"))
	                key = key.substring(1);
				if (!key.startsWith("jose."))
					key = "jose."+key;
			}

            int k = key.indexOf("=");
            if (k < 0) {
                if ((i+1) < args.length && args[i+1].indexOf("=")<=0)
                {
                    value = args[++i];
                    if (value.equals("=")) {
                        if ((i+1) < args.length)
                            value = args[++i];
                        //  key = value
                    }
                    else if (value.startsWith("=")) {
                        value = value.substring(1);
                        //  key =value
	                } else {
                        //  value might be a file or url
	                    if (FileUtil.exists(value) || Util.isValidURL(value)) {
		                    if (files.length() > 0) files.append(File.pathSeparator);
	                        files.append(value);
		                    value = null;
		                    continue;
	                    }
                    }
                    // else: key value
                }
	            //  else: key only
            }
            else if ((k+1)==key.length()) {
                if ((i+1) < args.length)
                    value = args[++i];
                key = key.substring(0,k);
                //  key= value
            }
            else {
                value = key.substring(k+1);
                key = key.substring(0,k);
                //  key=value
            }

	        if (key.equalsIgnoreCase("jose.file") || key.equalsIgnoreCase("jose.url")) {
		        //  add file
		        if (files.length() > 0) files.append(File.pathSeparator);
		        files.append(value);
		        continue;
	        }
			else {
				if (value==null) value = "true";
				Version.setSystemProperty(key,value);
	        }
        }

	    if (files.length() > 0)
		    Version.setSystemProperty("jose.file", files.toString());

    }

	public void splashToFront()
	{
        if (Version.windows)
            /* WinUtils already deals with it */ ;
        else {
            SplashScreen splash = SplashScreen.get();
            if (splash!=null && splash.isShowing()) splash.toFront();
        }
	}

    static class ApplicationListener extends Thread
    {
        protected ServerSocket inBound;

		ApplicationListener() {
			super("jose.appl-listener");
			setPriority(Thread.MIN_PRIORITY);
			setDaemon(true);
		}

        public void close() {
            try {
                if (inBound!=null) inBound.close();
                inBound = null;
            } catch (IOException e) {
                //  ignore
            }
        }

        public void run()
        {
            try {
				inBound = new ServerSocket(0x105E);

                while (inBound != null)
                    try {

                        Socket incoming = inBound.accept();
                        //  ping from other application; bring this application to the front
//						incoming.getInputStream().read();
                        JoFrame.activeToFront();

                        incoming.close();

                    }  catch (Exception ex) {
                        continue;
                    }

            } catch (Throwable e1) {
//              Application.error(e1);
				// couldn't create server socket (strict firewall ?)
            }
        }

		/**
		  * is there another instance running on the local machine ?
		  */
		 protected static boolean searchApplication()
		 {
			 /** try to connect  */
			 try {
				 byte[] localip = { 127,0,0,1 };
				 Socket outBound = new Socket(InetAddress.getByAddress(localip),0x105E);
//					 outBound.sendUrgentData(0x01);

				 try { outBound.close(); } catch (IOException e) { }

				 return true;    //  another running application was detected

			 } catch (Throwable e) {
				 //  connection failed = there is no other application = fine !
			 }
			 //  no other application was detected
			 return false;
		 }
	}

	//-------------------------------------------------------------------------------
	//	main entry point
	//-------------------------------------------------------------------------------


	public static void main(String[] args)
		throws Exception
	{
		parseProperties(args);

//		JoDialog.showMessageDialog(Util.toString(args));

		new Application().open();
	}

	//-------------------------------------------------------------------------------
	//	basic access
	//-------------------------------------------------------------------------------

	public final DocumentPanel docPanel()		{ return (DocumentPanel)JoPanel.get("window.game"); }
	public final ConsolePanel consolePanel()	{ return (ConsolePanel)JoPanel.get("window.console"); }
	public final ClockPanel clockPanel()		{ return (ClockPanel)JoPanel.get("window.clock"); }
    public final ListPanel listPanel()          { return (ListPanel)JoPanel.get("window.gamelist"); }
    public final QueryPanel queryPanel()          { return (QueryPanel)JoPanel.get("window.query"); }
    public final CollectionPanel collectionPanel()  { return (CollectionPanel)JoPanel.get("window.collectionlist"); }
	public final SymbolBar symbolToolbar()		{ return (SymbolBar)JoPanel.get("window.toolbar.symbols"); }
	public final EnginePanel enginePanel()      { return (EnginePanel) JoPanel.get("window.engine"); }
	public final EvalPanel evalPanel()      	{ return (EvalPanel) JoPanel.get("window.eval"); }

	public final JoDialog openDialog(String name) throws Exception
	{
		return openDialog(name,-1);
	}

	public final JoDialog openDialog(String name, int tab) throws Exception
	{
		JoDialog dialog = getDialog(name);
		openDialog(dialog,tab);
		return dialog;
	}

	public void openDialog(JoDialog dialog, int tab) throws Exception
	{
		dialog.read();

		if (dialog instanceof JoTabDialog && tab >= 0)
			((JoTabDialog)dialog).setTab(tab);

		dialog.show();
		dialog.toFront();
//		dialog.updateLanguage();    //  this will mess up label with contents; why was is needed ???
	}

	public final JoDialog getDialog(String name)
	{
		JoDialog dialog = JoDialog.getDialog(name);
		if (dialog==null)
			dialog = JoDialog.create(name);
		return dialog;
	}

	//-------------------------------------------------------------------------------
	//	Complex Methods
	//-------------------------------------------------------------------------------

	public final void setLanguage(String lang)
		throws IOException
	{
		Language.setLanguage(theLanguageDirectory,lang);
		if (theClassificator!=null) theClassificator.setLanguage(theLanguageDirectory,lang);

		broadcast(new Command("update.language", null, lang));
	}

	public final void setLookAndFeel(String lookAndFeel)
		throws Exception
	{
		String className = LookAndFeelList.loadLookAndFeel(lookAndFeel);

		if (className==null)
			JoDialog.showErrorDialog("error.lnf.not.supported");
		else
			try {
				Class<?> lnfClass = Class.forName(className);
				LookAndFeel lnf = (LookAndFeel) (lnfClass.newInstance());
				useLookAndFeel(lnf);
			} catch (UnsupportedLookAndFeelException usex) {
				JoDialog.showErrorDialog("error.lnf.not.supported");
			}
	}

	private void useLookAndFeel(LookAndFeel lnf) throws UnsupportedLookAndFeelException
	{
		if (lnf instanceof FlatLaf) {
			FlatLaf.setSystemColorGetter(name -> {
				if (name.equals("accent"))
					return theUserProfile.getAccentColors()[1];
				return null;
			});

			FlatLaf.registerCustomDefaultsSource(
					"themes",
					new ResourceClassLoader("config"));
			//	uses a set of custom themes

			FlatLaf.setup(lnf);

			boolean isdark = ((FlatLaf) lnf).isDark();
		}

		UIManager.setLookAndFeel(lnf);
		UIManager.put("TextPane.selectionBackground", theUserProfile.getAccentColors()[0]);

		broadcast(new Command("update.ui", null, /*lnfName*/null, isDarkLookAndFeel()));
	}

	public final void resetLookAndFeel()
	{
		LookAndFeel lnf = UIManager.getLookAndFeel();
        try {
            useLookAndFeel(lnf);
        } catch (UnsupportedLookAndFeelException e) {
			JoDialog.showErrorDialog("error.lnf.not.supported");
        }
    }

	public boolean isDarkLookAndFeel()
	{
		LookAndFeel lnf = UIManager.getLookAndFeel();
		if (lnf instanceof FlatLaf) {
			return ((FlatLaf) lnf).isDark();
		}
		return false;
	}

	//-------------------------------------------------------------------------------
	//	interface ActionListener
	//-------------------------------------------------------------------------------

	public void actionPerformed(ActionEvent e)
	{
		theCommandDispatcher.handle(e,this);
	}

    //-------------------------------------------------------------------------------
    //	interface AWTEventListener
    //-------------------------------------------------------------------------------

    public void eventDispatched(AWTEvent event)
    {
        /** we are only registered for FocusEvents  */
        FocusEvent fevt = (FocusEvent)event;

        if (!fevt.isTemporary())
            switch (event.getID()) {
//            case FocusEvent.FOCUS_LOST:
//                    theFocus = fevt.getOppositeComponent(); break;	//	since 1.4
            case FocusEvent.FOCUS_GAINED:
	                JoPanel oldFocusPanel = getFocusPanel();
//	                Component oldFocus = theFocus;

                    theFocus = fevt.getComponent();
	                JoPanel newFocusPanel = getFocusPanel();

//	                if (oldFocus!=theFocus)
//	                    broadcast(new Command("focus.changed",event,oldFocus,theFocus));
	            //  at the time, we don't need such fine grained broadcasts
	                if (oldFocusPanel!=newFocusPanel)
	                    broadcast(new Command("focus.panel.changed",event,oldFocusPanel,newFocusPanel));
	            //  it is good enough to notify changes in the panel (toolbars may adjust their buttons)
	                break;
            }
    }

	public ContextMenu getContextMenu()
	{
		if (contextMenu==null)
			synchronized (this)
			{
				if (contextMenu==null)
					contextMenu = new ContextMenu();
			}
		return contextMenu;
	}

	public boolean isContextMenuShowing()
	{
		return contextMenu!=null && contextMenu.isShowing();
	}

	public ECOClassificator getClassificator()
	{
		if (theClassificator==null)
			try {
				synchronized(loadClassificator) {
					if (theClassificator==null) {
						ECOClassificator classificator = new ECOClassificator(false);
						classificator.open(new File(theLanguageDirectory,"eco.key"));
						classificator.setLanguage(theLanguageDirectory, Language.theLanguage.langCode);
						theClassificator = classificator;
                    }
				}
			} catch (Exception e) {
				Application.error(e);
			}
		return theClassificator;
	}

	public ECOClassificator getClassificator(String language)
	{
		if (language.equals(Language.theLanguage.langCode))
			return getClassificator();

		ECOClassificator result = null;
		try {
			if (theClassificator != null)
				result = new ECOClassificator(theClassificator);
			else {
				result = new ECOClassificator(false);
				result.open(new File(theLanguageDirectory,"eco.key"));
			}
			result.setLanguage(theLanguageDirectory, language);
		} catch (Exception e) {
			Application.error(e);
		}

		return result;
	}

	public SoundMoveFormatter getSoundFormatter()
	{
		if (theSoundFormatter==null)
			try {
				File dir = (File)theUserProfile.get("sound.moves.dir");
				if (dir!=null && dir.exists()) {
					SoundMoveFormatter sform = new SoundMoveFormatter();
					sform.setDirectory(dir);
					sform.setPronounceMate(false);
					theSoundFormatter = sform;
				}
			} catch (IOException ioex) {
				Application.error(ioex);
			}

		return theSoundFormatter;
	}

	public void updateSoundFormatter(UserProfile profile) throws IOException
	{
		if (theSoundFormatter!=null)
		{
			File dir = (File)profile.get("sound.moves.dir");
			if (dir!=null && dir.exists())
				theSoundFormatter.setDirectory(dir);
			else
				theSoundFormatter = null;
		}
	}

	public void speakMove(int format, Move mv, Position pos)
	{
		getSoundFormatter();
		if (theSoundFormatter!=null)
			theSoundFormatter.format(format,mv,pos);
	}

	public void speakAcknowledge()
	{
		getSoundFormatter();
		if (theSoundFormatter==null || !theSoundFormatter.play("Oke.wav"))
			Sound.play("sound.notify");
	}

    /**
     * @return the currently focused component (may be null)
     */
    public Component getFocus() { return theFocus; }

    /**
     * @return the panel that contains the current focus; null if there is no focus,
     *  or if the focused compoment does not belong to a JoPanel
     */
    public JoPanel getFocusPanel()
    {
        if (theFocus==null) {
            //  get the frontmost panel
            JoFrame jframe = JoFrame.getActiveFrame();
            if (jframe!=null)
                return jframe.getAnchorPanel();
        }
        else {
            for (Component comp = theFocus; comp != null; comp = comp.getParent())
                if ((comp instanceof JoPanel) && ((JoPanel)comp).isFocusable())
                    return (JoPanel)comp;
        }
        //  no focus set, or not part of a JoPanel
        return null;
    }

	public void updateClock()
	{
		theUserProfile.getTimeControl().update(theClock,
										  theGame.getPosition().gameMove(),
										  theGame.getPosition().movedLast());
		theClock.setCurrent(theGame.getPosition().movesNext());
	}

	//-------------------------------------------------------------------------------
	//	interface CommandListener
	//-------------------------------------------------------------------------------

	public int numCommandChildren()
	{
		return JoFrame.countFrames() + JoDialog.countAllDialogs();
	}
	public CommandListener getCommandChild(int i)
	{
		if (i < JoFrame.countFrames())
			return JoFrame.getFrame(i);
		else
			return JoDialog.getAllDialogs() [i-JoFrame.countFrames()];
	}

	public void broadcast(Command cmd)
	{
		theCommandDispatcher.broadcast(cmd,this);
	}

	public void broadcast(String code)
	{
		broadcast(new Command(code,null,null));
	}

	public void setupActionMap(Map<String, CommandAction> map)
	{
		CommandAction action;

		action = new CommandAction() {
			public boolean isEnabled(String code) {
				return AbstractApplication.theCommandDispatcher.canUndo();
			}

			public String getDisplayText(String code) {
				if (AbstractApplication.theCommandDispatcher.canUndo()) {
					HashMap params = new HashMap();
					CommandAction undoAction = AbstractApplication.theCommandDispatcher.getUndoAction();
					Command cmd = AbstractApplication.theCommandDispatcher.getUndoCommand();
					params.put("action", undoAction.getDisplayText(cmd.code));
					return StringUtil.replace(Language.get("menu.edit.undo"), params);
				}
				else
					return Language.get("menu.edit.cant.undo");
			}

			public void Do(Command cmd) {
				theCommandDispatcher.Undo();
			}
		};
		map.put("menu.edit.undo", action);

		action = new CommandAction() {
			public boolean isEnabled(String code) {
				return AbstractApplication.theCommandDispatcher.canRedo();
			}

			public String getDisplayText(String code) {
				if (AbstractApplication.theCommandDispatcher.canRedo()) {
					HashMap params = new HashMap();
					CommandAction redoAction = AbstractApplication.theCommandDispatcher.getRedoAction();
					Command cmd = AbstractApplication.theCommandDispatcher.getRedoCommand();
					params.put("action", redoAction.getDisplayText(cmd.code));
					return StringUtil.replace(Language.get("menu.edit.redo"), params);
				}
				else
					return Language.get("menu.edit.cant.redo");
			}

			public void Do(Command cmd) {
				theCommandDispatcher.Redo();
			}
		};
		map.put("menu.edit.redo", action);

		action = new CommandAction() {
			public void Do(Command cmd) {
				try {
					gameMaintenance(cmd,null,null);
				} catch (Exception ex) {
					error(ex);
				}
			}
		};
		map.put("menu.edit.empty.trash", action);

		action = new CommandAction() {
			public void Do(Command cmd) throws Exception {
				newCollection(cmd);
			}
		};
		map.put("menu.edit.collection.new", action);

		action = new CommandAction() {
			public void Do(Command cmd) throws Exception {
				//  forward to collection panel
                GameSource src;
                Collection target = (Collection)cmd.moreData;   //  optional

                if (cmd.data instanceof GameSource)
                    src = (GameSource)cmd.data;    //  game source explicitly passed
                else if (getFocusPanel() instanceof ClipboardOwner)
                    src = null;   //  focused panel can handle clipboard
                else
                    src = getGameSource(cmd,false,true);
                //  collectionOnly==false: all sources can act as copy sources
				//	selectionOnly==true don't cut the whole list panel

				if (src!=null) {
					//	game/collection (DB) operation
					gameMaintenance(cmd, src, target);
				}
				else {
					//	system clipboard operation
					theCommandDispatcher.forward(cmd, getFocusPanel());
				}
			}
		};
		map.put("menu.edit.cut", action);
		map.put("menu.edit.copy", action);
		map.put("menu.edit.clear", action);
		map.put("menu.edit.restore", action);
		map.put("menu.edit.erase", action);
		map.put("menu.edit.collection.crunch", action);


        action = new CommandAction() {
            public void Do(Command cmd) throws Exception {
                //  forward to collection panel
                GameSource src;
                Collection target = (Collection)cmd.moreData;   //  optional

                if (cmd.data instanceof GameSource)
                    src = (GameSource)cmd.data;    //  game source explicitly passed
                else if (getFocusPanel() instanceof ClipboardOwner)
                    src = null;   //  focused panel can handle clipboard
                else
                    src = getGameSource(cmd,target==null,false);
                //  collectionOnly==true: only collection can act as paste targets

                if (src!=null) {
                    //	game/collection (DB) operation
                    gameMaintenance(cmd, src, target);
                }
                else {
                    //	system clipboard operation
                    theCommandDispatcher.forward(cmd, getFocusPanel());
                }
            }
        };
        map.put("menu.edit.paste", action);
        map.put("menu.edit.paste.copy", action);
        map.put("menu.edit.paste.same", action);


		/**
		 * forward to the document panel
		 * but keep here for accessibility
		 */
		action = new CommandAction() {
			public CommandListener forward(CommandListener current)
			{
				JoPanel doc = docPanel();
				if (doc!=null && doc.isShowing())
					return doc;
				else
					return null;
			}
		};
		map.put("menu.edit.bold",action);
		map.put("menu.edit.italic",action);
		map.put("menu.edit.underline",action);
		map.put("menu.edit.plain",action);

		map.put("menu.edit.larger",action);
		map.put("menu.edit.smaller",action);
		map.put("menu.edit.color",action);

		map.put("menu.edit.left",action);
		map.put("menu.edit.center",action);
		map.put("menu.edit.right",action);

		map.put("move.format.short",action);
		map.put("move.format.long",action);
		map.put("move.format.algebraic",action);
		map.put("move.format.correspondence",action);
		map.put("move.format.english",action);
		map.put("move.format.telegraphic",action);

        map.put("figurine.usefont.true",action);
        map.put("figurine.usefont.false",action);

		action = new CommandAction() {
			public void Do(Command cmd) {
				//  paste a variation line into the current document
				MoveNode mvnd = null;
//				if (cmd.data instanceof LichessEndgameQuery) { todo
//					NalimovOnlineQuery nq = (NalimovOnlineQuery)cmd.data;
//					mvnd = GameUtil.pasteLine(nq.getGame(), nq.getMoveNode(), nq.getText());
//				}
				mvnd = GameUtil.pasteLine(theGame, theGame.getCurrentMove(), cmd.data.toString());
				if (Util.toboolean(cmd.moreData) && mvnd!=null) {
					//  ... and replay the line
					cmd = new Command("move.goto", null, mvnd);
					Application.theCommandDispatcher.forward(cmd, Application.this);
				}
				else {
					//  notify, though position was not changed
				cmd = new Command("move.notify",null,null,Boolean.TRUE);
				broadcast(cmd);
			}
			}
		};
		map.put("menu.game.paste.line",action);

		action = new CommandAction() {
			@Override
			public boolean isEnabled(String cmd) {
				return theGame.canInsertNullMove();
			}

			public void Do(Command cmd) {
				if (!theGame.canInsertNullMove()) return;

				theGame.insertNullMove();
				cmd = new Command("move.notify",null,new Move(Move.NULLMOVE));
				broadcast(cmd);

				//	turn on analysis
				cmd = new Command("menu.game.analysis");
				theCommandDispatcher.forward(cmd,enginePanel());
			}
		};
		map.put("menu.game.threat",action);

        action = new CommandAction() {
            public void Do(Command cmd) throws Exception
            {
				/*
                GameSource src = getGameSource(cmd,false,false);
                CreatePositionIndex2 task = new CreatePositionIndex2();
                task.setSource(src);
                task.start();
				 */
				throw new UnsupportedOperationException();
            }
        };
        map.put("menu.edit.position.index",action);

        action = new CommandAction() {
            public void Do(Command cmd) throws Exception
            {
                GameSource src = getGameSource(cmd,false,false);
                if (src!=null) {
					EcofyTask task = new EcofyTask();
					task.setSource(src);
					if (task.askParameters(JoFrame.theActiveFrame, theUserProfile))
						task.start();
                }
            }
        };
        map.put("menu.edit.ecofy",action);


        
		action = new CommandAction() {
			public void Do(Command cmd) throws Exception {
				//  forward to collection panel
				GameSource src = (GameSource)cmd.data;
				Collection target = (Collection)cmd.moreData;

				//	game/collection related
				gameMaintenance(cmd, src, target);
			}
		};
		map.put("dnd.move.games",action);

		action = new CommandAction() {
			public void Do(Command cmd) throws Exception {
				//	move collection to top level
				GameSource src = (GameSource)cmd.data;
                Collection target = (Collection)cmd.moreData;   //  not needed here, but who knows...
				gameMaintenance(cmd, src, target);
			}
		};
		map.put("dnd.move.top.level",action);


        action = new CommandAction() {
            public void Do(Command cmd) throws Exception {
                getHelpSystem().init();

                Component focus = (Component)cmd.data;
				if (focus==null) getFocus();
                if (focus==null) focus = getFocusPanel();

                getHelpSystem().show(focus);
            }
        };
        map.put("menu.help.context", action);

        action = new CommandAction() {
            public void Do(Command cmd) throws Exception {
                getHelpSystem().init();
                getHelpSystem().showHome();
            }
        };
        map.put("menu.help.manual", action);

		action = new CommandAction() {
			@Override
			public boolean isEnabled(String cmd) {
				//	not available for external databases
				return JoConnection.getAdapter().getServerMode() != DBAdapter.MODE_EXTERNAL;
			}
			public void Do(Command cmd) throws Exception {
				DBRepairTool.open();
			}
		};
		map.put("menu.help.repair", action);

		action = new CommandAction() {
			public void Do(Command cmd) throws IOException {
				URL url = (URL)cmd.data;
				BrowserWindow.showWindow(url);
			}
		};
		map.put("menu.web.home",action);
		map.put("menu.web.download",action);
		map.put("menu.web.report",action);
		map.put("menu.web.feature",action);
		map.put("menu.web.support",action);
		map.put("menu.web.forum",action);
        //map.put("menu.web.donate",action);

		action = new CommandAction() {
			public void Do(Command cmd) throws IOException
			{
				//  show donwload page in browser
				String url = theConfig.getURL("book-download");
				if (url!=null)
					BrowserWindow.showWindow(url);
			}
		};
		map.put("book.list.download",action);

		action = new CommandAction() {
			public void Do(Command cmd)
					throws Exception
			{
				//  download one file
				URL url = new URL((String) cmd.data);

				final File dir = new File(theWorkingDirectory, "books");
				dir.mkdirs();
				final File file = FileUtil.uniqueFile(dir, FileUtil.getFileName(url));
				final OptionDialog dialog = (OptionDialog)openDialog("dialog.option",7);

				Runnable addbook = new Runnable() {
					public void run()
					{
						try {

							File[] files;
							if (FileUtil.hasExtension(file.getName(),"zip")) {      //  unzip
								files = FileUtil.unzip(file, dir);
								file.delete();
							}
							else
								files = new File[] { file };

							dialog.show(5);
							dialog.addBooks(files);

						} catch (IOException e) {
							Application.error(e);
						}
					}
				};

				FileDownload fd = new FileDownload(url, file, -1);
				fd.setOnSuccess(addbook);

				fd.start();
			}
		};
		map.put("book.file.download",action);

		/**
		 * check for Online-Update
		 */
		action = new CommandAction() {
		    public void Do(Command cmd)
		    {
		        OnlineUpdate.check();
		    }
		};
		map.put("menu.web.update", action);

		/**
		 * Online-Bug Reports
		 *
		action = new CommandAction() {
		    public void Do(Command cmd) throws IOException
		    {
		        OnlineReport report = new OnlineReport();
				report.setType(OnlineReport.BUG_REPORT);
				report.setSubject(Language.get("online.report.default.subject"));
				report.setDescription(Language.get("online.report.default.description"));
				report.setEmail(Language.get("online.report.default.email"));

				File log = new File(theWorkingDirectory,"error.log");
				if (log.exists() && log.length() > 20)
					report.addAttachment(log);

				report.show();
		    }
		};
		map.put("menu.web.report", action);
		*/

		/**
		 * perform Online-Update !
		 */
		action = new CommandAction() {
		    public void Do(Command cmd) throws Exception
		    {
			    //  shut down the program (without quitting the JVM)
			    Thread oldHook = shutdownHook;
			    shutdownHook = null;

			    if (!quit(cmd)) {
				    shutdownHook = oldHook;
				    return;   //  user cancelled quit - allright
			    }

			    //  let's do the update
			    File zipFile = (File)cmd.data;
			    String newVersion = (String)cmd.moreData;

			    OnlineUpdate.update(zipFile,newVersion);

			    //  and exit
			    System.exit(+2);
		    }
		};
		map.put("system.update", action);


		action = new CommandAction() {
			@Override
			public boolean isEnabled(String cmd) {
				return ! Desktop.isDesktopSupported();
			}
			public void Do(Command cmd) {
				BrowserWindow.getBrowser(BrowserWindow.ALWAYS_ASK);
			}
		};
		map.put("menu.web.browser",action);


        action = new CommandAction() {
            public boolean isSelected(String code) {
                return getHelpSystem().isShowing();
            }

            public void Do(Command cmd) throws Exception {
                getHelpSystem().init();
                getHelpSystem().show();
            }
        };
        map.put("window.help", action);

		action = new CommandAction() {
			public void Do(Command cmd) throws Exception {
				openDialog("dialog.about");
//				initSplashscreen(SplashScreen.open());
//              splashToFront();
			}
		};
		map.put("menu.help.splash", action);
		map.put("menu.help.about", action);

		action = new CommandAction() {
			public void Do(Command cmd) throws Exception {
				openDialog("dialog.about",5);
			}
		};
		map.put("menu.help.license", action);

		action = new CommandAction() {
			public void Do(Command cmd) throws Exception
			{
				if (cmd.data instanceof Number)
					openDialog("dialog.option", ((Number)cmd.data).intValue());		//	go to specific tab
				else
					openDialog("dialog.option");
			}
		};
		map.put("menu.edit.option", action);


		action = new CommandAction() {
			public void Do(Command cmd) throws Exception
			{
				String fen = null;
				if (cmd.data!=null) fen = (String)cmd.data;

				SetupDialog setup = (SetupDialog)openDialog("dialog.setup");
				if (fen!=null)
					try {
						setup.setFEN(fen.trim());
					} catch (Throwable thr) {
						//  invalid FEN
						JoDialog.showErrorDialog("dialog.setup.invalid.fen");
					}
			}
		};
		map.put("menu.game.setup", action);

		action = new CommandAction() {
			public boolean isEnabled(String cmd) {
				EnginePlugin engine = getEnginePlugin();
				return 	(theGame.getResult()==Game.RESULT_UNKNOWN) &&
				//		!theGame.askedAdjudicated &&
						(engine!=null);
			}

			public void Do(Command cmd) throws Exception
			{
				EnginePlugin xplug = getEnginePlugin();
				if (xplug!=null) {
					if (xplug.hasOfferedDraw()) {
						//	accept draw from engine
						gameDraw();
					} else if (xplug.canAcceptDraw()) {
						xplug.offerDrawToEngine();
					}
					else {
						//	else: use heuristics for adjudication
						Position pos = theGame.getPosition();
						xplug.offerDrawToEngine();
						adjudicate(theGame, pos.movedLast(), pos.gamePly(),
								theGame.getCurrentMove(), getEnginePlugin());
					}
				}
			}
		};
		map.put("menu.game.draw", action);

		action = new CommandAction() {
			public boolean isEnabled(String cmd) {
				return (theGame.getResult()==Game.RESULT_UNKNOWN);
			}

			public void Do(Command cmd)  throws Exception
			{
				int whichColor = theGame.getPosition().movesNext();
				if (getEnginePlugin()!=null) {
					if (getEnginePlugin().isThinking())
						whichColor = theGame.getPosition().movedLast();
					getEnginePlugin().pause();
				}
				theClock.halt();

				if (EngUtil.isWhite(whichColor))
					theGame.setResult(Game.BLACK_WINS);
				else
					theGame.setResult(Game.WHITE_WINS);
			}
		};
		map.put("menu.game.resign", action);

		action = new CommandAction() {
			public boolean isEnabled(String cmd) { return true; }

			public boolean isSelected(String cmd) { return boardPanel()!=null && boardPanel().is2d(); }

			public void Do(Command cmd) {
				showPanel("window.board");
				if (!boardPanel().is2d()) {
					boardPanel().set2d();
					boardPanel().getView().refresh(true);
				}
			}
		};
		map.put("menu.game.2d", action);

		action = new CommandAction() {
			public boolean isEnabled(String cmd) { return Version.hasJava3d(false,false); }

			public boolean isSelected(String cmd) {	return boardPanel()!=null && boardPanel().is3d(); }

			public void Do(Command cmd) {
				showPanel("window.board");
				if (!boardPanel().is3d()) {
					try {
						boardPanel().set3d();
					} catch (Exception ex) {
						Application.error(ex);
						boardPanel().set2d();
					}
					boardPanel().getView().refresh(true);
				}
			}
		};
		map.put("menu.game.3d", action);

		action = new CommandAction() {
			public void Do(Command cmd) throws Exception
			{
				//  command from BoardPanel
				Move mv = (Move)cmd.data;
				handleUserMove(mv,false);
			}
		};
		map.put("move.user", action);

		action = new CommandAction() {
			public boolean isEnabled(String code) {
				return ! Application.theApplication.theGame.isFirst();
			}

			public void Do(Command cmd) {
				if (theGame.first()) {
					updateClock();
	                getAnimation().pause();
					pausePlugin();
					cmd.code = "move.notify";
					cmd.moreData = null;
					broadcast(cmd);
				}
			}
		};
		map.put("move.first", action);

		action = new CommandAction() {
			public boolean isEnabled(String code) {
				return ! Application.theApplication.theGame.isFirst();
			}

			public void Do(Command cmd) {
				Move mv = theGame.backward();
				updateClock();
				if (mv != null) {
					getAnimation().pause();
					pausePlugin();
					cmd.code = "move.notify";
					cmd.moreData = null;
					broadcast(cmd);
				}
			}
		};
		map.put("move.backward", action);

        action = new CommandAction() {
            public void Do(Command cmd) {
                MoveNode mv = (MoveNode)cmd.data;
				if (mv==theGame.getCurrentMove()) return;	//	nothing to be done

				theGame.gotoMove(mv);
				theClock.halt();
				getAnimation().pause();

				if (theMode==AppMode.USER_ENGINE)
					setMode(AppMode.USER_INPUT);	//	stop auto-play
                pausePlugin();

                cmd = new Command("move.notify",null,mv.getMove());
                broadcast(cmd);
            }
        };
        map.put("move.goto", action);

		action = new CommandAction() {
			public boolean isEnabled(String code) {
				return theGame.canDelete();
			}

			public void Do(Command cmd) throws Exception {
				cutLine(theGame.getCurrentMove());
			}
		};
		map.put("move.delete", action);

		action = new CommandAction() {
			public void Do(Command cmd) throws Exception
			{
				int speed = Util.toint(cmd.data);
				boolean hints = Util.toboolean(cmd.moreData);

				theUserProfile.set("animation.speed",speed);
				theUserProfile.set("board.animation.hints",hints);

				getAnimation().setSpeed(speed);
			}
		};
		map.put("change.animation.settings",action);

		action = new CommandAction() {
			public void Do(Command cmd) throws BadLocationException {
				cutLine((Node)cmd.data);
			}
		};
		map.put("doc.menu.line.delete",action);
		map.put("doc.menu.line.cut",action);

		action = new CommandAction(CommandAction.UNDO_MANY_REDO) {
			public boolean isEnabled(String code) {
				return ! Application.theApplication.theGame.isLast();
			}
			public void Do(Command cmd) {
				Move mv = theGame.forward();
				updateClock();

				if (mv != null) {
					 pausePlugin();
				     if (boardPanel() != null) {
				         float speed = (float)(mv.distance()*0.2);
				         boardPanel().move(mv, speed);
				     }
					cmd.code = "move.notify";
					cmd.moreData = null;
					broadcast(cmd);
				 }
			}
/*			public void Undo(Command cmd) {		//	EXPERIMENTAL
				Move mv = theGame.backward();
				updateClock();
				if (mv != null) {
					 pausePlugin();
				     if (boardPanel() != null) {
				         float speed = (float)(mv.distance()*0.2);
				         boardPanel().move(mv, speed);
				     }
					cmd.code = "move.notify";
					broadcast(cmd);
				 }
			}
*/		};
		map.put("move.forward", action);

		boolean isClassic = theGame.getPosition().isClassic();
		action = new CommandAction() {
/*			public boolean isEnabled(String code) {
				return Application.theApplication.thePlugin==null ||
				       ! Application.theApplication.thePlugin.isThinking();
			}
*/
			public void Do(Command cmd) throws Exception
			{
				updateClock();
				getAnimation().pause();

				EnginePlugin engine = getEnginePlugin();
				if (engine !=null && engine.isThinking())
					engine.moveNow();
				else if (theGame.getPosition().isMate() || theGame.getPosition().isStalemate())
					Sound.play("sound.error");
				else if (! queryBookMoveForPlay(null))
					enginePlay();
			}
		};
		map.put("move.start", action);

		action = new CommandAction() {
			/**	this command has tow meanings: stop te engine and/or stop the animation	*/
			public boolean isEnabled(String code) {
				return 	getAnimation().isRunning() ||
						(getEnginePlugin()!=null) && !getEnginePlugin().isPaused();
			}

			public void Do(Command cmd) {
				getAnimation().pause();
				setMode(AppMode.USER_INPUT);
				pausePlugin(false);
			}
		};
		map.put("engine.stop", action);

		action = new CommandAction() {
			public boolean isEnabled(String code) {
				return ! Application.theApplication.theGame.isLast();
			}

			public void Do(Command cmd) throws Exception {
				openDialog("dialog.animate");
				setMode(AppMode.USER_INPUT);
				pausePlugin(false);
				getAnimation().start();
			}
		};
		map.put("move.animate", action);

		action = new CommandAction() {
			public boolean isEnabled(String code) {
				return ! Application.theApplication.theGame.isLast();
			}

			public void Do(Command cmd) {
				if (theGame.last()) {
					updateClock();
	                getAnimation().pause();
					pausePlugin();
					cmd.code = "move.notify";
					cmd.moreData = null;
					broadcast(cmd);
				}
			}
		};
		map.put("move.last", action);

		action = new CommandAction() {
			public void Do(Command cmd) throws Exception {
				openDialog("dialog.animate");
			}
		};
		map.put("menu.game.animate", action);

		action = new CommandAction() {
			public void Do(Command cmd) throws Exception
			{
				//boolean flipped = Util.toboolean(cmd.data);
				prepareNewGame();
				switchGame(theHistory.currentIndex());

				cmd.code = "move.notify";
				cmd.moreData = null;
				broadcast(cmd);
			}
		};
		map.put("menu.file.new", action);

		action = new CommandAction() {
			@Override
			public boolean isEnabled(String cmd) {
				return theGame!=null;
			}

			public void Do(Command cmd) throws Exception {
				ArrayList<Move> setupMoves = null;
				String setupFen = null;
				if (theGame!=null) {
					setupFen = theGame.getPosition().getStartFEN(XFEN);
					setupMoves = theGame.getPosition().snapshot();
				}

				prepareNewGame(setupFen,setupMoves,false);	//	todo
				switchGame(theHistory.currentIndex());
				docPanel().reformat();// to update eco etc. ?

				cmd.code = "move.notify";
				cmd.moreData = null;
				broadcast(cmd);
			}
		};
		map.put("menu.file.new.from.here", action);

		action = new CommandAction() {
			public void Do(Command cmd) throws Exception
			{
				//  show setup dialog with random FRC (?)
/*
				SetupDialog setup = (SetupDialog)openDialog("dialog.setup");
				setup.enableAllCastlings();
				setup.setFRCIndex(-1);
*/
				String randomFen = Board.initialFen(Board.FISCHER_RANDOM,-1);
				Command newCmd = new Command("new.game.setup",null,randomFen);
				theCommandDispatcher.forward(newCmd, Application.this);
			}
		};
		map.put("menu.file.new.frc", action);

		action = new CommandAction() {
			public void Do(Command cmd) throws Exception
			{
				//  show setup dialog with random FRC (?)
				String randomFen = Board.initialFen(Board.SHUFFLE_CHESS, -1);
				Command newCmd = new Command("new.game.setup",null,randomFen);
				theCommandDispatcher.forward(newCmd, Application.this);
			}
		};
		map.put("menu.file.new.shuffle", action);

		action = new CommandAction() {
			public void Do(Command cmd) throws Exception
			{
				String fen = cmd.data.toString();
				if (! theGame.isEmpty()) {
					prepareNewGame(fen,null,false);
					switchGame(theHistory.currentIndex());
				}
				else
					theGame.setup(fen);

				if (!isClassic)
					theGame.setTagValue(PgnConstants.TAG_VARIANT,"Fischer Random Chess");

				theGame.reformat();
				cmd.code = "move.notify";
				cmd.moreData = Boolean.TRUE;
				broadcast(cmd);
			}
		};
		map.put("new.game.setup", action);

		action = new CommandAction() {
			public void Do(Command cmd)
			{
				openFile();
			}
		};
		map.put("menu.file.open", action);

        action = new CommandAction() {
            public void Do(Command cmd) throws Exception
            {
                openURL();
            }
        };
        map.put("menu.file.open.url", action);

		action = new CommandAction() {
			public void Do(Command cmd) throws Exception
			{
				showPanel("window.collectionlist");
				showPanel("window.gamelist");

				StringTokenizer tokens = new StringTokenizer((String)cmd.data,File.pathSeparator);
				while (tokens.hasMoreTokens())
				try {

					String tk = tokens.nextToken();
					if (FileUtil.exists(tk)) {
						if (FileUtil.hasExtension(tk,"jos") || FileUtil.hasExtension(tk,"jose"))
							new ArchiveImport(new File(tk)).start();
						else
							PGNImport.openFile(new File(tk));
					}
					else if (Util.isValidURL(tk)) {
						if (FileUtil.hasExtension(tk,"jos") || FileUtil.hasExtension(tk,"jose"))
							new ArchiveImport(new URL(tk)).start();
						else
							PGNImport.openURL(new URL(tk));
					}
					else
						JoDialog.showErrorDialog(null,"download.error.invalid.url","p",tk);

				} catch (FileNotFoundException ex) {
					JoDialog.showErrorDialog("File not found: "+ex.getLocalizedMessage());
				} catch (Exception ex) {
					Application.error(ex);
					JoDialog.showErrorDialog(ex.getLocalizedMessage());
				}
			}
		};
		map.put("menu.file.open.all",action);


		action = new CommandAction() {
			public void Do(Command cmd) throws Exception {
				PrintableDocument prdoc = null;

				if (cmd.data instanceof PrintableDocument)
					prdoc = (PrintableDocument)cmd.data;	//	from PrintPreviewDialog. this is a StyledDocument (don't use it)
				else if (cmd.data instanceof ExportContext) {
					/*
						avoid use of XSLFOExport.Preview
						it's based on fop AWTRenderer and has generally poor word spacing.

						Use PDF renderer instead. Print to temporary file.
					 */
					ExportContext context = (ExportContext)cmd.data;
					if (context.preview!=null)
						prdoc = context.preview;   // called from preview; print this document
					else {
						ExportDialog dlg = (ExportDialog)getDialog("dialog.export");
						if (dlg.confirmPrint(context.source,25)) {
							if (context.getOutput()==ExportConfig.OUTPUT_XSL_FO)
							{
								context.target = File.createTempFile("jose",".pdf");
								Version.loadFop();
								XSLFOExport fotask = new XSLFOExport(context);
								fotask.printOnCompletion = new Runnable() {
									@Override
									public void run() {
                                        try {
											Desktop desktop = Desktop.getDesktop();
                                            desktop.open((File)context.target);
                                        } catch (IOException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
								};
								fotask.start();
								prdoc=null;
							}
							else {
								prdoc = context.createPrintableDocument();   // create awt document then print
							}
                            dlg.hide();
                        }
					}
				}
				else
					throw new IllegalArgumentException();

				if (prdoc!=null)
					prdoc.print();
			}
		};
		map.put("export.print",action);

		action = new CommandAction() {
			public void Do(Command cmd) throws Exception {
				ExportContext context = (ExportContext)cmd.data;

				ExportDialog dlg = (ExportDialog)getDialog("dialog.export");
				if (!dlg.confirmExport(context.source)) return;

                dlg.hide();

				switch (context.getOutput())
				{
				case ExportConfig.OUTPUT_HTML:
					HtmlUtil.createCollateral(context, context.profile.getBoolean("xsl.html.complete"));
					HtmlUtil.exportFile(context,true);
					break;

				case ExportConfig.OUTPUT_XML:
				case ExportConfig.OUTPUT_TEX:
				case ExportConfig.OUTPUT_TEXT:
					HtmlUtil.exportFile(context,true);
					break;
				case ExportConfig.OUTPUT_XSL_FO:
					//  setup XSL-FO exporter with appropriate style sheet
					Version.loadFop();
					XSLFOExport fotask = new XSLFOExport(context);
					fotask.start();   //  don't wait for task to complete
					break;
                case ExportConfig.OUTPUT_PGN:
					String charSet = context.profile.getString("pgn.export.charset","iso-8859-1");
	                PGNExport pgntask = new PGNExport(context.target,charSet);
	                pgntask.setSource(context.source);
	                pgntask.start();
	                break;
				case ExportConfig.OUTPUT_ARCH:
					ArchiveExport arctask = new ArchiveExport((File)context.target);
					arctask.setSource(context.source);
					arctask.start();
					break;
				default:
					throw new IllegalArgumentException();   //  TODO
				}
			}
		};
		map.put("export.disk",action);

		action = new CommandAction() {
            public boolean isEnabled(String code) {
                return /*theGame.isNew() ||*/ theGame.isDirty();
            }
			public void Do(Command cmd) throws Exception {
				saveGame(theGame,cmd.code.equals("menu.file.save.copy"));
			}
		};
		map.put("menu.file.save", action);
		map.put("menu.file.save.copy", action);

		action = new CommandAction() {
			public boolean isEnabled(String code) {
				return /*theGame.isNew() ||*/ theGame.isDirty();
			}
			public void Do(Command cmd) throws Exception {
				saveGame(theGame,true);
			}
		};

		action = new CommandAction() {
			public boolean isEnabled(String code) {
				return hasGameSource(null,false);
			}

			public void Do(Command cmd) throws Exception {
				//  export selected files

				ExportDialog dlg = (ExportDialog)getDialog("dialog.export");
				dlg.forExport(getGameSource(cmd,false,false));
				openDialog(dlg,0);
			}
		};
		map.put("menu.file.save.as", action);

        action = new CommandAction()  {
            public boolean isEnabled(String code) {
                return theHistory.isDirty();
            }
            public void Do(Command cmd) throws Exception {
                theHistory.saveAll();
            }
        };
        map.put("menu.file.save.all", action);

		action = new CommandAction()  {
			//  CommandAction.NEW_THREAD if long-running
			public boolean isEnabled(String code) {
				return hasGameSource(null,false);
			}
			public void Do(Command cmd) throws Exception
			{
				ExportDialog dlg = (ExportDialog)getDialog("dialog.export");
				dlg.forPrint(getGameSource(cmd,false,false));
				openDialog(dlg,0);
			}
		};
		map.put("menu.file.print", action);

		action = new CommandAction()  {
			public boolean isEnabled(String code) {
				return hasGameSource(null,false);
			}
			public void Do(Command cmd) throws Exception
			{
				PrintPreviewDialog prvdlg;

				if (cmd.data!=null && cmd.data instanceof ExportContext) {
					//  called rom ExportDialog
					ExportContext context = (ExportContext)cmd.data;
					boolean preferInternal = Util.toboolean(cmd.moreData);

					ExportDialog dlg = (ExportDialog)getDialog("dialog.export");
					if (!dlg.confirmPreview(context.source,25)) return;

                    dlg.hide();

					boolean internal = ExportConfig.canPreview(context.config);
					boolean external = ExportConfig.canBrowserPreview(context.config);

					if (internal && external) {
						if (preferInternal)
							external = false;
						else
							internal = false;
					}

					if (internal) {
						//  internal preview
						prvdlg = (PrintPreviewDialog)createPanel("window.print.preview");
						prvdlg.setContext(context);
						showPanel(prvdlg);
						prvdlg.reset();
					}
					else if (external) {
						//  preview in Web Browser
						HtmlUtil.exportTemporary(context,false);
						URI uri = ((File)context.target).toURI();
						BrowserWindow.showWindow(uri);
					}
					else
						throw new IllegalArgumentException();
				}
				else {
					//  called from menu
					ExportDialog dlg = (ExportDialog)getDialog("dialog.export");
					dlg.forPrint(getGameSource(cmd,false,false));
					openDialog(dlg,0);
				}
			}
		};
		map.put("menu.file.print.preview", action);
		map.put("window.print.preview", action);

		action = new CommandAction()  {
			public boolean isEnabled(String code) {
				return true;
			}
			public void Do(Command cmd) throws Exception
			{
				ExportDialog dlg = (ExportDialog)getDialog("dialog.export");
				dlg.forPrint(getGameSource(cmd,false,false));
				openDialog(dlg,1);
			}
		};
		map.put("menu.file.print.setup", action);

		action = new CommandAction() {
            public void Do(Command cmd) throws Exception {
				GameDetailsDialog dialog = (GameDetailsDialog)getDialog("dialog.game");
				JoPanel parentPanel = null;

				if (cmd.data==null && theGame!=null) {
					//	edit current game
					dialog.setGame(theGame);
				}
				else if (cmd.data instanceof GameSource) {
					//	edit database game
					int GId = ((GameSource)cmd.data).firstId();
					Game gm = theHistory.getById(GId);
					if (gm!=null) {
						//	game is open: switch
						if (gm!=theGame) switchGame(theHistory.indexOf(gm));
						dialog.setGame(gm);
					}
					else {
						//	game is not open: just edit
						dialog.setGameId(GId);
						parentPanel = listPanel();
					}
				}
				else
					throw new IllegalArgumentException(String.valueOf(cmd.data));

				if (parentPanel == null)
					parentPanel = docPanel();
				if (parentPanel!=null && parentPanel.isShowing())
					dialog.stagger(parentPanel, 10,10);

                openDialog(dialog,1);
            }
        };
        map.put("menu.game.details", action);

		action = new CommandAction() {
			public void Do(Command cmd)
			{
				boolean engine_analysis = false;
				EnginePanel eng_panel = enginePanel();
				eng_panel.setVisible(true);

				if (eng_panel.inBook) {
					//	switch from Book to Engine analysis
					startEngineAnalysis();//true);
				}
				else {
					updateBook(false,true);
					//	todo call asynch, switch to engine analysis on completion
				}
			}
		};
		map.put("menu.game.analysis",action);

        action = new CommandAction() {
            public boolean isEnabled(String code) {
                return theHistory.hasPrevious();
            }

            public void Do(Command cmd) {
                if (theHistory.hasPrevious())
                    switchGame(theHistory.currentIndex()-1);
            }
        };
        map.put("menu.game.previous", action);

        action = new CommandAction() {
            public boolean isEnabled(String code) {
                return theHistory.hasNext();
            }

            public void Do(Command cmd) {
                if (theHistory.hasNext())
					switchGame(theHistory.currentIndex()+1);
            }
        };
        map.put("menu.game.next", action);

		action = new CommandAction() {
			public boolean isEnabled(String code) {
				return theHistory.size() > 0;
			}

			public void Do(Command cmd) throws Exception
			{
				Game g;
				if (cmd.data != null && (cmd.data instanceof Integer)) {
					int gidx = ((Integer)cmd.data).intValue();
					g = theHistory.get(gidx);
				}
				else
					g = theGame;

				if (theGame.isDirty())
					switch (confirmSaveOne()) {
					case JOptionPane.YES_OPTION:	saveGame(g,false); break;
					case JOptionPane.NO_OPTION:		g.clearDirty();
                                                    //  important to adjust dirty indicators
                                                    break;
					case JOptionPane.CANCEL_OPTION:	return;
					}

				theHistory.remove(g);
				if (theHistory.size()== 0)
					prepareNewGame();
				switchGame(theHistory.currentIndex());
			}
		};
		map.put("menu.game.close", action);

		action = new CommandAction() {
			public boolean isEnabled(String code) {
				return theHistory.size() > 1;
			}

			public void Do(Command cmd) throws Exception {
				if (theHistory.isDirty())
					switch (confirmSaveAll()) {
					case JOptionPane.YES_OPTION:	theHistory.saveAll(); break;
					case JOptionPane.NO_OPTION:		theHistory.clearDirty(); break;
					case JOptionPane.CANCEL_OPTION:	return;
					}

				theHistory.removeAll();
//				theGame = new Game(theUserProfile.getStyleContext(),
//								   null,null, PgnUtil.currentDate(), null, null);
				prepareNewGame();
				switchGame(0);
			}
		};
		map.put("menu.game.close.all", action);

		action = new CommandAction() {
			public boolean isEnabled(String code) {
				return theHistory.size() > 1;
			}

			public void Do(Command cmd) throws Exception {
				int gidx;
				if (cmd.data != null && (cmd.data instanceof Integer))
					gidx = ((Integer)cmd.data).intValue();
				else
					gidx = theHistory.currentIndex();

				if (theHistory.isDirtyBut(gidx))
					switch (confirmSaveAll()) {
					case JOptionPane.YES_OPTION:	theHistory.saveAllBut(gidx); break;
					case JOptionPane.NO_OPTION:		break;
					case JOptionPane.CANCEL_OPTION:	return;
					}

				theHistory.removeAllBut(gidx);
				theGame = theHistory.get(0);
				switchGame(0);
			}
		};
		map.put("menu.game.close.all.but", action);

		action = new CommandAction() {
			public void Do(Command cmd) throws IOException {
				restartPlugin();
			}
		};
		map.put("restart.plugin",action);
/*
		action = new CommandAction() {
			public boolean isEnabled(String code) {
				return getEnginePlugin() != null;
			}
			public void Do(Command cmd)
					throws IOException
			{
				Move mv = null;
				if (cmd.data!=null && cmd.data instanceof Move)
					mv = (Move)cmd.data;
				else if (enginePanel()!=null)
					mv = enginePanel().getHintMove();

				if (mv!=null) {
					enginePanel().handleMessage(getEnginePlugin(), Plugin.PLUGIN_REQUESTED_HINT, mv);
					Application.this.handleMessage(getEnginePlugin(), Plugin.PLUGIN_REQUESTED_HINT, mv);
					return;
				}

				/**
				 * otherwise: request hint from Book or Engine
				 * /
				//BookEntry hint = theOpeningLibrary.selectMove(pos, theMode,true, pos.whiteMovesNext());
				submitBookQuery(BOOK_HINT,null);
			}
		};
		map.put("menu.game.hint",action);
*/
		action = new CommandAction()
		{
			public boolean isSelected(String code) {
				return AbstractApplication.theUserProfile.getBoolean("board.flip");
			}
			public void Do(Command cmd) {
				boolean flipped = theUserProfile.getBoolean("board.flip");
				flipped = !flipped;
				theUserProfile.set("board.flip", flipped);
				broadcast(new Command("broadcast.board.flip", cmd.event, Util.toBoolean(flipped)));
			}
		};
		map.put("menu.game.flip", action);

		action = new CommandAction() {
			public void Do(Command cmd) {
				int idx = ((Integer)cmd.data).intValue();
				if (idx != theUserProfile.getTimeControlIdx())
				{
					theUserProfile.setTimeControlIdx(idx);
					TimeControl control = theUserProfile.getTimeControl();
					control.resetTime(theClock);
					if (getEnginePlugin()!=null)
						getEnginePlugin().setTimeControls(control.getPhase(0));
				}
			}
		};
		map.put("menu.game.time.control", action);

		action = new CommandAction() {
			public boolean isSelected(String code) {
				return AbstractApplication.theUserProfile.getBoolean("board.coords");
			}
			public void Do(Command cmd) {
				boolean showCoords = theUserProfile.getBoolean("board.coords");
				showCoords = !showCoords;
				theUserProfile.set("board.coords", showCoords);
				broadcast(new Command("broadcast.board.coords", Util.toBoolean(showCoords)));
			}
		};
		map.put("menu.game.coords", action);

		action = new CommandAction() {
			public boolean isSelected(String code) {
				return AbstractApplication.theUserProfile.getBoolean("board.evalbar");
			}
			public void Do(Command cmd) {
				boolean showEvalbar = theUserProfile.getBoolean("board.evalbar");
				showEvalbar = !showEvalbar;
				theUserProfile.set("board.evalbar", showEvalbar);
				broadcast(new Command("broadcast.board.evalbar", Util.toBoolean(showEvalbar)));
			}
		};
		map.put("menu.game.evalbar", action);

		action = new CommandAction() {
			public boolean isSelected(String code) {
				return AbstractApplication.theUserProfile.getBoolean("board.suggestions");
			}
			public void Do(Command cmd) {
				boolean showSuggestions = theUserProfile.getBoolean("board.suggestions");
				showSuggestions = !showSuggestions;
				theUserProfile.set("board.suggestions", showSuggestions);
				broadcast(new Command("broadcast.board.suggestions", Util.toBoolean(showSuggestions)));
			}
		};
		map.put("menu.game.suggestions", action);

		action = new CommandAction() {
			public CommandListener forward(CommandListener current)
			{
				return JoFrame.getActiveFrame();
			}
		};
		map.put("menu.window.fullscreen", action);

		action = new CommandAction() {
			public void Do(Command cmd) {
				/** Restore Factory Layout  */
				//  close all open frames
				JoFrame.closeAll();
				theUserProfile.createFactoryFrameLayout(null);
				JoPanel.updateAllPanels(theUserProfile);
				openFrames(theUserProfile.getFrameProfiles());
			}
		};
		map.put("menu.window.reset",action);

		action = new CommandAction() {
			public boolean isSelected(String code) {
				return JoPanel.isShowing(code);
			}

			public void Do(Command cmd) {
				showPanel(cmd.code);
			}
		};
		for(String window : JoPanel.panelNames())
			map.put(window,action);

		action = new CommandAction() {
			public void Do(Command cmd) {
				JoPanel panel = (JoPanel)cmd.data;
				broadcast(cmd);
                try {
                    panel.doInit();
                } catch (Exception e) {
                    error(e);
                }
            }
		};
		map.put("panel.init", action);

		action = new CommandAction() {
			public void Do(Command cmd)	throws Exception
			{
				Thread oldHook = shutdownHook;
				shutdownHook = null;

				if (!quit(cmd)) {
					shutdownHook = oldHook;
					return; //  user cancelled - allright
				}

				System.exit(+1);
			}
		};
		map.put("menu.file.quit", action);

		action = new CommandAction() {
			public void Do(Command cmd) throws Exception {
				GameSource src = getGameSource(cmd,false,false);
				PositionFilter posFilter = null;
				if (listPanel()!=null)
					posFilter = listPanel().getSearchRecord().makePositionFilter();

				boolean showPanel = (cmd.moreData==null) || ((Boolean)cmd.moreData).booleanValue();
				int oldTabIndex = theHistory.currentIndex();

				boolean posChanged;
                if (cmd.code.equals("edit.game")) {
				    int GId = src.firstId();
                    posChanged = prepareEditGame(GId,posFilter);
                }
                else if (cmd.code.equals("menu.edit.paste.pgn")) {
	                String pgnText = ClipboardUtil.getPlainText(this);
	                if (pgnText!=null) pgnText = pgnText.trim();
	                if (pgnText==null || pgnText.length()==0) {
		                AWTUtil.beep(docPanel());
	                    return;
	                }
	                posChanged = prepareEditGame(pgnText,null);
                }
                else {
                    posChanged = prepareEditGames(src,posFilter);
                }

				if (showPanel) showPanel("window.game");

				switchGame(theHistory.currentIndex());

				cmd.code = "broadcast.edit.game";
				broadcast(cmd);

				if (posChanged) {
					//	new game: goto last move
//                    cmd = new Command("move.last", null, null);
					switch (theMode) {
						case USER_ENGINE:
						case ENGINE_ENGINE:
							setMode(AppMode.USER_INPUT);
							break;
					}
					pausePlugin();
//                    broadcast(cmd);
				}

				if (!showPanel && oldTabIndex>=0 && oldTabIndex!=theHistory.currentIndex()) {
					//	back to original panel
					//	(Lichess download: open downloaded game, then switch back.
					//   not elegant, but necessary?)
					switchGame(oldTabIndex);
				}
			}
		};
		map.put("edit.game", action);
        map.put("edit.all", action);
		map.put("menu.edit.paste.pgn", action);


		action = new CommandAction() {
			public void Do(Command cmd) throws Exception {
				GameRepair repair = new GameRepair();
				if (! repair.checkOnStart())
					repair.start();
			}
		};
		map.put("db.check",action);

		action = new CommandAction() {
            public boolean isEnabled(String code) {
                return !theGame.isNew() && theGame.isDirty();
            }
			public void Do(Command cmd) throws Exception
			{
				int GId = theGame.getId();
				theGame.reread(GId);

				pausePlugin();
				//  forward to ourself
				theCommandDispatcher.forward(new Command("move.last"),Application.this);
			}
		};
		map.put("menu.file.revert", action);

        action = new CommandAction() {
            public void Do(Command cmd) throws Exception
            {
                //  saerch current position in database
                showPanel("window.query");
                queryPanel().searchCurrentPosition();
            }
        };
        map.put("menu.edit.search.current",action);


        action = new CommandAction() {
            public void Do(Command cmd) throws Exception
            {
                //  dump current frame layout
                File dumpFile = new File(theWorkingDirectory,"config/layout-dump.xml");
                PrintWriter pout = new PrintWriter(new FileWriter(dumpFile));
	            /** PrintWriter(File) since 1.5 !! */
                FrameProfile.serializeXml(theUserProfile.frameLayout, pout);
                pout.close();
            }
        };
        map.put("debug.layout.dump",action);

		action = new CommandAction() {
			@Override
			public void Do(Command cmd) throws Exception {
				if (cmd.code.equals("eboard.connect"))
					initEBoardConnector().connect();
				if (cmd.code.equals("eboard.disconnect"))
					initEBoardConnector().disconnect();
			}
			@Override
			public boolean isEnabled(String cmd) {
				return eboard!=null && eboard.isAvailable();
			}
			@Override
			public boolean isSelected(String cmd) {
				return initEBoardConnector().connected;
			}
		};
		map.put("eboard.disconnect",action);
		map.put("eboard.connect",action);

		action = new CommandAction(INVOKE_LATER) {
			public void Do(Command cmd) throws Exception {
				JOptionPane opane = new JOptionPane(Language.get(cmd.code), JOptionPane.ERROR_MESSAGE);
				JDialog dlg = opane.createDialog(JoFrame.theActiveFrame, Language.get("dialog.error.title"));
				SplashScreen.close();
				dlg.setVisible(true);
			}
		};
		map.put("error.duplicate.database.access",action);
	}

	public void updateBook(boolean onEngineMove, boolean switchAnalysis)
	{
		boolean inBook;
		if (onEngineMove && Application.theApplication.theOpeningLibrary.engineMode==OpeningLibrary.NO_BOOK)
		{	//	don't update book after an engine move, if this is not desired
			enginePanel().exitBook();
		}
		else
		{
			//  show opening book moves
			Position pos = Application.theApplication.theGame.getPosition();
			// onEngineMove implies NOT analysis mode, right?
			if (pos.isGameFinished(true)) {
				enginePanel().exitBook();
				pausePlugin(false);
			}
			else {
				if (onEngineMove) switchAnalysis = false;
				submitBookQuery(switchAnalysis ? BOOK_ANALYSIS : BOOK_SHOW, null);
				//	will call back with message BOOK_RESPONSE, onBookUpdate
			}
		}
	}

	private void handleBookMessage(BookQuery query, Position pos) throws Exception {
		if (!query.isValid())
			return;

		EnginePanel eng = enginePanel();
		BookEntry entry;

		switch(query.onCompletion)
		{
			case BOOK_SHOW:
			case BOOK_ANALYSIS:
				if (!query.result.isEmpty()) {
					pausePlugin(false);
					eng.showBook(query.result, pos);
				} else {
					eng.exitBook();
					//	when out of book, switch to Engine analysis
					if (query.onCompletion == BOOK_ANALYSIS)
						startEngineAnalysis();//true);
				}
				break;

			case BOOK_PLAY:
				entry = theOpeningLibrary.selectMove(query.result,
						SELECT_GAME_COUNT, pos.whiteMovesNext(),
						theOpeningLibrary.random);
				if (entry != null)
					playBookMove(entry);
				else if (query.lastMove!=null)
					enginePlay(query.lastMove);
				else
					enginePlay();
				break;
/*
			case BOOK_HINT:
				entry = theOpeningLibrary.selectMove(query.result,
						SELECT_GAME_COUNT, pos.whiteMovesNext(),null);
				if (entry!=null) {
					//  (1) Hint from Opening Library
					Application.this.handleMessage(theOpeningLibrary, Plugin.PLUGIN_REQUESTED_HINT, entry.move);
					if (enginePanel()!=null)
						enginePanel().handleMessage(theOpeningLibrary, Plugin.PLUGIN_REQUESTED_HINT, entry.move);
				}
				else if (getEnginePlugin()!=null)
				{
					//  (2) Hint from Plugin
					getEnginePlugin().getHint();
					//	plugin will eventually respond with a Hint message
				}
				break;
 */
		}
	}

	private void startEngineAnalysis()
	{
//		if (engine_analysis) {
			invokeWithPlugin(new Runnable() {
				public void run() {	//  (2) enter engine analysis mode
					//pausePlugin(true);
					Position pos = theGame.getPosition();
					if (engine.canAnalyze(pos)) {
						engine.analyze(pos);    //	right?
						setMode(AppMode.ANALYSIS);
					}
				}
			});
//		}
//		else {
//			//  (2) leave engine analysis mode
//			pausePlugin(false);
//			theMode = AppMode.USER_INPUT;
//		}
	}

	public void handleUserMove(Move move, boolean animate)
	        throws BadLocationException, ParseException
	{
		final Move mv = new Move(move,theGame.getPosition());

		if (theUserProfile.getBoolean("sound.moves.ack.user"))
			speakAcknowledge();
		if (theUserProfile.getBoolean("sound.moves.user")) {
			int format = theUserProfile.getInt("doc.move.format",MoveFormatter.SHORT);
			speakMove(format,mv,theGame.getPosition());
		}

		if (theGame.insertMove(-1, mv, 0) == Game.INSERT_USER_ABORT) {
			//  user aborted insert of new line
			//  update board panel
			if (boardPanel()!=null) boardPanel().getView().refresh(true);
			return;
		}

		updateClock();

		if (animate && boardPanel() != null)
			boardPanel().move(mv, (float)(mv.distance()*0.2));

		//	get an evaluation from current AnalysisRecord
		//	and attach it to MoveNode
		if (enginePanel()!=null)
		{
			MoveNode mvnd = theGame.getCurrentMove();
			mvnd.engineValue = enginePanel().findScore(mv);
			//	otherwise: wait for the engine to respond, copy their value
		}

		Command cmd = new Command("move.notify",null,mv);
		broadcast(cmd);

		if (mv.isGameFinished(false)) {
			gameFinished(mv.flags, theGame.getPosition().movedLast(), theGame.isMainLine());
			theClock.halt();
		}
		else {
			switch (theMode) {
			case ENGINE_ENGINE:
					//	TODO not yet implemented
					break;

			case USER_ENGINE:
				// PLAY FROM BOOK
				//	migth call back to onBookUpdate, later
				if (! queryBookMoveForPlay(mv))
					enginePlay(mv);
				break;

			case ANALYSIS:
				engineAnalyze(mv);
				break;
			}
		}

		classifyOpening();
	}

	private void engineAnalyze(Move userMove)
	{
		boolean isFrcCastling = userMove.isFRCCastling();
		boolean isClassic = theGame.getPosition().isClassic();

		invokeWithPlugin(new Runnable() {
			public void run() {
				EnginePlugin engine = getEnginePlugin();
				boolean supportsFRC = engine.supportsFRC();
				if (isFrcCastling && !supportsFRC) {
					showFRCWarning(true);
					pausePlugin(false);
				}
				else {
					if (!isClassic && !supportsFRC)
						showFRCWarning(false);
						//  but keep on playing (you have been warned ;-)
					engine.analyze(theGame.getPosition(), userMove);
				}
			}
		});
	}

	private void enginePlay()
	{
		boolean isClassic = theGame.getPosition().isClassic();

		//enginePanel().exitBook(); ?
		invokeWithPlugin(new Runnable() {
			public void run() {
				EnginePlugin engine = getEnginePlugin();
				boolean supportsFRC = engine.supportsFRC();
				if (!isClassic && !supportsFRC)
					showFRCWarning(false);
				//  but keep on playing (you have been warned ;-)
				if (engine.isThinking())
					engine.moveNow();
				else {
					//	thePlugin.setTime(clockPanel().getWhite/BlackTime());
					//	adjust time ? or rely on engine's time keeping ?
					setGameDefaultInfo();
					engine.go();
				}
			}
		});
		setPlayState(PlayState.ENGINE);
	}

	private void enginePlay(Move userMove)
	{
		boolean isFrcCastling = userMove.isFRCCastling();
		boolean isClassic = theGame.getPosition().isClassic();

		enginePanel().exitBook();
		invokeWithPlugin(new Runnable() {
			public void run() {
				EnginePlugin engine = getEnginePlugin();
				boolean supportsFRC = engine.supportsFRC();
				if (isFrcCastling && !supportsFRC) {
					showFRCWarning(true);
					pausePlugin(false);
				} else {
					if (!isClassic && !supportsFRC)
						showFRCWarning(false);
					//  but keep on playing (you have been warned ;-)
					// LET ENGINE PLAY
					engine.userMove(userMove, true);
				}
			};
		});
		setPlayState(PlayState.ENGINE);
	}

	private void cutLine(Node node) throws BadLocationException
	{
		//	delete/cut line
		boolean cutCurrent = theGame.cutBeforeCurrent(node);
		MoveNode closeMove = theGame.cutBefore(node);

		theClock.halt();
		getAnimation().pause();
		pausePlugin();

		if (cutCurrent) {
			Command cmd = new Command("move.notify",null,closeMove,Boolean.TRUE);
			broadcast(cmd);
		}
	}

	private boolean classifyOpening()
	{
		if (theGame.isMainLine() &&
			theGame.isLast() &&
			theUserProfile.getBoolean("doc.classify.eco"))
		{
			theGame.getPosition().updateHashKeys();
			ECOClassificator classificator = getClassificator();
			int result = classificator.lookup(theGame.getPosition());
			if (result != ECOClassificator.NOT_FOUND) {
				theGame.setTagValue(PgnConstants.TAG_ECO, classificator.getEcoCode(result,3));
				theGame.setTagValue(PgnConstants.TAG_OPENING, classificator.getOpeningName(result));
				if (docPanel()!=null)
					docPanel().reformat();
				return true;
			}
		}
		return false;
	}

	public JoPanel createPanel(String name)
	{
		JoPanel panel = JoPanel.get(name);
		if (panel==null) {
			LayoutProfile profile = Application.theUserProfile.getPanelProfile(name);
			profile.hide = false;
			panel = JoPanel.create(profile,true);
		}
		return panel;
	}

	/**
	 * show a panel
	 * @param name
	 */
	public JoPanel showPanel(String name)
	{
		JoPanel panel = createPanel(name);
		showPanel(panel);
		return panel;
	}

    /**
     * show a panel
     */
	public JoPanel showPanel(JoPanel panel)
	{
		JoFrame frame = panel.getParentFrame();
		if (frame==null) {
			frame = JoFrame.getFrame(panel.getProfile().frameProfile);
			if (frame==null)
				frame = new JoFrame(panel.getProfile().frameProfile);
			JoFrame.dock(panel, frame, panel.getProfile().dockingPath);
		}

		showFrame(frame);
		return panel;
	}

    /**
     * show the frame, a panel belongs to
     * (son't show the panel itself)
     * @param name
     */
    public JoFrame showPanelFrame(String name)
    {
        JoPanel panel = createPanel(name);
        return showPanelFrame(panel);
    }

	public JoFrame hidePanelFrame(String name)
	{
		JoPanel panel = JoPanel.get(name);
		if (panel==null) return null;

		JoFrame frame = panel.getParentFrame();
		if (frame==null) return null;

		frame.setVisible(false);
		return frame;
	}

    /**
     * show the frame, a panel belongs to
     * (don't show the panel itself)
     */
    public JoFrame showPanelFrame(JoPanel panel)
    {
        JoFrame frame = panel.getParentFrame();
        if (frame==null) {
            frame = JoFrame.getFrame(panel.getProfile().frameProfile);
            if (frame==null) {
	            frame = new JoFrame(panel.getProfile().frameProfile);
                //  DON'T JoFrame.dock
            }
        }
        showFrame(frame);
        return frame;
    }


	protected void showFrame(JoFrame frame)
	{
		frame.setComponentsVisible(true);
		frame.setVisible(true);
		frame.toFront();
	}

	public void switchGame(int tabIndex)
	{
		theGame = theHistory.get(tabIndex);
        theHistory.setCurrent(tabIndex);
		theGame.resetPosition();
        theGame.gotoMove(theGame.getCurrentMove(),true);
		//  TODO each of the above calls Board.setupFEN(); three times in a row.
		//  potential for optimisation...
		pausePlugin();

		Command cmd = new Command("switch.game",null, theGame, tabIndex);
		broadcast(cmd);
	}

	protected void saveGame(Game g, boolean copy)	throws Exception
	{
		if (g != null)
			if (g.isNew() || copy) {
				if (!g.isEmpty() && (g.getTagValue(PgnConstants.TAG_DATE)==null))
				{
					//  when saving a new, non-empty game, fill in default date
					g.setTagValue(PgnConstants.TAG_DATE, PgnUtil.currentDate(), g);
				}
				int CId = Collection.makeAutoSave(null);
				g.saveAs(CId,0);
				//	adjust database panel
				broadcast(new Command("collection.modified",null,CId));
			}
			else {
				g.save();
				broadcast(new Command("game.modified",null,g.getId()));
				//	adjust database panel
			}
	}

    protected boolean prepareEditGame(int GId, PositionFilter posFilter) throws Exception
    {
		broadcast("prepare.game");

		int index = theHistory.indexOf(GId);
        if (index >= 0) {
			theGame = theHistory.get(index);
            theHistory.setCurrent(index);
			return theGame.gotoMove(posFilter);
		}
		else if (!Game.exists(GId))
			return false;
        else {
            prepareNewGame();
			try {
				theGame.read(GId);
			} catch (IllegalArgumentException ise) {
				//	not found in database !
				//	(may happen if Ids are stored in profile
				error(ise);
			}
			theGame.clearDirty();

			if (!theGame.gotoMove(posFilter)) {
				if (theGame.getTagValue(Game.TAG_FEN)!=null)
					theGame.first();
				else
					theGame.last();
			}
            return true;    //  = new editor opened
		}
    }

	protected boolean prepareEditGame(String pgnText, PositionFilter posFilter) throws Exception
	{
		broadcast("prepare.game");

		prepareNewGame();

		//  note that this need not be a valid PGN text
		try {
			GameUtil.pastePGN(theGame,pgnText);
		} catch (Throwable e) {
			//  parse error in PGN ? - don't mind
			CommentNode errorComment = new CommentNode("Error ("+e.getMessage()+") in PGN text:\n\n");
			//  TODO style
			errorComment.insertAfter(theGame.getMainLine().first());

			CommentNode pgnComment = new CommentNode(pgnText);
			pgnComment.insertAfter(errorComment);

			AWTUtil.beep(docPanel());  //  "beep"
		}

		//  goto last move
		if (theGame.getTagValue(Game.TAG_FEN)!=null)
			theGame.first();
		else
			theGame.last();
		return true;
	}

    protected boolean prepareEditGames(GameSource src, PositionFilter posFilter) throws Exception
    {
        int count = 0;
		//	don't open more than 24 tabs

        if (src.isSingleGame()) {
            if (prepareEditGame(src.firstId(),posFilter)) count++;
		}
        else if (src.isGameArray()) {
            int[] ids = src.getIds();
            for (int i=0; i < ids.length; i++)
                if (prepareEditGame(ids[i],posFilter))
                    if (++count >= 24) break;
        }
        else if (src.isGameSelection()) {
            int idx1 = src.getSelection().getMinSelectionIndex();
            int idx2 = src.getSelection().getMaxSelectionIndex();
            for ( ; idx1 <= idx2; idx1++)
                if (src.getSelection().isSelectedIndex(idx1)) {
                    int id = src.getSelection().getDBId(idx1);
                    if (prepareEditGame(id,posFilter))
                        if (++count >= 24) break;
                }
        }
        else
            throw new IllegalArgumentException("GameSource type not supported here");

        return (count > 0);
    }

	protected void prepareNewGame() throws Exception
	{
		prepareNewGame(null,null,false);
	}

    protected void prepareNewGame(String setupFen, ArrayList<Move> setupMoves, boolean flipped) throws Exception
    {
	    boolean createNew=true;
		/*	save new game into autosave ?	*/
		if (theGame != null && theGame.isNew()) {
			if (theGame.isDirty())
				/*saveGame(theGame)*/ ;
			else
				createNew = false;  //	reuse current game
		}

	    if (createNew)
	    {
		    //  create new Game object
			String white = null;
			String black = null;
			Position pos = null;

			if (theGame != null) {
			/* ??
				white = (String)theGame.getTagValue(flipped ? PgnConstants.TAG_BLACK:PgnConstants.TAG_WHITE);
				black = (String)theGame.getTagValue(flipped ? PgnConstants.TAG_WHITE:PgnConstants.TAG_BLACK);
			 */
				pos = theGame.getPosition();
			}

			theGame = new Game(theUserProfile.getStyleContext(),
							   white,black, null/*PgnUtil.currentDate()*/, setupFen,
							   pos);

			theHistory.add(theGame);
	    }
	    else
	    {
		    //  recycle existing
		    theGame.clear(setupFen);
		    if (!theHistory.contains(theGame))
			    theHistory.add(theGame);
	    }

		if (setupMoves!=null) {
			//	replay initial moves
			theGame.fireEvents=false; // why, exactly?
			theGame.setDirty();
			for(Move mv : setupMoves)
				theGame.insertMove(-1,mv,Game.OVERWRITE);
			theGame.clearDirty();
			classifyOpening();
			theGame.fireEvents=true;
		}

		TimeControl tc = theUserProfile.getTimeControl();
		tc.reset(theClock);
		if (getEnginePlugin()!=null) {
			getEnginePlugin().newGame();
			getEnginePlugin().setTimeControls(tc.getPhase(0));
		}
    }

    protected int confirmSaveOne()
    {
        return JoDialog.showYesNoCancelDialog(
                    "confirm.save.one", "confirm",
                    "dialog.confirm.save","dialog.confirm.dont.save",
                    JOptionPane.YES_OPTION);
    }

    protected int confirmSaveAll()
    {
        return JoDialog.showYesNoCancelDialog(
                    "confirm.save.all", "confirm",
                    "dialog.confirm.save","dialog.confirm.dont.save",
                    JOptionPane.YES_OPTION);
    }

	protected boolean hasGameSource(Command cmd, boolean collectionOnly)
	{
		if (cmd!=null && cmd.data instanceof GameSource)
			return true;
			/*	source passed with command; fine */

		/**	else: collection or list panel in frontmost window ?	*/
		CollectionPanel collPanel = collectionPanel();
		ListPanel listPanel = listPanel();

		if (collPanel!=null && collPanel.isInFront() ||
			listPanel!=null && listPanel.isInFront())
		{
			if (!collectionOnly && (listPanel!=null)) {
				if (listPanel.hasSelection())
					return true;
					//  set of selected games
				SearchRecord srec = listPanel.getSearchRecord();
				if (srec.hasFilter() || srec.hasSortOrder())
					return true;
					//  current result set
			}

			if ((collPanel!=null) && collPanel.hasSelection())
				return true;
				//  set of selected collections
		}

		/** else: document panel in front ? */
		DocumentPanel docPanel = docPanel();
		if (docPanel!=null && docPanel.isInFront()) {
			return theGame.getLength() > 0;  //  one open game
		}

		//  alternative: use the complete database !?!
		return false;
	}

    protected SearchRecord getSearchRecord()
    {
        /**	collection or list panel in frontmost window ?	*/
        ListPanel listPanel = listPanel();
        if (listPanel!=null)
            return listPanel.getSearchRecord();
        else
            return null;
    }

	protected GameSource getGameSource(Command cmd,
									   boolean collectionOnly,
									   boolean selectionOnly)
	{
		if (cmd!=null && cmd.data instanceof GameSource)
			return ((GameSource)cmd.data);
			/*	source passed with command; fine */

		/**	else: collection or list panel in frontmost window ?	*/
		CollectionPanel collPanel = collectionPanel();
		ListPanel listPanel = listPanel();
        DocumentPanel docPanel = docPanel();

        JoPanel preferredPanel = null;

        if (collPanel!=null && collPanel.isFocusInside() && collPanel.hasSelection())
            preferredPanel = collPanel;
        else if (listPanel!=null && listPanel.isFocusInside()
				&& !collectionOnly
				&& (!selectionOnly || listPanel.hasSelection()))
            preferredPanel = listPanel;
        else if (docPanel!=null && docPanel.isFocusInside() && !collectionOnly)
            preferredPanel = docPanel;
        else if (collPanel!=null && collPanel.isInFront() && collPanel.hasSelection())
            preferredPanel = collPanel;
        else if (listPanel!=null && listPanel.isInFront()
				&& !collectionOnly
				&& (!selectionOnly || listPanel.hasSelection()))
            preferredPanel = listPanel;
        else if (docPanel!=null && docPanel.isInFront() && !collectionOnly)
            preferredPanel = docPanel;
        else {
            //  can't help it
            return null;
        }

        if (preferredPanel==listPanel)
        {
            if (listPanel.getCurrentSelection().hasSelection())
                return GameSource.gameSelection(listPanel.getCurrentSelection());
            else if (!selectionOnly)
                return GameSource.gameSelection(listPanel.getCompleteResult());
            //  note that this may not be the most efficient implementation,
            //  since we have to iterate over the complete list
            //  TODO think about a more elegant solution that retrieves the result set
            //  through the current filter settings
        }

		if (preferredPanel==collPanel)
		{
            return GameSource.collectionSelection(collPanel);
			//  set of selected collections
		}

		/** else: document panel in front ? */
		if (preferredPanel==docPanel)
        {
			if (theHistory.size()==1) {
				if (theGame.hasContents())
					return GameSource.gameObject(theGame);  //  one open game
			}
			else {
				Game[] array = theHistory.getArray(false);
				if (array!=null && array.length > 0)
					return GameSource.gameList(array);  //  all open games
			}
		}

		//  alternative: use the complete database !?!
		return null;
	}

	protected void newCollection(Command cmd) throws Exception
	{
		showPanel("window.collectionlist");

        int parentId;
        Collection coll = null;

        try {
            DBTask.broadcastOnUpdate("collection.new");

            GameSource src = getGameSource(cmd,true,false);
            if (src!=null)
                parentId = src.firstId();
            else
                parentId = 0;

            JoConnection conn = null;
            coll = null;
            try {
                conn = JoConnection.get();
                String name = Collection.makeUniqueName(parentId,Language.get("collection.new"),conn);
                coll = Collection.newCollection(parentId, name, conn);
                coll.insert(conn);
            } finally {
                if (conn!=null) conn.release();
            }


        } finally {
            DBTask.broadcastAfterUpdate((coll==null) ? 0:coll.Id);
        }

        if (collectionPanel() != null) {
			collectionPanel().expand(parentId);
			collectionPanel().edit(coll.Id);
		}
	}

    protected int confirmDBPaste(GameSource src)
    {
        return JoDialog.showYesNoCancelDialog("dialog.paste.message", "dialog.paste.title",
                              "dialog.paste.copy", "dialog.paste.same",
                              JOptionPane.YES_OPTION);
    }

	public void gameMaintenance(Command cmd, GameSource src, Collection target)
		throws Exception
	{
		GameTask task = null;

        if (cmd.code.equals("menu.edit.paste")) {
            //  replaced by either menu.edit.paste.same or menu.edit.paste.copy
            if (!Collection.hasContents(Collection.CLIPBOARD_ID)) {
                return;
            }
            else switch (confirmDBPaste(src))
            {
            case JOptionPane.YES_OPTION:    cmd.code = "menu.edit.paste.copy"; break;
            case JOptionPane.NO_OPTION:     cmd.code = "menu.edit.paste.same"; break;
            default:
            case JOptionPane.CANCEL_OPTION: return; //  user cancelled
            }
        }

		int CId = Collection.makeClipboard(null);
		if (cmd.code.equals("menu.edit.cut")) {
			//	Cut = move to clipboard
			//	except system folders
			task = new MoveToClipboardTask(src, CId, Collection.TRASH_ID,  true);
		}
		else if (cmd.code.equals("menu.edit.copy")) {
			//	Copy = copy to clipboard
			task = new CopyToClipboardTask(src, CId, Collection.TRASH_ID);
		}
        else if (cmd.code.equals("menu.edit.paste.copy")) {
        	//	Paste = copy from clipboard
            if (target!=null)
                task = new CopyTask(src, target.Id, true);
            else {
                GameSource clip = GameSource.collectionContents(CId);
			    task = new CopyTask(clip, src.firstId(),true);
            }
		}
		else if (cmd.code.equals("menu.edit.paste.same")) {
			//	Paste Same = move from clipboard
            if (target!=null)
                task = new MoveTask(src, target.Id, false,true,false);
            else {
                GameSource clip = GameSource.collectionContents(CId);
                task = new MoveTask(clip, src.firstId(),false,true,false);
            }
		}
		else if (cmd.code.equals("menu.edit.clear")) {
			//	Clear = move to trash
			//	except system folders
			task = new MoveTask(src, Collection.TRASH_ID,true,false,true);
		}
		else if (cmd.code.equals("menu.edit.restore")) {
			//	Restore = move from trash
			task = new RestoreTask(src);
		}
		else if (cmd.code.equals("menu.edit.erase")) {
			//	There's no need for immediate erase. Use empty.trash instead;
			//task = new EraseTask(src);
			throw new IllegalStateException("not supported anymore");
		}
		else if (cmd.code.equals("menu.edit.empty.trash")) {
			//	Empty Trash = erase trash
			GameSource trash = GameSource.collectionContents(Collection.TRASH_ID);
			task = new EraseTask(trash);
		}
		else if (cmd.code.equals("menu.edit.collection.crunch")) {
			//  Crunch collection = update index column
            task = new CrunchTask(src,getSearchRecord());
		}
		else if (cmd.code.equalsIgnoreCase("dnd.move.games"))
		{
			int targetId = 0;	//	 default==root
			boolean setOId=false, calcIdx=true;
			if (target!=null) {
				targetId = target.Id; // default=root
				setOId = target.isInTrash() || target.isInClipboard();
				calcIdx = !target.isInTrash() && !target.isInClipboard();
			}
			task = new MoveTask(src, targetId, setOId,calcIdx,false);
		}
		else if (cmd.code.equalsIgnoreCase("dnd.move.top.level")) {
			task = new MoveTask(src, 0, false,true,false);
		}

		if (!(task instanceof MoveTask) && (task.size() > 500))
			task.setSilentTime(0);	//	this will likely be a "long runner". Show progress dialog always.
		//	todo maybe ask user: "Do you really want to copy 5.6 million files to the clipboard ???"

		task.setDisplayComponent((JoPanel)Util.nvl(listPanel(),collectionPanel(),getFocusPanel()));
//		task.run();	//	snychroneous operation
		task.start();	// for asynchronesous operation
	}

	/**
	 * messages from the plugin (and from the clock come from a separate thread
	 * but since this affects the GUI quite a bit, we better keep them in synch with
	 * the event dispatching thread. DeferredMessageListeners will be called after
	 * all GUI events have been processed.
	 */
	public void handleMessage(Object who, int what, Object data)
	{
		try {
			if (who==getEnginePlugin() || who==theOpeningLibrary || (who instanceof BookQuery))
				handlePluginMessage(what, data);
			if (who==theClock)
				handleClockMessage(what);
			if (who==docPanel())
				handleDocMessage(what,data);
		} catch (Exception e) {
			Application.error(e);
		}
	}

	public void handleClockMessage(int what) throws Exception
	{
		switch (what) {
		case Clock.TIME_ELAPSED:
				if (theGame.getResult()==Game.RESULT_UNKNOWN)
					gameFinished(Clock.TIME_ELAPSED,0,true);
				break;
		}
	}

	public void handleDocMessage(int what, Object data) throws BadLocationException, ParseException
	{
		switch (what) {
		case DocumentPanel.EVENT_USER_MOVE:
				//  move input through keyboard
				//  message from DocumentEditor
				Object[] params = (Object[])data;
				MoveNode after = (MoveNode)params[0];
				Move mv = (Move)params[1];
				theGame.gotoMove(after);
				//  TODO think about more general copy/paste with PGN fragments
				handleUserMove(mv,true);
				break;
		}
	}

	private void setGameDefaultInfo()
	{
		if (getEnginePlugin()==null)
			return;

		boolean modified = false;
		if (/*theGame.getPosition().gameMove()<=1*/true) {
			if (theGame.getTagValue(PgnConstants.TAG_WHITE)==null &&
				theGame.getTagValue(PgnConstants.TAG_BLACK)==null)
			try {
				if (theGame.getPosition().whiteMovesNext()) {
					theGame.setTagValue(PgnConstants.TAG_WHITE, getEnginePlugin().getName(), theGame);
					theGame.setTagValue(PgnConstants.TAG_BLACK, theUserProfile.get("user.name"), theGame);
					modified = true;
				}
				else {
					theGame.setTagValue(PgnConstants.TAG_BLACK, getEnginePlugin().getName(), theGame);
					theGame.setTagValue(PgnConstants.TAG_WHITE, theUserProfile.get("user.name"), theGame);
					modified = true;
				}
			} catch (Exception ex) {
				error(ex);
			}
			if (modified && docPanel()!=null)
				docPanel().reformat();
		}
	}

	public void handlePluginMessage(int what, Object data) throws Exception
	{
		Position pos = theGame.getPosition();
		switch (what) {
		case Plugin.PLUGIN_MOVE:
			handlePluginMove((EnginePlugin.EvaluatedMove) data, pos);
			setPlayState(PlayState.NEUTRAL);
			break;

		case Plugin.PLUGIN_ACCEPT_DRAW:
			EnginePlugin xplug = getEnginePlugin();
			if (xplug.wasOfferedDraw()) {
				//	engine accepts draw request from user
				gameDraw();	//	todo not used; should be gameFinished(...)
			}
			break;

		case Plugin.PLUGIN_DRAW:
			xplug = getEnginePlugin();
			//	engine offers draw to user
            String message = Language.get("dialog.engine.offers.draw");
            message = StringUtil.replace(message,"%engine%",getEnginePlugin().getDisplayName());

            int result = JoDialog.showYesNoCancelDialog(
                    message, "confirm",
                    "dialog.accept.draw","dialog.decline.draw",
                    JOptionPane.NO_OPTION);
			switch (result) {
			case JOptionPane.YES_OPTION:
					gameDraw(); break;
			case JOptionPane.NO_OPTION:
					xplug.declineDraw(); break;
			}
			break;

		case EnginePlugin.PLUGIN_ERROR:
		case EnginePlugin.PLUGIN_FATAL_ERROR:
			handleEngineError(what, data.toString());
			break;

		case Plugin.PLUGIN_RESIGNS:
			gameFinished(Plugin.PLUGIN_RESIGNS,pos.movesNext(),true);
			break;
/*
		case Plugin.PLUGIN_REQUESTED_HINT:
			if (boardPanel() != null)
				boardPanel().showHint(data);
			break;
*/
		case BOOK_ANALYSIS:
		case BOOK_SHOW:
		case BOOK_PLAY:
			BookQuery query = (BookQuery)data;
			handleBookMessage(query, pos);
			break;
		}
	}

	private void handlePluginMove(EnginePlugin.EvaluatedMove emv, Position pos) throws BadLocationException, ParseException
	{
		if (pos.isGameFinished(true)) {
			handleEngineError(EnginePlugin.PLUGIN_ERROR, "game is already finished");
			return;
		}

		if (emv==null) {
			handleEngineError(EnginePlugin.PLUGIN_ERROR,"");
			return;
		}
		Move mv = new Move(emv, pos);  //  assert correct owner
		MoveNode node = null;

		synchronized (theGame) {
			int oldOptions = pos.getOptions();
			pos.setOption(Position.CHECK+Position.STALEMATE, true);
//                System.err.println("engine move "+mv.toString());
			try {
				if (!pos.tryMove(mv)) {
					/*  throw new IllegalArgumentException("illegal move from engine");
						plugin got out of synch
					*/
					handleEngineError(EnginePlugin.PLUGIN_ERROR,mv.toString());
					return;
				}
				else
					pos.undoMove();
			} finally {
				pos.setOptions(oldOptions);
			}

			setGameDefaultInfo();

			if (theUserProfile.getBoolean("sound.moves.engine")) {
				int format = theUserProfile.getInt("doc.move.format",MoveFormatter.SHORT);
				speakMove(format, mv, pos);
			}

			theGame.insertMove(-1, mv, Game.NEW_LINE);
			node = theGame.getCurrentMove();
		}
		theClock.setCurrent(pos.movesNext());

		if (boardPanel() != null)
			boardPanel().move(mv, (float)(mv.distance()*0.2));

		if (mv.isGameFinished(false))
			gameFinished(mv.flags, pos.movedLast(), theGame.isMainLine());

		classifyOpening();

		if (node!=null && emv!=null) {
			//  update move evaluation history
			if (emv.score!=null && !emv.score.hasWDL())
				getEnginePlugin().mapUnit(emv.score);
			if (emv.score!=null && emv.score.hasWDL()) {
				node.engineValue = emv.score;
				theGame.setDirty(true);
			}
			/** UCI engines can't resign or offer draws (stupid gits)
			 *  we got to track the evaluation of recent moves and allow the user to adjudicate the game
			 */
			adjudicate(theGame, pos.movedLast(), pos.gamePly(), node,getEnginePlugin());
		}

		Command cmd = new Command("move.notify", null, mv, emv);
		theCommandDispatcher.broadcast(cmd, this);
	}

	protected void handleEngineError(int errorMessage, String errorText)
	{
		String dialogText;
		Map placeholders = new HashMap();
		placeholders.put("engine",getEnginePlugin().getDisplayName(null));
		placeholders.put("message",errorText);

		switch (errorMessage)
		{
		case EnginePlugin.BOOK_ERROR:
			dialogText = Language.get("error.book");
			break;
		case EnginePlugin.PLUGIN_ERROR:
			dialogText = Language.get("error.engine");
			//  recoverable error, stop calculating
			setMode(AppMode.USER_INPUT);
			pausePlugin();
			break;
		default:
		case EnginePlugin.PLUGIN_FATAL_ERROR:
			dialogText = Language.get("error.engine.fatal");
			//  unrecoverable error. shut down the plugin.
//			restartPlugin();
			closePlugin();
			break;
		}

		dialogText = StringUtil.replace(dialogText,placeholders);

		JOptionPane.showMessageDialog(JoFrame.getActiveFrame(),
			  dialogText, Language.get("error.engine.title"),
			  JOptionPane.ERROR_MESSAGE);
	}

	public boolean askNewGame()
	{
//		JCheckBox reverseCheckBox = new JCheckBox(Language.get("new.game.reverse"));
//		reverseCheckBox.setSelected(false);
		Object[] params = { "Start new game?" /*, reverseCheckBox*/ };

		int result = JOptionPane.showConfirmDialog(null,
				params, "New Game",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE);
		if (result!=JOptionPane.YES_OPTION)
			return false;

		Command cmd = new Command("menu.file.new");
		//cmd.data = reverseCheckBox.isSelected();
		theCommandDispatcher.handle(cmd,this);
		return true;
	}


	protected boolean adjudicate(Game game, int engineColor, int gamePly, MoveNode node,
	                                  EnginePlugin engine)
	        throws BadLocationException, ParseException
	{
//		System.err.println(move.toString()+"="+move.getValue());
		if (game==null || engine==null
		        || (game.getResult()!=PgnConstants.RESULT_UNKNOWN)
		        || !game.isMainLine(node)) return false;

		if (!engine.canResign()
		        && engine.shouldResign(game,engineColor,gamePly,node))
		{
			//  if last 5 moves are below resignation threshold, resign
			gameFinished(Plugin.PLUGIN_RESIGNS,engineColor,true);
			return true;
		}

		if (!engine.canAcceptDraw()
				&& engine.wasOfferedDraw())
		{
			//  engine was offered draw, but can't decide
			//  if last 5 moves are within draw threshold, accept draw without asking
			if (engine.shouldDraw(game,gamePly,node)) {
				gameFinished(PLUGIN_ACCEPT_DRAW, engineColor, true);
			}
			else {
				//	engine declines draw offer
				drawDeclined(engineColor);
			}
			return true;
		}

		if (!engine.canOfferDraw()
				&& (theGame.engineDrawOffer < 0 || theGame.engineDrawOffer+20 <= gamePly)
		        && engine.shouldDraw(game,gamePly,node))
		{
			//  if last 5 moves are within draw threshold, ask to adjudicate
			theGame.engineDrawOffer = gamePly;   //  ask only once per session and game
			String message = Language.get("dialog.engine.offers.draw");
			message = StringUtil.replace(message,"%engine%",getEnginePlugin().getDisplayName());

			int result = JoDialog.showYesNoCancelDialog(
			        message, "confirm",
			        "dialog.accept.draw","dialog.decline.draw",
			        JOptionPane.NO_OPTION);
			if (result==JOptionPane.YES_OPTION) {
				gameDraw();
				return true;
			}
		}

		return false;
	}


	//-------------------------------------------------------------------------------
	//	useful stuff
	//-------------------------------------------------------------------------------

	/**
	 * open the application
	 * restore layout from user profile
	 */
	public void open()
		throws Exception
	{
		//  DEBug: don't check SSL certificates on download (because pgn files are not that critical in the first place)
		HttpsUtil.acceptAll();

		/**	load user profile		 */
		readProfile();

		/**	load language		 */
		Language.setLanguage(theLanguageDirectory, theUserProfile.getString("user.language"));

		theGame = new Game(theUserProfile.getStyleContext(),
						   null,null, null/*PgnUtil.currentDate()*/, null, null);
		theClock = new Clock();
		theClock.addMessageListener(this);

        theHistory = new History();
        theHistory.add(theGame);
// 		switchGame(0);

		theCommandDispatcher = new CommandDispatcher();
		theCommandDispatcher.addCommandListener(this);

		TimeControl tc = theUserProfile.getTimeControl();
		tc.reset(theClock);

		/*	setup textures	*/
		TextureCache.setDirectory(new File(theWorkingDirectory, "images/textures"));

		/**	set look & feel	 */
		String lnfClassName = getLookAndFeelClassName();
		setLookAndFeel(lnfClassName);

		if (Version.mac) new MacAdapter();      //  listens to application menu

		AppMode firstMode = AppMode.valueOf(theUserProfile.get("game.mode", AppMode.USER_ENGINE));

		//	create DB adapter
		JoConnection.getAdapter(true);

		//	check Game-MoreGame consistency
		JoConnection.postWithConnection(new Command("db.check"));

		if (theUserProfile.getBoolean("doc.load.history"))
			openHistory();

		theOpeningLibrary = new OpeningLibrary();
		theOpeningLibrary.open(theUserProfile,theConfig);

		/*	setup windows	*/
		openFrames(theUserProfile.getFrameProfiles());

		if (JoFrame.countVisibleFrames()==0) {
			/*	corrupted profile ? should never happen	*/
			System.err.println("corrupted layout ?");
			openFrames(FrameProfile.FACTORY_LAYOUT);
		}

//		theCommandDispatcher.handle(new Command("menu.file.new",null,null),this);
		SplashScreen.close();

//		showErrorDialog(new IOException("shit happens"));

		Runtime.getRuntime().addShutdownHook(shutdownHook = new DirtyShutdown());

		//  open files ?
		String filePath = Version.getSystemProperty("jose.file");
		if (filePath!=null) {
			Command cmd = new Command("menu.file.open.all",null,filePath);
			theCommandDispatcher.handle (cmd,this);
		}

		if (Version.windows) {
			Object assoc = theUserProfile.get("jos.associate.old");
			if (assoc==null) {
				assoc = WinUtils.associateFileExtension("jos","jose.exe");
				assoc = WinUtils.associateFileExtension("jose","jose.exe");
				theUserProfile.set("jos.associate.old",assoc);
			}
		}

		setMode(firstMode);	//	set now so that it can be broadcast

		SwingUtilities.invokeLater(new Startup());
	}

	private static String getLookAndFeelClassName()
	{
		/*
			1. from cli -Djose.look.and.feel
			2. from user profile "ui.look.and.feel.new"
			3. from factory setting
		 */
		String lnfClassName = Version.getSystemProperty("jose.look.and.feel");
		if ("default".equalsIgnoreCase(lnfClassName))
			lnfClassName = LookAndFeelList.getDefaultClassName();
		if (lnfClassName==null)
            lnfClassName = theUserProfile.getString("ui.look.and.feel2");
		if (lnfClassName==null) {
			lnfClassName = UserProfile.getFactoryLookAndFeel();
			if (lnfClassName==null)
				lnfClassName = LookAndFeelList.getDefaultClassName();
			theUserProfile.set("ui.look.and.feel2",lnfClassName);
		}
		return lnfClassName;
	}


	protected class Startup implements Runnable, ActionListener
	{
		public void run() {

			getContextMenu();

			//	launch DB process
			//	launch background process
			DBAdapter adapter = JoConnection.getAdapter(true);
			adapter.launchProcess();

			//  deferred loading of ECO classificator & additional fonts
			Thread deferredLoader = new DeferredStartup();
			deferredLoader.setPriority(Thread.MIN_PRIORITY);
			deferredLoader.start();

			Sound.initialize(theConfig);	//	will start a background thread (that initially sleeps)

			if (Version.getSystemProperty("jose.detect",false))
			{
				if (ApplicationListener.searchApplication()) {
					//	another instance detected - exit immediately
					System.err.println("another running instance detected");
					System.exit(+2);
				}

				applListener = new ApplicationListener();
				applListener.start();
			}

		}

		public void actionPerformed(ActionEvent e)
		{
		}
	}

	protected class DeferredStartup extends Thread
	{
		public void run()
		{
			try {
				sleep(1000);
			} catch (InterruptedException e) {
				//
			}
			JoStyleContext styles = (JoStyleContext)theUserProfile.getStyleContext();
			styles.assertCustomFonts();
			getClassificator();
			initEBoardConnector();
			//if (boardPanel()!=null)
			//	boardPanel().getView().useAppBoard(EBoardConnector.Mode.PLAY,theApplication);
		}
	}

	/**
	 * usually, a shutdown is performed throuh File/Quit and issuing "menu.file.quit"
	 * however, if the programs is shut down otherwise (application menu on OS X ?)
	 * we attempt to perform a shutdown as good as possible
	 * note that such a dirty shutdown cannot be cancelled by the user
	 */
	protected class DirtyShutdown extends Thread
	{
		public void run()
		{
			try {
				if (shutdownHook!=null) {
					Command cmd = new Command("menu.file.quit");
					quit(cmd);
				}
			} catch (Throwable e) {
				error(e);
			}
		}
	}

	private void openFrames(FrameProfile[] frameProfiles)
	{
		Vector openFrames = new Vector();
		for (int i=0; i<frameProfiles.length; i++)
		{
			if (frameProfiles[i].state == FrameProfile.HELP_FRAME)
				helpBounds = frameProfiles[i].bounds;
			else if (JoFrame.isVisible(frameProfiles[i].state))
            {
				JoFrame frame = new JoFrame(frameProfiles[i]);
 				openFrames.add(frame);
				 SwingUtilities.invokeLater(new Runnable() {
					 @Override
					 public void run() {
						 frame.setComponentsVisible(true);
						 frame.revalidate();
					 }
				 });
			}
		}

		for (int i = openFrames.size()-1; i >= 0; i--) {
			JoFrame frame = (JoFrame) openFrames.get(i);
			frame.setVisible(true);
			frame.revalidate();
        }
	}

	public void invokeWithPlugin(final Runnable lambda) {
		EnginePlugin plugin = getEnginePlugin();
		if (plugin==null)
		try {
			plugin = createEnginePlugin();
			if (plugin==null)
				return;
			plugin.setLaunchHook(lambda);
			openEnginePlugin();	// todo may be lenghty. put to worker thread?
		} catch(IOException e) {
			error(e);
		}
		else if (!plugin.isOpen()) {
			plugin.setLaunchHook(lambda);
		}
		else {
			lambda.run();
		}
	}

	public void submitBookQuery(int onCompletion, Move lastMove)
	{
		if (onCompletion==BOOK_PLAY)
			setPlayState(PlayState.BOOK);
		Position pos = theGame.getPosition();
		//BookEntry hint = theOpeningLibrary.selectMove(pos, theMode,true, pos.whiteMovesNext());
		OpeningLibrary lib = Application.theApplication.theOpeningLibrary;
		BookQuery query = new BookQuery(pos, onCompletion, lastMove);

		if (lib.isEmpty()) {
			//	shortcut if there is no book at all
			query.result = new ArrayList<BookEntry>();
			query.sendMessage(onCompletion,query);
		}
		else {
			theExecutorService.submit(query);
		}
	}

	public boolean queryBookMoveForPlay(Move lastMove)
	{
		switch (theOpeningLibrary.engineMode)
		{
			case OpeningLibrary.PREFER_ENGINE_BOOK:
				if (getEnginePlugin()!=null && getEnginePlugin().isBookEnabled())
					return false;
				//  else: intended fall-through
			default:
			case OpeningLibrary.GUI_BOOK_ONLY:
			case OpeningLibrary.PREFER_GUI_BOOK:
				submitBookQuery(BOOK_PLAY,lastMove);
				return true;

			case OpeningLibrary.NO_BOOK:
				return false;   //  pretty easy
		}
	}

	protected boolean playBookMove(BookEntry entry)
			throws Exception
	{
		//  play the book move
		Position pos = theGame.getPosition();
		MoveNode node = null;

		synchronized (theGame) {
			int oldOptions = pos.getOptions();
			pos.setOption(Position.CHECK+Position.STALEMATE, true);
//                System.err.println("engine move "+mv.toString());
			try {
				if (!pos.tryMove(entry.move)) {
					/*  throw new IllegalArgumentException("illegal move from book!");			*/
					handleEngineError(EnginePlugin.BOOK_ERROR,entry.move.toString());
					return false;
				}
				else
					pos.undoMove();
			} finally {
				pos.setOptions(oldOptions);
			}

			if (theUserProfile.getBoolean("sound.moves.engine")) {
				int format = theUserProfile.getInt("doc.move.format",MoveFormatter.SHORT);
				speakMove(format, entry.move, pos);
			}

			theGame.insertMove(-1, entry.move, Game.NEW_LINE);
			node = theGame.getCurrentMove();
		}
		theClock.setCurrent(pos.movesNext());

		if (boardPanel() != null)
			boardPanel().move(entry.move, (float)(entry.move.distance()*0.2));

		EnginePlugin.EvaluatedMove emv = new EnginePlugin.EvaluatedMove(entry.move,-1,new Score(),null);
		entry.toScore(emv.score,1000);

		if (node!=null && entry.move!=null) {
			//  update move evaluation history
			if (node.engineValue==null)
				node.engineValue = emv.score;
			theGame.setDirty(true);
		}

		theCommandDispatcher.broadcast(new Command("move.notify", null, entry.move, emv), this);
		setPlayState(PlayState.NEUTRAL);

		if (entry.move.isGameFinished(false))
			gameFinished(entry.move.flags,pos.movedLast(), theGame.isMainLine());

		classifyOpening();
		return true;
	}

	protected boolean showFRCWarning(boolean atCastling)
	{
		if (!atCastling) {
			if (shownFRCWarning) return false;
			if (! theUserProfile.getBoolean("show.frc.warning",true)) return false;
		}
		String title = Language.get("warning.engine");
		String message = "<html>"+Language.get("warning.engine.no.frc")+"<br><br>";

		JCheckBox dontShowAgain;
		Box box = Box.createVerticalBox();
		box.add(new JoStyledLabel(message));

		if (atCastling) {
			JOptionPane.showMessageDialog(JoFrame.theActiveFrame,
					box, title, JOptionPane.WARNING_MESSAGE);
		}
		else {
			box.add(dontShowAgain = new JCheckBox(Language.get("warning.engine.off")));
			JOptionPane.showMessageDialog(JoFrame.theActiveFrame,
					box, title, JOptionPane.WARNING_MESSAGE);
			theUserProfile.set("show.frc.warning", !dontShowAgain.isSelected());
		}

		return (shownFRCWarning=true);
	}

	public EnginePlugin createEnginePlugin() throws IOException
	{
		/**	setup plugin		 */
		String name = theUserProfile.getString("plugin.1");
		engine = (EnginePlugin)Plugin.getPlugin(name,Version.osDir,true);

		if (engine==null) {
			EnginePlugin defaultPlugin = (EnginePlugin) Plugin.getDefaultPlugin(Version.osDir, true);
			if (defaultPlugin == null) {
				//	no plugin available !
				JoDialog.showErrorDialog(null, "error.engine.not.found", "plugin.1", name);
				setMode(AppMode.USER_INPUT);   //  no use bothering the user with more errors
				return null;
			} else {
				//	use default plugin instead
				engine = defaultPlugin;
				JoDialog.showErrorDialog(null, "error.plugin.revert.default",
						"plugin.1", name,
						"plugin.2", defaultPlugin.getName());
			}
		}

		try {
			engine.init(theGame.getPosition(), Version.osDir);
			engine.addInputListener(this,1);

			//	set real time
			broadcast(new Command("new.plugin", null, getEnginePlugin()));
		} catch (IOException ioex) {
			if (getEnginePlugin().print()!=null)
				getEnginePlugin().print().println(ioex.getMessage());
			throw ioex;
		}
		return engine;
	}

	public void setMode(AppMode mode)
	{
		if (mode!=theMode) {
			theMode = mode;
			thePlayState = PlayState.NEUTRAL;
			Command cmd = new Command("app.state.changed", null,
							theMode, thePlayState);
			broadcast(cmd);
		}
	}

	public void setPlayState(PlayState thinkState)
	{
		theMode = AppMode.USER_ENGINE;
		thePlayState = thinkState;
		Command cmd = new Command("app.state.changed", null,
							theMode, thePlayState);
		broadcast(cmd);
	}

	public boolean openEnginePlugin() throws IOException
	{
		if ((getEnginePlugin() == null) && (createEnginePlugin()==null))
				return false;

//	            showPanel("window.eval");
		showPanel("window.engine");

		engine.open(Version.osDir);	//	todo maybe lenghty (setOptions)
		engine.addMessageListener(this);

		TimeControl tc = theUserProfile.getTimeControl();
		engine.setTimeControls(tc.getPhase(0));

		//  adjust book settings
		switch (theOpeningLibrary.engineMode)
		{
			case OpeningLibrary.GUI_BOOK_ONLY:
			case OpeningLibrary.NO_BOOK:
				//  disable the engine book
				engine.disableBook();
				break;
		}
		return true;
	}

	public void pausePlugin()
	{
		pausePlugin(theMode==AppMode.ANALYSIS);
	}

	public void pausePlugin(boolean analyze)
	{
		EnginePlugin engine = getEnginePlugin();
		Position position = theGame.getPosition();
		if (engine!=null) {
			if ( analyze && !position.isGameFinished(true)) {
			//	engine.analyze(position);
				submitBookQuery(BOOK_ANALYSIS,null);	//	right?
			}
			if (!analyze && !engine.isPaused()) {
				engine.pause();
//				if (enginePanel()!=null)
//					enginePanel().exitBook();	// right?
			}
		}
	}

    public void closePlugin()
    {
        if (getEnginePlugin() != null) {
			Plugin oldPlugin = getEnginePlugin();
            oldPlugin.close();
			engine = null;
			broadcast(new Command("close.plugin", null, oldPlugin));
	        shownFRCWarning=false;
        }
    }

	public void switchPlugin()
		throws IOException
	{
		if (getEnginePlugin()!=null) {
			closePlugin();
			openEnginePlugin();
		}
	}

	public void restartPlugin() throws IOException
	{
		closePlugin();
		openEnginePlugin();
	}

	public void askRestartPlugin() throws IOException
	{
		int answer = JoDialog.showYesNoDialog("plugin.restart.ask","",null,null, JOptionPane.YES_OPTION);
		if (answer == JOptionPane.YES_OPTION)
			restartPlugin();
	}

	public void askSwitchPlugin() throws IOException
	{
		if (getEnginePlugin()==null)    //  no need to ask
			return;
		else {
			int answer = JoDialog.showYesNoDialog("plugin.switch.ask","",null,null, JOptionPane.YES_OPTION);
			if (answer == JOptionPane.YES_OPTION)
				switchPlugin();
		}
	}

	public EBoardConnector initEBoardConnector() {
		if (eboard==null) {
			eboard = new ChessNutConnector();
			if (boardPanel()!=null)
				boardPanel().getView().useAppBoard(EBoardConnector.Mode.PLAY,this);
			eboard.readProfile(theUserProfile);
			if (!eboard.connected) eboard.connect();
		}
		return eboard;
	}

	public int getInsertMoveWriteMode(Move mv)
	{
		int writeMode = theUserProfile.getInt("doc.write.mode",Game.ASK);
		if (writeMode != Game.ASK) return writeMode;

//		Point location;
		WriteModeDialog dialog = (WriteModeDialog)getDialog("dialog.write.mode");
		if (boardPanel() != null) {
			Point locationOnScreen = boardPanel().getView().getLocationOnScreen(mv.to);
			dialog.fitInto(locationOnScreen,boardPanel());
		}
		else {
			dialog.stagger(JoFrame.getActiveFrame(), 10, 10);
		}

		dialog.show(writeMode);
		writeMode = dialog.getWriteMode();

		if (writeMode==Game.CANCEL)
			return Game.CANCEL;

		if (dialog.askUser())
			theUserProfile.set("doc.write.mode",Game.ASK);
		else
			theUserProfile.set("doc.write.mode",writeMode);

		return writeMode;
	}

	protected void gameDraw()
	{
		try {
			theGame.setResult(Game.DRAW);
//			if (docPanel()!=null) docPanel().reformat();
		} catch (Exception e) {
			Application.error(e);
		}
		if (getEnginePlugin()!=null) getEnginePlugin().pause();
		theClock.halt();
	}

	protected String getPlayerName(int color)
	{
		String name = (String)theGame.getTagValue(EngUtil.isWhite(color) ? PgnConstants.TAG_WHITE:PgnConstants.TAG_BLACK);
		if (name == null)
			name = Language.get(EngUtil.isWhite(color) ? "message.white":"message.black");
		return name;
	}

	protected String getNextPlayerName()
	{
		return getPlayerName(theGame.getPosition().movesNext());
	}

	protected String getLastPlayerName()
	{
		return getPlayerName(theGame.getPosition().movedLast());
	}

	protected void drawDeclined(int color) {
		String message = Language.get("message.draw.declined");
		message = StringUtil.replace(message,"%player%",getPlayerName(color));
		SplashScreen.close();
		JOptionPane.showMessageDialog(JoFrame.getActiveFrame(),
				message, Language.get("message.result"),
				JOptionPane.INFORMATION_MESSAGE);
	}

	protected void gameFinished(int flags, int color, boolean mainLine)
			throws BadLocationException, ParseException
	{
		String message = null;
		boolean resultDirty = false;
		if (flags==Plugin.PLUGIN_RESIGNS) {
			message = Language.get("message.resign");
			message = StringUtil.replace(message,"%player%",getPlayerName(color));
			if (mainLine) {
				if (EngUtil.isWhite(color))
					resultDirty = theGame.setResult(Game.BLACK_WINS);
				else
					resultDirty = theGame.setResult(Game.WHITE_WINS);
			}
		}
		else if (flags==Plugin.PLUGIN_ACCEPT_DRAW) {
			message = Language.get("message.draw.accepted");
			message = StringUtil.replace(message,"%player%",engine.getDisplayName());
			resultDirty = theGame.setResult(Game.DRAW);
		}
		else if (flags==Clock.TIME_ELAPSED) {
			boolean whiteLose = (theClock.getWhiteTime() < 0) || !theGame.getPosition().canMate(WHITE);
			boolean blackLose = (theClock.getBlackTime() < 0) || !theGame.getPosition().canMate(BLACK);

			if (whiteLose && blackLose) {
				message = Language.get("message.time.draw");
				if (mainLine)
					resultDirty = theGame.setResult(Game.DRAW);
			}
			else if (whiteLose) {
				message = Language.get("message.time.lose");
				message = StringUtil.replace(message,"%player%",getLastPlayerName());
				if (mainLine)
					resultDirty = theGame.setResult(Game.BLACK_WINS);
			}
			else {
				message = Language.get("message.time.lose");
				message = StringUtil.replace(message,"%player%",getLastPlayerName());
				if (mainLine)
					resultDirty = theGame.setResult(Game.WHITE_WINS);
			}
		}
		else if (EngUtil.isMate(flags)) {
			if (EngUtil.isWhite(color)) {
				message = Language.get("message.mate");
				message = StringUtil.replace(message,"%player%",getPlayerName(Game.WHITE));
				if (mainLine)
					resultDirty = theGame.setResult(Game.WHITE_WINS);
			}
			else {
				message = Language.get("message.mate");
				message = StringUtil.replace(message,"%player%",getPlayerName(Game.BLACK));
				if (mainLine)
					resultDirty = theGame.setResult(Game.BLACK_WINS);
			}
		}
		else if (EngUtil.isStalemate(flags)) {
			message = Language.get("message.stalemate");
			if (mainLine)
				resultDirty = theGame.setResult(Game.DRAW);
		}
		else if (EngUtil.isDraw3(flags)) {
			message = Language.get("message.draw3");
			if (mainLine)
				resultDirty = theGame.setResult(Game.DRAW);
		}
		else if (EngUtil.isDraw50(flags)) {
			message = Language.get("message.draw50");
			if (mainLine)
				resultDirty = theGame.setResult(Game.DRAW);
		}
		else if (EngUtil.isDrawMat(flags)) {
			message = Language.get("message.drawmat");
			if (mainLine)
				resultDirty = theGame.setResult(Game.DRAW);
		}
		else
			return;

		if (resultDirty && docPanel()!=null) docPanel().reformat();

		if (getEnginePlugin()!=null)
			getEnginePlugin().pause();
		theClock.halt();

		getSoundFormatter();
		switch (theGame.getResult()) {
		case Game.DRAW:
				if (!EngUtil.isStalemate(flags) || theSoundFormatter==null ||
				        !theSoundFormatter.play("Stalemate.wav"))
					Sound.play("sound.draw");
				break;
		default:
		case Game.WHITE_WINS:
		case Game.BLACK_WINS:
				if (!EngUtil.isMate(flags) || theSoundFormatter==null ||
				        !theSoundFormatter.play("Checkmate.wav"))
					Sound.play("sound.mate");
				break;
		}
		SplashScreen.close();
        JOptionPane.showMessageDialog(JoFrame.getActiveFrame(),
				message, Language.get("message.result"),
				JOptionPane.INFORMATION_MESSAGE);
	}

	/**
	 * opens a PGN file for reading
	 */
	protected void openFile()
	{
		boolean externalDB = JoConnection.getAdapter().getServerMode() == DBAdapter.MODE_EXTERNAL;

        File[] preferredDirs = new File[] {
            (File)theUserProfile.get("filechooser.open.dir"),
            new File(Application.theWorkingDirectory, "pgn"),
            Application.theWorkingDirectory,
        };

        int[] preferredFilters = new int[] {
            theUserProfile.getInt("filechooser.open.filter"),
		    JoFileChooser.ARCH,
            JoFileChooser.PGN,
            JoFileChooser.EPD,
        };

		JoFileChooser chooser = JoFileChooser.forOpen(preferredDirs, preferredFilters);

		if (chooser.showOpenDialog(JoFrame.getActiveFrame()) != JFileChooser.APPROVE_OPTION)
            return; //  cancelled

        File file = chooser.getSelectedFile();
        theUserProfile.set("filechooser.open.dir", chooser.getCurrentDirectory());
        theUserProfile.set("filechooser.open.filter", chooser.getCurrentFilter());

		try {
			int type = chooser.getCurrentFilter();
			if (FileUtil.hasExtension(file.getName(),"jos") || FileUtil.hasExtension(file.getName(),"jose"))
				type = JoFileChooser.ARCH;

			switch (type) {
			default:
			case JoFileChooser.PGN:
			case JoFileChooser.EPD:
                    showPanelFrame("window.collectionlist");
                    showPanelFrame("window.gamelist");

					PGNImport.openFile(file);
					break;

			case JoFileChooser.ARCH:
					if (externalDB) {
						JoDialog.showErrorDialog("Archive Import is not available for an external database.\nUse *.pgn or *.zip instead.");
						break;
					}

                    showPanelFrame("window.collectionlist");
                    showPanelFrame("window.gamelist");

					ArchiveImport task = new ArchiveImport(file);
					task.start();
					break;
			}
		} catch (FileNotFoundException ex) {
		    JoDialog.showErrorDialog("File not found: "+ex.getLocalizedMessage());
		} catch (IOException ex) {
		    JoDialog.showErrorDialog(ex.getLocalizedMessage());
        } catch (Exception ex) {
            Application.error(ex);
            JoDialog.showErrorDialog(ex.getLocalizedMessage());
        }
	}

	/**
	 * opens a PGN file for reading
	 */
	protected void openURL()
	{
        SplashScreen.close();
        String urlStr = JOptionPane.showInputDialog(Language.get("menu.file.open.url"));

        if (urlStr==null) return;
        urlStr = urlStr.trim();
        if (urlStr.length()==0) return;

        URL url = null;
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException mex) {
            JoDialog.showErrorDialog(mex.getLocalizedMessage());
            return;
        }

//        theUserProfile.add("filechooser.url.history", url.toExternalForm());

        //  determine type
        String fileName = FileUtil.getFilePath(url);
		Map pmap = new HashMap();
		pmap.put("p",url.toExternalForm());

        try {
            if (FileUtil.hasExtension(fileName,"pgn") ||
                FileUtil.hasExtension(fileName,"epd") ||
                FileUtil.hasExtension(fileName,"fen") ||
                FileUtil.hasExtension(fileName,"zip") ||
                FileUtil.hasExtension(fileName,"gzip"))
            {
                showPanelFrame("window.collectionlist");
                showPanelFrame("window.gamelist");

                PGNImport.openURL(url);
            }
            else if (FileUtil.hasExtension(fileName,"jose"))
            {
                showPanelFrame("window.collectionlist");
                showPanelFrame("window.gamelist");

                ArchiveImport task = new ArchiveImport(url);
                task.start();
            }
            else {
                JoDialog.showErrorDialog(null,"download.error.invalid.url",pmap);
                return;
            }
        } catch (Exception e) {
            JoDialog.showErrorDialog(null,"download.error.invalid.url",pmap);
        }
    }

	protected void openHistory()
	{
		int[] gids = theUserProfile.getHistory();
		if (gids != null && gids.length > 0)
		{
			GameSource src = GameSource.gameArray(gids);
			JoConnection.postWithConnection(new Command("edit.all", null,src,Boolean.FALSE));
		}
	}

	public synchronized boolean quit(Command cmd) throws Exception
	{
		//  close modeless dialogs
        SplashScreen.close();
		JoDialog.closeAll();

		//  ask for dirty docs
		if (theHistory.isDirty()) {
			switch (confirmSaveAll()) {
			case JOptionPane.YES_OPTION:	theHistory.saveAll(); break;
			case JOptionPane.NO_OPTION:		break;
			default:
			case JOptionPane.CANCEL_OPTION:	return false;
			}
		}

		broadcast(cmd);

		try {
			if (applListener!=null) applListener.close();
		} catch (Throwable e) {
		    Application.error(e);
		}

		//  save user profile
		try {
			if (helpSystem!=null)
				helpBounds = getHelpSystem().getWindowBounds();

			theUserProfile.update(helpBounds);

			writeProfile();

		    if (helpSystem!=null)
				getHelpSystem().close();
			JoFrame.closeAll();
		} catch (Throwable thr) {
			Application.error(thr);
		}

		if (eboard!=null)
			eboard.storeProfile(theUserProfile);

		//  close connection pool
		try {
			DBAdapter ad = JoConnection.getAdapter(false);
			if (ad!=null && (ad.getServerMode()== MODE_STANDALONE) && JoConnection.isConnected())
			try {
				JoConnection conn = JoConnection.get();
				ad.shutDown(conn);
			} catch (SQLException ioex) {
				//	can't connect. so be it
			}

			JoConnection.closeAll();

		} catch (Throwable thr) {
			Application.error(thr);
		}

		//  close engine plugin
		try {
			closePlugin();
		} catch (Throwable thr) {
			Application.error(thr);
		}

		//	close thread pool
		theExecutorService.shutdown();

		return true;
	}


	/**
	 *	read application & user profile from disk,
	 *	or revert to factory settings
	 */
	public void readProfile()
		throws IOException, ClassNotFoundException
	{
        /** list of preferred profile locations */
        ArrayList search_path = new ArrayList();

		String profilePath = Version.getSystemProperty("jose.profile");
		if (profilePath!=null) {
			//	1. path to profile explicitly set on command line
			File profile = new File(profilePath);
			if (profile.getParentFile().exists()) search_path.add(profile);
		}

        String homePath = Version.getSystemProperty("user.home");
        //  2. Library/Preferences Path (on Macs)
        File prefDir = new File(homePath,"Library/Preferences");
        if (prefDir.exists())
        {
            search_path.add(new File(prefDir,USER_PROFILE.substring(1)));  //  without "."
            search_path.add(new File(prefDir,USER_PROFILE));
        }

        //	3. default: user home directory
        File homeDir = new File(homePath);
        if (homeDir.exists())
            search_path.add(new File(homeDir,USER_PROFILE));

        //	4. default: jose installation directory
	    search_path.add(new File(theWorkingDirectory,USER_PROFILE));

        //  5. default: current directory
        search_path.add(new File(USER_PROFILE));

		theUserProfile = UserProfile.open(search_path);
		//	DON'T launch 3D window (it's just not reliable)
		theUserProfile.set("board.3d", Boolean.FALSE);
	}

	/**
	 *	write application & user profile to disk
	 */
	public void writeProfile()
		throws IOException
	{
		UserProfile.write(theUserProfile,UserProfile.searchPath,true);
	}

	public HelpSystem getHelpSystem()
	{
		if (helpSystem==null) {
			helpBounds = JoFrame.adjustBounds(helpBounds,true);
			helpSystem = new HelpSystem(theWorkingDirectory, "doc/man/help-en.hs", helpBounds);
		}
		return helpSystem;
	}

	public static File getWorkingDirectory() throws IOException
	{
		String workDir = Version.getSystemProperty("jose.workdir");
		if (workDir==null) workDir = Version.getSystemProperty("user.dir");
		if (workDir==null) workDir = ".";

        return new File(workDir).getCanonicalFile();
	}


	public ExportConfig getExportConfig()
	{
		if (theExportConfig==null)
			try {
				theExportConfig = new ExportConfig(new File(theWorkingDirectory,"xsl"));
			} catch (Exception e) {
				Application.error(e);
			}
		return theExportConfig;
	}

	//		implements plugin InputListener
	public void readLine(char[] chars, int offset, int len) { }

	//		implements plugin InputListener
	public void readEOF() {	}

	//		implements plugin InputListener
	public void readError(Throwable ex) {
		closePlugin();
		try {
			openEnginePlugin();
			//  hm ... this will most likely lead to follow-up errors...
			//  but there's not much else we can do.
		} catch (IOException e) {
			Application.error(e);
		}
	}

	public void lostOwnership(Clipboard clipboard, Transferable contents)
	{
		//  implements ClipboardOwner
	}


}
