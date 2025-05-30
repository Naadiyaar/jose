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

package de.jose.view;

import de.jose.*;
import de.jose.book.GameRef;
import de.jose.book.lichess.LiChessGameRef;
import de.jose.book.lichess.LiChessOpeningExplorer;
import de.jose.chess.*;
import de.jose.chess.Position;
import de.jose.comm.Command;
import de.jose.comm.CommandAction;
import de.jose.comm.msg.DeferredMessageListener;
import de.jose.pgn.ECOClassificator;
import de.jose.book.BookEntry;
import de.jose.pgn.Game;
import de.jose.pgn.MoveNode;
import de.jose.plugin.*;
import de.jose.profile.LayoutProfile;
import de.jose.util.*;
import de.jose.util.style.StyleUtil;
import de.jose.view.input.JoBigLabel;
import de.jose.view.input.JoButton;
import de.jose.view.input.JoStyledLabel;
import de.jose.view.input.WdlLabel;
import de.jose.view.style.JoStyleContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.io.IOException;

import static de.jose.plugin.EngineState.*;

public class EnginePanel
		extends JoPanel
		implements ClipboardOwner, MouseListener, DeferredMessageListener
{
	/**	the plugin engine	*/
	protected EnginePlugin    plugin;
	/**	engine display name */
	protected String    pluginName;
	/**	used to exchange information with the plugin	*/
	protected AnalysisRecord analysis;
	/** contains book info  */
	protected AnalysisRecord bookmoves = new AnalysisRecord();  //  TODO share with OpeningLirary ?
	public boolean inBook = false;
	private static long msgSeen=0;

	private Color bgColor;

	/** go button   */
	protected JButton   bGo;
	/** pause button    */
	protected JButton   bPause;
	/** anaylze button  */
	protected JButton   bAnalyze;
	/** hint button */
	protected JoButton 	bHint;
	/**	threat button */
	protected JButton 	bThreat;

	/** label for current move  */
	protected JLabel lCurrentMove, tCurrentMove;
	/** label for search depth  */
	protected JLabel lDepth, tDepth;
	/** label for elapsed time  */
	protected JLabel lElapsedTime, tElapsedTime;
	/** label for node count    */
	protected JLabel lNodeCount, tNodeCount;
	/** label for nodes per second  */
	protected JLabel lNodesPerSecond, tNodesPerSecond;
    /** pv history  */
    protected JTextArea tPVHistory;
    protected String pvHistLastLine;
    protected boolean showHistory;
	protected boolean showTooltips;

	protected JPanel infoPanel, pvPanel;
    protected JScrollPane pvScroller;

	/** pv evaluation
	 *  Vector<JLabel>
	 *   */
	protected ArrayList<WdlLabel> lEval;
	/** primary variation
	 *  Vector<JLabel>
	 *  0 = general info
	 *  1 = first pv, ...
	 * */
	protected ArrayList<JoStyledLabel> lPrimaryVariation;
	/** number of displayed primary variations  */
	protected int pvCount;
	protected boolean showInfoLabel;

	protected StyledMoveFormatter formatter;
	protected JoStyleContext styles;

	// engine mode when Hint is pressed
	protected EngineState hintMode;
	//	suggestion status before hint is pressed
	protected boolean hintSuggestions;

	/** status info    */
	protected JLabel    lStatus;

    protected static Icon[] iGoBlue, iGoGreen, iGoYellow, iGoRed, iGoOrange;
	protected static Icon[] iPause, iHint, iAnalyze, iBolt;
	protected static Icon iBook, iEngine;

    protected static final Color BACKGROUND_COLOR  = new Color(0xff,0xff,0xee);

	protected static final DecimalFormat NCOUNT_0 = new DecimalFormat("###0.#");
	protected static final DecimalFormat NCOUNT_K = new DecimalFormat("###0.# k");
	protected static final DecimalFormat NCOUNT_M = new DecimalFormat("####0.# M" );

	protected static final long TEN_MINUTES  = 10*60*1000L;

	protected static final SimpleDateFormat TIMEFORMAT_1    = new SimpleDateFormat("m:ss");
	protected static final SimpleDateFormat TIMEFORMAT_2    = new SimpleDateFormat("hh:m:ss");

    static Insets NO_INSETS = new Insets(0,0,0,0);

    static GridBagConstraints BUTTON_BOX_CONSTRAINTS = new GridBagConstraints(
                                    0, 0,  1,1, 0.0,1.0,GridBagConstraints.NORTHWEST,
                                    GridBagConstraints.BOTH, NO_INSETS, 0,0);
    static GridBagConstraints STATUS_LABEL_CONSTRAINTS = new GridBagConstraints(
                                    1, 0,  1,1, 1.0,1.0,GridBagConstraints.NORTHWEST,
                                    GridBagConstraints.BOTH, NO_INSETS, 0,0);

	 public EnginePanel(LayoutProfile profile, boolean withContextMenu, boolean withBorder)
	{
		super(profile,withContextMenu, withBorder);

		titlePriority = 6;

		boolean dark = Application.theApplication.isDarkLookAndFeel();

		setBgColor(dark);
		createIcons(dark);
		createComponents();
		createLayout();
	}

	private void setBgColor(boolean dark)
	{
		if (dark)
			bgColor = UIManager.getColor("background");
		else
			bgColor = BACKGROUND_COLOR;
	}

	private void applyBgColor()
	{
		this.setBackground(bgColor);
		infoPanel.setBackground(bgColor);

		tPVHistory.setBackground(bgColor);
		infoPanel.setBackground(bgColor);
		pvPanel.setBackground(bgColor);
		lStatus.setBackground(bgColor);

		lCurrentMove.setBackground(bgColor);
		tCurrentMove.setBackground(bgColor);

		lDepth.setBackground(bgColor);
		tDepth.setBackground(bgColor);

		lElapsedTime.setBackground(bgColor);
		tElapsedTime.setBackground(bgColor);

		lNodeCount.setBackground(bgColor);
		tNodeCount.setBackground(bgColor);

		lNodesPerSecond.setBackground(bgColor);
		tNodesPerSecond.setBackground(bgColor);

		for(int i=0; i < countPvLines(); i++) {
			getPvLabel(i,false,false).setBackground(bgColor);
			getEvalLabel(i,false,false).setBackground(bgColor);
		}
	}

	private static final Color GREY_INFO = Color.darkGray;
	private static final Color BLUE_LINK = new Color(0,0,196);

	private void setupStyles()
	{
		StringMoveFormatter.setDefaultLanguage(Application.theUserProfile.getFigurineLanguage());

		JoStyleContext userStyles = Application.theUserProfile.getStyleContext();
		//textStyle = styles.getStyle("body.line");
		styles = new JoStyleContext(userStyles);
		styles.setScreenResolution(72);
		//	not sure what that means, or why it is necessary
		//	but it scales correctly :\

		Style def = userStyles.getDefaultStyle();

		Style textStyle = styles.addStyle("engine.pv",def);
		StyleConstants.setFontFamily(textStyle, "sans-serif");
		StyleConstants.setFontSize(textStyle, 12);
		//StyleConstants.setLineSpacing(textStyle, -12.f);

		Style boldStyle = styles.addStyle("bold",def);
		StyleConstants.setBold(boldStyle, true);

		Style infoStyle = styles.addStyle("engine.pv.info",textStyle);
		StyleConstants.setFontSize(infoStyle, 10);
		StyleConstants.setForeground(infoStyle, GREY_INFO);

		Style linkStyle = styles.addStyle("engine.pv.link",textStyle);
		StyleConstants.setFontSize(linkStyle, 10);
		StyleConstants.setForeground(linkStyle, BLUE_LINK);
		StyleConstants.setUnderline(linkStyle, true);

		formatter = new StyledMoveFormatter();
		setupMoveFormatter(userStyles);
	}

	private void setupMoveFormatter(JoStyleContext userStyles)
	{
		Style textStyle = userStyles.getStyle("engine.pv");
		Style userFigStyle = userStyles.getStyle("body.figurine");
		String figFontName = StyleConstants.getFontFamily(userFigStyle);

		Style figStyle = styles.addStyle("engine.pv.figurine", textStyle);
		StyleConstants.setFontFamily(figStyle,figFontName);

		formatter.setTextStyle(textStyle);
		formatter.setPieceCharArray(
				StringMoveFormatter.getDefaultFormatter().getPieceCharArray());

		int moveFormat = Application.theUserProfile.getInt("doc.move.format", MoveFormatter.SHORT);
		formatter.setFormat(moveFormat);

		boolean useFigurines = userStyles.useFigurineFont();
		formatter.setFigStyle(useFigurines ? figStyle : null);
	}

	private void createIcons(boolean dark)
	{
		int iconSize=20;

		String green 	= "#009900";
		String yellow 	= "#cccc00";
		String red 		= "#b30000";
		String blue 	= "#0000d9";
		String orange 	= "#cc9900";

		//	todo think about frameless icons
		iGoGreen 	= JoToolBar.createAwesomeIcons("\uf04b:flat:"+green,iconSize,dark);
		iGoYellow 	= JoToolBar.createAwesomeIcons("\uf04b:flat:"+yellow,iconSize,dark);
		iGoRed 		= JoToolBar.createAwesomeIcons("\uf04b:flat:"+red,iconSize,dark);
		iGoBlue 	= JoToolBar.createAwesomeIcons("\uf04b:flat:"+blue,iconSize,dark);
		iGoOrange 	= JoToolBar.createAwesomeIcons("\uf04b:flat:"+orange,iconSize,dark);

		iPause 		= JoToolBar.createAwesomeIcons("\uf04c:flat:"+green,iconSize,dark);
		iHint 		= JoToolBar.createAwesomeIcons("?:flat:"+blue,iconSize,dark);
		iAnalyze 	= JoToolBar.createAwesomeIcons("\uf013:bold:flat:"+yellow,iconSize,dark);
		iBolt 		= JoToolBar.createAwesomeIcons("\ue0b7:bold:flat:"+orange,iconSize,dark);
	}

	private void createFontIcons()
	{
		iBook = FontUtil.awesomeIcon(BoardView2D.cBook,20,Color.lightGray);
		iEngine = FontUtil.awesomeIcon(BoardView2D.cGears,20,Color.lightGray);
	}

	private void createComponents()
	{
//		FontEncoding.assertFont("Arial");

		Font normalFont = new Font("SansSerif",Font.PLAIN,12);
		Font smallFont  = new Font("SansSerif",Font.PLAIN,10);

		bGo             = newButton("move.start");
		bPause          = newButton("engine.stop");
		bAnalyze        = newButton("menu.game.analysis");
		bHint           = new JoButton() {
			@Override
			public void mousePressed(MouseEvent e) { showHint(); }
			@Override
			public void mouseReleased(MouseEvent e) { hideHint(); }
		};
		newButton("menu.game.hint",bHint);
		bThreat			= newButton("menu.game.threat");

		setIcon(bGo, iGoBlue);
		setIcon(bPause,iPause);
		setIcon(bHint,iHint);
		setIcon(bAnalyze,iAnalyze);
		setIcon(bThreat,iBolt);

		int tborder = JoLineBorder.LEFT+JoLineBorder.RIGHT+JoLineBorder.TOP;
		int lborder = JoLineBorder.LEFT+JoLineBorder.RIGHT+JoLineBorder.BOTTOM;

		lCurrentMove    = newLabel("plugin.currentmove",normalFont,JLabel.LEFT,lborder);
		lDepth          = newLabel("plugin.depth",normalFont,JLabel.CENTER,lborder);
		lElapsedTime    = newLabel("plugin.elapsed.time",normalFont,JLabel.RIGHT,lborder);
		lNodeCount      = newLabel("plugin.nodecount",normalFont,JLabel.RIGHT,lborder);
		lNodesPerSecond = newLabel("plugin.nps",normalFont,JLabel.RIGHT,lborder);
		lStatus         = new JLabel() {
			public JToolTip createToolTip()
			{
				return new EngineToolTip(EnginePanel.this);
			}
		};
		newLabel(lStatus,"plugin.info",normalFont,JLabel.LEFT,JoLineBorder.ALL);

		lStatus.setFont(normalFont);

		tCurrentMove    = newLabel("plugin.currentmove.title",smallFont,JLabel.CENTER,tborder,false);
		tDepth          = newLabel("plugin.depth.title",smallFont,JLabel.CENTER,tborder,false);
		tElapsedTime    = newLabel("plugin.elapsed.time.title",smallFont,JLabel.CENTER,tborder,false);
		tNodeCount      = newLabel("plugin.nodecount.title",smallFont,JLabel.CENTER,tborder,false);
		tNodesPerSecond = newLabel("plugin.nps.title",smallFont,JLabel.CENTER,tborder,false);

		tCurrentMove.setLabelFor(lCurrentMove);
		tDepth.setLabelFor(lDepth);
		tElapsedTime.setLabelFor(lElapsedTime);
		tNodeCount.setLabelFor(lNodeCount);
		tNodesPerSecond.setLabelFor(lNodesPerSecond);

		lCurrentMove.setLabelFor(tCurrentMove);
		lDepth.setLabelFor(tDepth);
		lElapsedTime.setLabelFor(tElapsedTime);
		lNodeCount.setLabelFor(tNodeCount);
		lNodesPerSecond.setLabelFor(tNodesPerSecond);

		lEval = new ArrayList<>();  //  will be filled on demand
		lPrimaryVariation = new ArrayList<>(); //   will be filled on demand
		pvCount = 0;

        tPVHistory = new JTextArea();
        tPVHistory.setName("plugin.pv.history");
        tPVHistory.setFont(normalFont);
        tPVHistory.setBorder(new JoLineBorder(tborder, 1, 0,4,0,4));
        tPVHistory.setBackground(bgColor);
        tPVHistory.setOpaque(true);
        tPVHistory.setWrapStyleWord(false);

		setShowInfoLabel(false);
		/** will be create later    */
	}

	private WdlLabel createPvEvalComponent(String name)
	{
		Font normalFont = new Font("SansSerif",Font.PLAIN,12);
		WdlLabel label = new WdlLabel(""/*Language.get(name)*/,1,4);
		makeLabel(label, name,normalFont,JLabel.LEFT,
				JoLineBorder.ALL, 3,3,3,3);
		return label;
	}

	private JoStyledLabel createPvLineComponent(String name)
	{
		Font normalFont = new Font("SansSerif",Font.PLAIN,12);

		//JoStyleContext styles = Application.theUserProfile.getStyleContext();
		StyledDocument sdoc = new DefaultStyledDocument(this.styles);
		JoStyledLabel label = new JoStyledLabel(""/*Language.get(name)*/, sdoc);

		makeLabel(label, name,normalFont,JLabel.LEFT,
                                    JoLineBorder.ALL, 3,3,3,3);
		label.addActionListener(this); // click Lichess top game
		return label;
	}


	private void createLayout()
	{
//		setLayout(new GridBagLayout());
		setLayout(new TopDownLayout());

		/** layout buttons  */
		Box buttonBox = Box.createHorizontalBox();
		buttonBox.setBorder(new EmptyBorder(4,4,4,4));
		buttonBox.add(bGo);
		buttonBox.add(Box.createHorizontalStrut(4));
		buttonBox.add(bPause);
		buttonBox.add(Box.createHorizontalStrut(4));
		buttonBox.add(bAnalyze);
		buttonBox.add(Box.createHorizontalStrut(12));
		buttonBox.add(bHint);
		buttonBox.add(Box.createHorizontalStrut(4));
		buttonBox.add(bThreat);
        buttonBox.add(Box.createHorizontalStrut(12));

		JPanel buttonPanel = new JPanel(new GridBagLayout());
		buttonPanel.add(buttonBox, BUTTON_BOX_CONSTRAINTS);
		buttonPanel.add(lStatus, STATUS_LABEL_CONSTRAINTS);

		/** layout info area    */
		infoPanel = new JPanel(new GridLayout(2,5));
		infoPanel.setBackground(bgColor);

        infoPanel.add(tCurrentMove);
        infoPanel.add(tDepth);
        infoPanel.add(tElapsedTime);
        infoPanel.add(tNodeCount);
        infoPanel.add(tNodesPerSecond);

        infoPanel.add(lCurrentMove);
        infoPanel.add(lDepth);
        infoPanel.add(lElapsedTime);
        infoPanel.add(lNodeCount);
        infoPanel.add(lNodesPerSecond);

		/** more components will be added later */
		/** layout pv area  */
		pvPanel = new JPanel(new EnginePanelLayout(this));
		pvPanel.setBackground(bgColor);
        pvPanel.add(tPVHistory);
		/** more components will be added later, when the number of PVs is known */

		pvScroller = new JScrollPane(pvPanel,
		        JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
		        JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		pvScroller.getVerticalScrollBar().setUnitIncrement(20);
//		pvScroller.getVerticalScrollBar().setMaximumSize(new Dimension(12,32768));
//		pvScroller.getHorizontalScrollBar().setMaximumSize(new Dimension(32768,12));

		/** layout  */
		add(buttonPanel);
		add(infoPanel);
		add(pvScroller);
	}

	private void adjustCapabilities(int cap)
	{
		boolean moveVisible = Util.anyOf(cap, AnalysisRecord.CURRENT_MOVE +
		                                    AnalysisRecord.CURRENT_MOVE_NO);
		boolean depthVisible = Util.anyOf(cap, AnalysisRecord.DEPTH+AnalysisRecord.SELECTIVE_DEPTH);
		boolean timeVisible = Util.anyOf(cap, AnalysisRecord.ELAPSED_TIME);
		boolean nodecountVisible = Util.anyOf(cap, AnalysisRecord.NODE_COUNT);
		boolean npsVisible = Util.anyOf(cap, AnalysisRecord.NODES_PER_SECOND);

		/** clear info panel; components will be re-inserted on demand
		 * */
		pvPanel.removeAll();
        pvPanel.add(tPVHistory);
		pvCount = 0;
		showInfoLabel = false;
		pvPanel.revalidate();

		tCurrentMove.setForeground(moveVisible ? Color.black:Color.lightGray);
		tDepth.setForeground(depthVisible ? Color.black:Color.lightGray);
		tElapsedTime.setForeground(timeVisible ? Color.black:Color.lightGray);
		tNodeCount.setForeground(nodecountVisible ? Color.black:Color.lightGray);
		tNodesPerSecond.setForeground(npsVisible ? Color.black:Color.lightGray);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		 if (e.getActionCommand().equals("clicked"))
		 {
			 Object source = e.getSource();
			 if (source instanceof GameRef) {
				 LiChessGameRef ref = (LiChessGameRef) source;
                 LiChessOpeningExplorer.startDownload(ref);
			 }
		 }
		 else {
			 super.actionPerformed(e);
		 }
	}

	protected int countPvLines()
	{
		return pvCount;
	}

	protected boolean showInfoLabel()
	{
		return showInfoLabel;
	}

	protected void setShowInfoLabel(boolean on)
	{
		if (on != showInfoLabel) {
			showInfoLabel = on;
			pvPanel.revalidate();
		}
	}

	public EnginePlugin getPlugin()
	{
		return plugin;
	}

	public UciPlugin getUciPlugin() {
		EnginePlugin plugin = getPlugin();
		if (plugin instanceof UciPlugin)
			return (UciPlugin)plugin;
		else
			return null;
	}

	protected JoStyledLabel getInfoLabel(boolean create)
	{
		JoStyledLabel info = getDynamicLineLabel(0, create, true, "plugin.info");
		if (info!=null && !info.isShowing()) {
//			info.setBackground(Color.lightGray);
			info.setVisible(true);
			pvPanel.add(info);
			pvPanel.revalidate();
		}
		return info;
	}

	protected JoStyledLabel getPvLabel(int idx, boolean create, boolean show)
	{
		JoStyledLabel pv = getDynamicLineLabel( idx+1, create, show, "plugin.pv."+(idx+1));
		if (pv!=null && show && !pv.isShowing())
		synchronized (this) {
			WdlLabel eval = getDynamicEvalLabel( idx+1, true, show, "plugin.eval."+(idx+1));
			pv.setVisible(true);
			showTooltip(idx);
			pv.setToolTipText("?");
			eval.setVisible(true);
			pvPanel.add(pv);
			pvPanel.add(eval);  //  TODO think about using constraints to help EnginePanelLayout
			pvPanel.revalidate();
		}
		return pv;
	}

	public int findPly(JTextComponent label, Point at)
	{
		//	todo map to text position; count spaces and line breaks
		int i = label.viewToModel(at);
		int spaces = StringUtil.countWhitespace(label.getText(),0,i);
		return spaces+1;
	}

	protected WdlLabel getEvalLabel(int idx, boolean create, boolean show)
	{
		WdlLabel eval = getDynamicEvalLabel( idx+1, create, show, "plugin.eval."+(idx+1));
		if (eval!=null && show && !eval.isShowing())
		synchronized (this) {
			JoStyledLabel pv = getDynamicLineLabel( idx+1, true, show, "plugin.pv."+(idx+1));
			pv.setVisible(true);
			showTooltip(idx);
			pv.setToolTipText("?");
			eval.setVisible(true);
			pvPanel.add(pv);
			pvPanel.add(eval);  //  TODO think about using constraints to help EnginePanelLayout
			pvPanel.revalidate();
		}
		return eval;
	}

	private WdlLabel getDynamicEvalLabel(int vidx, boolean create, boolean show, String name)
	{
		ArrayList<WdlLabel> v = lEval;
		if (vidx >= v.size() && !create)
			return null;

		while (vidx >= v.size()) v.add(null);

		WdlLabel result = (WdlLabel)v.get(vidx);
		if (result==null && create)
		{
			//  create new label
			result = createPvEvalComponent(name);
			v.set(vidx,result);
		}
		if (result!=null && show) {
			pvCount = Math.max(pvCount,vidx);
			result.setVisible(!showHistory);
		}
		return result;
	}

	private JoStyledLabel getDynamicLineLabel(int vidx, boolean create, boolean show, String name)
	{
		ArrayList<JoStyledLabel> v = lPrimaryVariation;
		if (vidx >= v.size() && !create)
			return null;

		while (vidx >= v.size()) v.add(null);

		JoStyledLabel result = v.get(vidx);
		if (result==null && create)
		{
			//  create new label
			result = createPvLineComponent(name);
			v.set(vidx,result);
		}
        if (result!=null && show) {
	        pvCount = Math.max(pvCount,vidx);
	        result.setVisible(!showHistory);
        }
		return result;
	}

	public String getPvText(int idx)
	{
		JoStyledLabel pvlabel = getPvLabel(idx,false, false);
		if (pvlabel==null) return null; //  no PV

		String line;
		if (formatter!=null && formatter.getFigStyle()!=null) {
			String[] wasPieceChars = formatter.getPieceCharArray();
			if (!MoveFormatter.isAnsiChars(wasPieceChars)) //	can't handle multi-byte pieces; or Unicode. fallback to English
				formatter.setLanguage("en");
			line = formatter.reformat(pvlabel.getStyledDocument());
			formatter.setPieceCharArray(wasPieceChars);
		}
		else {
			line = pvlabel.getText();

			StringMoveFormatter defaultFormatter = StringMoveFormatter.getDefaultFormatter();
			String[] pieceChars = defaultFormatter.getPieceCharArray();	//	original language from UCI
			if (!MoveFormatter.isAnsiChars(pieceChars))
				line = defaultFormatter.reformat(line,"en");	//	translate to English
		}
		if (line==null || StringUtil.isWhitespace(line)) return null;   //  no PV

		JoBigLabel elabel = getEvalLabel(idx,false, false);
		String eval = (elabel!=null) ? elabel.getText() : null;

		StringBuffer buf = new StringBuffer();

		if (! inBook && analysis!=null)
			switch (analysis.engineMode) {
			case ANALYZING:
			case THINKING:
					break;

			case PONDERING:
					if (analysis.ponderMove!=null) {
						buf.append(analysis.ponderMove);
						//buf.append(" {ponder move} ");
						buf.append(" ");
					}
					else
						buf.append("...");
					break;
			}

		buf.append(line);

		if (! inBook && eval!=null) {
			//  evaluation
			buf.append(" {");
			buf.append(pluginName);
			buf.append(": ");
			buf.append(eval);
			//  search depth
			buf.append(". ");
			buf.append(tDepth.getText());
			buf.append(": ");
			buf.append(lDepth.getText());
			//  node count
			buf.append(". ");
			buf.append(tNodeCount.getText());
			buf.append(": ");
			buf.append(lNodeCount.getText());
			buf.append("}");
		}

		StringUtil.trim(buf,StringUtil.TRIM_BOTH);
		if (buf.length()==0)
			return null;
		else
			return buf.toString();
	}

	private Move getHintMove()
	{
		int cply = Application.theApplication.theGame.getCurrentMove().getPly();
		if (analysis!=null
				&& analysis.ponderMove!=null
				&& analysis.ponderMove.ply == cply+1)
			return analysis.ponderMove;
		if (inBook && bookmoves!=null
				&& bookmoves.data[0].moves!=null
				&& !bookmoves.data[0].moves.isEmpty()
				&& this.pvCount > 0
				&& bookmoves.ply == cply+1)
			return bookmoves.data[0].moves.get(0);
		if (!inBook && analysis!=null
				&& analysis.data[0].moves!=null
				&& !analysis.data[0].moves.isEmpty()
				&& this.pvCount > 0
				&& analysis.ply == cply+1)
			return analysis.data[0].moves.get(0);
		return null;
	}

	private void showHint()
	{
		BoardPanel bpanel = Application.theApplication.boardPanel();
		if (bpanel==null) return;

		Move mv = getHintMove();
		if (mv!=null) {
			bpanel.showHint(mv);
			return;
		}
		// else: switch on suggestions arrows temporarily
		hintMode = null;
		if (plugin==null) {
			//	launch analysis
			Command cmd = new Command("menu.game.analysis");
			Application.theCommandDispatcher.forward(cmd,this);
			return;
		}

		switch(hintMode = plugin.getMode())
		{
			case THINKING:
			case PONDERING:
				//	no hints while engine is thinking (and no ponderMove stored)
				return;
			case ANALYZING:
				//	turn on suggestions
				hintSuggestions = bpanel.getView().showSuggestions(true);
				return;
			case PAUSED:
				//	turn on analyzing
				hintSuggestions = bpanel.getView().showSuggestions(true);
				plugin.analyze(plugin.applPosition);
				//	turn on suggestions temporarily
				return;
		}
	}

	private void hideHint()
	{
		BoardPanel bpanel = Application.theApplication.boardPanel();
		if (bpanel==null) return;

		if (hintMode!=null) switch (hintMode)
		{
			case ANALYZING:
				bpanel.getView().showSuggestions(hintSuggestions);
				break;
			case PAUSED:
				bpanel.getView().showSuggestions(hintSuggestions);
				plugin.pause();
				break;
		}
		hintMode = THINKING;
	}

	protected JButton newButton(String command, JButton button)
	{
		button.setName(command);
		button.setActionCommand(command);
		button.addActionListener(this);
//		button.setIcon(ImgUtil.getMenuIcon(command));
//		button.setSelectedIcon(ImgUtil.getMenuIcon(command,true));
//		button.setText(null);		//	no text
//		button.setMargin(INSETS_MARGIN);
		button.setToolTipText(Language.getTip(command));
		button.setFocusable(false); //  don't steal keyboard focus from game panel
//		button.setBorder(null);
		button.setBorderPainted(true);
		button.setContentAreaFilled(true);
		button.setRolloverEnabled(true);
		button.putClientProperty("JButton.buttonType","roundRect");
		button.putClientProperty("Button.arc",999);
//		button.putClientProperty("Button.focusWidth",12);
//		button.setMargin(new Insets(8, 8, 8, 8));
		return button;
	}

	protected JButton newButton(String command)
	{
		JButton button = new JButton();
		return newButton(command,button);
	}

    protected JLabel newLabel(String name, Font font, int aligment, int border, boolean withToolTip)
    {
        JLabel result = newLabel(name,font,aligment,border);
	    if (!withToolTip) result.setToolTipText(null);
	    return result;
    }

	protected JLabel newLabel(String name, Font font, int aligment, int border)
	{
		return newLabel(name,font,aligment,border, 0,4,0,4);
	}

	protected JLabel newLabel(JLabel label, String name, Font font, int aligment, int border)
	{
	    return newLabel(label,name,font,aligment,border, 0,4,0,4);
	}


	protected JLabel newLabel(String name, Font font, int aligment,
	                          int border,
	                          int paddingTop, int paddingLeft, int paddingBottom, int paddingRight)
	{
		JLabel label = new JLabel();
		newLabel(label,name,font,aligment,border,paddingTop,paddingLeft,paddingBottom,paddingRight);
		return label;
	}

	protected JLabel newLabel(JLabel label, String name, Font font, int aligment,
	                          int border,
	                          int paddingTop, int paddingLeft, int paddingBottom, int paddingRight)
	{
		label.setName(name);
		label.setText(Language.get(name));		//	no text
		label.setToolTipText(Language.getTip(name));
		label.setFont(font);
		label.setHorizontalAlignment(aligment);
		label.setBorder(new JoLineBorder(border, 1,
                        paddingTop,paddingLeft,paddingBottom,paddingRight));
		label.setBackground(bgColor);
		label.setOpaque(true);

		label.setMinimumSize(new Dimension(12,16));
		label.setPreferredSize(new Dimension(48,16));
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE,16));

		return label;
	}

	protected void makeLabel(JTextComponent label,
			String name, Font font, int aligment,
			int border,
			int paddingTop, int paddingLeft, int paddingBottom, int paddingRight)
	{
//		JTextComponent label = new JoStyledLabel(Language.get(name));
		label.setName(name);
//		label.setText(Language.get(name));
//		label.setToolTipText(Language.getTip(name));
		label.setFont(font);
//		label.setHorizontalAlignment(aligment);
		label.setBorder(new JoLineBorder(border, 1,
                        paddingTop,paddingLeft,paddingBottom,paddingRight));
		label.setBackground(bgColor);
		label.setOpaque(true);

		label.setMinimumSize(new Dimension(12,16));
		label.setPreferredSize(new Dimension(48,16));
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE,48));

		label.setToolTipText(null);
		label.addMouseListener(this);
	}

	protected void broadcastAnalysis(int engineMode, AnalysisRecord a)
	{
		Command cmd = new Command("move.values", null, engineMode, a);
		Application.theCommandDispatcher.broadcast(cmd, Application.theApplication);
	}

	protected void broadcastMoveValue(int ply, Score score)
	{
		score = new Score(score);
		Command cmd = new Command("move.value", null, score, ply);
		Game game = Application.theApplication.theGame;
		if (game!=null) {
			MoveNode mvnd = game.getCurrentMove();
			if (mvnd!=null) {
				if (!score.equals(mvnd.engineValue)) {
					mvnd.engineValue = new Score(score);
					game.setDirty(true);
				}
				cmd.moreData = mvnd;
			}
		}

		Application.theCommandDispatcher.broadcast(cmd, Application.theApplication);
	}

	protected void updateButtonState()
	{
		updateButtonState(
				Application.theApplication.theMode, Application.theApplication.thePlayState, null);
	}

	protected void updateButtonState(
			Application.AppMode appState,
			Application.PlayState playState,
			EngineState engineState)
	{
		if (engineState==null && plugin!=null)
			engineState = plugin.getMode();

		setIcon(bGo,getGoIcon(appState,playState,engineState));
		bGo.setEnabled(true);
		bPause.setEnabled(engineState!=null && engineState != PAUSED);

		Game game = Application.theApplication.theGame;
		Position pos = game.getPosition();
		bAnalyze.setEnabled((plugin==null) || plugin.canAnalyze(pos));
		//	no hint while engine is thinking
		bHint.setEnabled(playState==null || playState==Application.PlayState.NEUTRAL);
		//	no null move in check positions
		bThreat.setEnabled(game.canInsertNullMove());
	}

	/**
	 * @param state
	 * @param rec
	 * @param bookMode
	 */
	protected void display(EngineState state, AnalysisRecord rec, boolean bookMode)
	{
		//	todo synchronized(rec) ? probably yes, because the engine might write into it
		updateButtonState(Application.theApplication.theMode,null,state);

		//  book mode/ engine mode layout
		infoPanel.setVisible(! bookMode);

		boolean infoModified = false;
		HashMap pmap = new HashMap();
		if (rec!=null) {
			if (rec.wasModified(AnalysisRecord.CURRENT_MOVE|AnalysisRecord.CURRENT_MOVE_NO))
				setCurrentMove(rec.currentMove,rec.currentMoveNo,plugin.countLegalMoves(),pmap);
			if (rec.wasModified(AnalysisRecord.DEPTH|AnalysisRecord.SELECTIVE_DEPTH))
				setDepth(rec.depth,rec.selectiveDepth,pmap);
			if (rec.wasModified(AnalysisRecord.ELAPSED_TIME))
				setElapsedTime(rec.elapsedTime,pmap);
			if (rec.wasModified(AnalysisRecord.NODE_COUNT))
				setNodeCount(rec.nodes,pmap);
			if (rec.wasModified(AnalysisRecord.NODES_PER_SECOND))
				setNodesPerSecond(rec.nodesPerSecond,pmap);

            boolean scrollhist = false;
			for (int idx=0; idx < rec.maxpv; idx++)
				if (rec.wasPvModified(idx)) {
					AnalysisRecord.LineData data = rec.data[idx];
					assert(data.eval!=null);
					assert(data.line!=null);
					if (state==PONDERING && rec.ponderMove!=null) {
						data.line.insert(0," ");
						data.line.insert(0,rec.ponderMove.text);
					}

					setEvaluation(idx, data.eval);
					setVariation(idx, data);

					if (!data.eval.hasWDL() && !bookMode && (plugin!=null))
						plugin.mapUnit(data.eval);	// todo why has this not been done before?

					if (idx==0)
						broadcastMoveValue(rec.ply, data.eval);

					if (! inBook) {
						if (countPvLines() > 1)
							scrollhist = appendHist("["+(idx+1)+"] "+getEvalLabel(idx,false,false).getText()+" "+ data.line.toString());
						else
							scrollhist = appendHist(getEvalLabel(idx,false,false).getText()+" "+ data.line.toString());
					}
				}

			setVariationCount(rec.maxpv);

			if (rec.wasModified(AnalysisRecord.INFO)) {
				//  show info
				scrollhist = showInfo(rec.info);
				infoModified = true;
			}

			//	we have acknowledged the modifications
			rec.modified = 0;
			rec.clearPvModified();

            //if (scrollhist)
            //    AWTUtil.scrollDown(pvScroller,pvPanel);
			//	don't. it's confusing most of the time.
			//	scrolling to top is less confusing (but not perfect, also :(
			AWTUtil.scrollUp(pvScroller,pvPanel);
		}
		else {
			//  clear all
			setCurrentMove(null,Score.UNKNOWN,Score.UNKNOWN,pmap);
			setDepth(Score.UNKNOWN,Score.UNKNOWN,pmap);
			setElapsedTime(Score.UNKNOWN,pmap);
			setNodeCount(Score.UNKNOWN,pmap);
			setNodesPerSecond(Score.UNKNOWN,pmap);

			for (int idx=0; idx < pvCount; idx++)
			{
				setEvaluation(idx,new Score());
				setVariation(idx,null);
			}

            tPVHistory.setText("");
            pvHistLastLine = null;
		}

		if (bookMode) {
			updateStatus(null);
			//  always scroll to the *top* of the list
			AWTUtil.scrollUp(pvScroller,pvPanel);
		}
		else if (!infoModified)
			updateStatus(state);
	}

	public void exitBook()
	{
		if (inBook) {
			display(null,null, inBook);    //  TODO which state ?
			showLines(1, false);
			pvCount = 1;
		}
		inBook = false;
	}

	public void showBook(List bookEntries, Position pos)
	{
		if (!inBook) {

		}

		bookmoves.reset();
		bookmoves.ply = pos.ply();

		for (int i=0; i < bookEntries.size(); i++)
		{
			BookEntry entry = (BookEntry)bookEntries.get(i);
			bookmoves.setPvModified(i);
			bookmoves.data[i].book = entry;

			StringBuffer line = bookmoves.getLine(i);
			line.append(StringMoveFormatter.formatMove(pos,entry.move,true));

			//  show eco code, if available
			ECOClassificator eco = Application.theApplication.getClassificator();
			int code = eco.lookup(pos,entry.move);
			if (code!=ECOClassificator.NOT_FOUND)
			{
				line.append("  {");
				line.append(eco.getEcoCode(code,3));
				line.append(" ");
				line.append(eco.getOpeningName(code));
				line.append("}");
			}

			AnalysisRecord.LineData data = bookmoves.data[i];
			if (data.moves==null)
				data.moves = new ArrayList();
			data.moves.clear();
			data.moves.add(entry.move);	//	useful for tooltips

			Score score = data.eval;
			entry.toScore(score,1000);
		}

		//  always show hint that these are book moves
//		bookmoves.info = Language.get("book.title");
//		bookmoves.modified |= AnalysisRecord.INFO;

		showLines(bookEntries.size(), false);
		pvCount = bookEntries.size();

		inBook = true;
		display(null,bookmoves, inBook);
	}

	public Score findScore(Move mv)
	{
		if (inBook && bookmoves!=null)
			return bookmoves.findScore(mv);
		if(!inBook && analysis!=null)
			return analysis.findScore(mv);
		return null;
	}


	protected boolean showInfo(String info)
	{
		boolean scrollhist;
		getInfoLabel(true).setText(info);
		setShowInfoLabel(true);
		scrollhist = appendHist(info);
		return scrollhist;
	}

	private boolean appendHist(String text)
	{
	    if (text==null) return false;
	    text = text.trim();
	    if (StringUtil.isWhitespace(text) || Util.equals(text,pvHistLastLine))
	        return false;
	    //  else {
	    tPVHistory.append(text);
	    tPVHistory.append("\n");
	    pvHistLastLine = text;
	    return true;
	}


	public void setCurrentMove(String move, int count, int max, HashMap pmap)
	{
		String key = "plugin.currentmove";
		if (count > 0) {
			key = "plugin.currentmove.max";
			pmap.put("moveno",String.valueOf(count));
			pmap.put("maxmove",String.valueOf(max));
		}

		pmap.put("move",move);
		setValue(lCurrentMove,key,pmap);
	}

	public void setDepth(int dep, int selectiveDep, HashMap pmap)
	{
        if (dep <= Score.UNKNOWN) {
            lDepth.setText("");
            return;
        }

		String key;
		if (dep < 0) {
			switch (dep) {
			case AnalysisRecord.BOOK_MOVE:
					key = "plugin.book.move"; break;
			case AnalysisRecord.HASH_TABLE:
					key = "plugin.hash.move"; break;
			case AnalysisRecord.ENDGAME_TABLE:
					key = "plugin.tb.move"; break;
			default:
					key = null; break;
			}
		}
		else {
			pmap.put("depth", String.valueOf(dep));
			if (selectiveDep > 0) {
				pmap.put("seldepth", String.valueOf(selectiveDep));
				key = "plugin.depth.sel";
			}
			else
				key = "plugin.depth";
		}

		setValue(lDepth,key,pmap);
	}

	public void setElapsedTime(long millis, HashMap ignored)
	{
        if (millis < 0) {
            lElapsedTime.setText("");
            return;
        }

		SimpleDateFormat format;
		if (millis < TEN_MINUTES)
			format = TIMEFORMAT_1;
		else
			format = TIMEFORMAT_2;
		String text = format.format(new Date(millis));

		if (!text.equals(lElapsedTime.getText()))
			lElapsedTime.setText(text);
	}

	protected static void setValue(JLabel value, String key, HashMap pmap)
	{
		String text = Language.args(key,pmap);
		String tip = Language.argsTip(key,pmap);
		value.setText(text);
		value.setToolTipText(tip);
	}


	void setVariationCount(int max)
	{
		while(pvCount > max)
		{
			--pvCount;
			WdlLabel l1 = getEvalLabel(pvCount,false,false);
			JoStyledLabel l2 = getPvLabel(pvCount,false,false);
			if (l1!=null) l1.setVisible(false);
			if (l2!=null) l2.setVisible(false);
		}
	}

	/**
	 * @param idx
	 * @param score (from whites point of view)
	 *
	 * todo use Score object with WDL info
	 */
	public void setEvaluation(int idx, Score score)
	{
		WdlLabel leval = getEvalLabel(idx, (score.cp > Score.UNKNOWN) || score.hasWDL(), true);
		if (leval==null) return;

		String text = EnginePlugin.printScore(score, plugin,false,true);
		String tooltip = EnginePlugin.printScoreTooltip(score, plugin,true);
		/* note: even though the methods are located at EnginePlugin, they work just as well for opening books */

		leval.setText(text);
		leval.setToolTipText(tooltip);

		if (score!=null && score.hasWDL())
			leval.setWdlScore(score);
		else
			leval.setWdlScore(null);
	}

	public void setNodeCount(long nodes, HashMap pmap)
	{
		String key;
		if (nodes < 0)
			key = null;
		else {
			if (nodes < 1000)
				pmap.put("nodecount", NCOUNT_0.format(nodes));
			else if (nodes < 1000000)
				pmap.put("nodecount", NCOUNT_K.format((double)nodes/1000));
			else
				pmap.put("nodecount", NCOUNT_M.format((double)nodes/1000000));

			key = "plugin.nodecount";
		}
		setValue(lNodeCount,key,pmap);
	}

	public void setNodesPerSecond(long nodes, HashMap pmap)
	{
		String key;
		if (nodes < 0)
			key = null;
		else {
			if (nodes < 1000)
				pmap.put("nps", NCOUNT_0.format(nodes));
			else if (nodes < 1000000)
				pmap.put("nps", NCOUNT_K.format((double)nodes/1000));
			else
				pmap.put("nps", NCOUNT_M.format((double)nodes/1000000));

			key = "plugin.nps";
		}
		setValue(lNodesPerSecond,key,pmap);
	}

	public void setVariation(int idx, AnalysisRecord.LineData rec)
	{
		JoStyledLabel lvar = getPvLabel(idx, (rec!=null), true);
		if (lvar!=null)
			setLine(lvar,rec);
	}

	public void setInfo(StringBuffer text)
	{
		JTextComponent linfo = getInfoLabel(text!=null);
		if (linfo!=null) {
			linfo.setText(text.toString());
			setShowInfoLabel(true);
		}
	}

	private void setLine(JoStyledLabel label, AnalysisRecord.LineData rec)
	{
		StringBuffer text = (rec!=null) ? rec.line:null;
		StringBuffer info = (rec!=null) ? rec.info:null;
		BookEntry book = (rec!=null) ? rec.book:null;

		StyledDocument doc = label.getStyledDocument();
		Style textStyle = doc.getStyle("engine.pv");
		Style infoStyle = doc.getStyle("engine.pv.info");
		Style boldStyle = doc.getStyle("bold");
        try {
            doc.remove(0,doc.getLength());
			if (text!=null && text.length()>0) {
				if (formatter.getFigStyle()!=null) {
					//formatter.setLanguage();
					String[] pieceChars = StringMoveFormatter.getDefaultFormatter().getPieceCharArray();
					//	this one was used by UCI engine to print the moves
					formatter.setPieceCharArray(pieceChars);
					formatter.setDocument(doc,doc.getLength());
					formatter.reformatFrom(text);
				}
				else {
					doc.insertString(doc.getLength(), text.toString(), textStyle);
				}
				int i1 = text.indexOf(" ");
				if (i1 < 0) i1 = text.length();
				//	first word is bold (todo except for ponder move?)
				doc.setCharacterAttributes(0,i1, boldStyle, false);
			}
			if (book != null && book.gameRefs!=null && !book.gameRefs.isEmpty()) {
				insertGameRefs(doc,book.gameRefs);
			}
			if (info!=null && info.length()>0) {
				doc.insertString(doc.getLength(), "\n", textStyle);
				doc.insertString(doc.getLength(), info.toString(), infoStyle);
			}
        } catch (BadLocationException e) {
            Application.error(e);
        }
	}

	protected void insertGameRefs(StyledDocument doc, ArrayList<GameRef> refs) throws BadLocationException
	{
		Style infoStyle = doc.getStyle("engine.pv.info");
		Style linkStyle = doc.getStyle("engine.pv.link");
		doc.insertString(doc.getLength()," {", infoStyle);

		for (int j=0; j < refs.size(); ++j) {
			if (j>0) doc.insertString(doc.getLength(), ", ", infoStyle);
			int pos = doc.getLength();
			GameRef ref = refs.get(j);
			String str = ref.toString(true);
			doc.insertString(pos, str, linkStyle);
			JoStyledLabel.setClickable(doc,pos,str.length(),ref);
		}
		doc.insertString(doc.getLength(),"}",infoStyle);
	}

	protected void setIcon(JButton button, Icon[] icon)
	{
		button.setDisabledIcon(icon[0]);
		button.setIcon(icon[1]);
		button.setRolloverIcon(icon[2]);
		button.setPressedIcon(icon[3]);
		int hmargin = 26-icon[1].getIconWidth();
		int vmargin = 26-icon[1].getIconHeight();
		button.setMargin(new Insets(vmargin/2, hmargin/2, vmargin/2, hmargin/2));
	}

	protected Icon[] getGoIcon(Application.AppMode appState,
							   Application.PlayState playState,
							   EngineState engineState)
	{
		if (engineState!=null) switch (engineState) {
		case THINKING:	    return iGoRed;
		case PONDERING:		return iGoGreen;
		}

		if (playState!=null) switch(playState) {
		case BOOK:
		case ENGINE:		return iGoRed;
		}

		if (appState!=null) switch(appState) {
		case USER_INPUT:	return iGoYellow;
		case ANALYSIS:		return iGoOrange;
		}

		if (engineState!=null) switch (engineState) {
		case PAUSED:		return iGoBlue;
		}

		return iGoYellow;
	}

	protected String getGoToolTip(EngineState state)
	{
		String result = null;
		switch (state) {
		default:
		case PAUSED:	result = "engine.paused.tip"; break;
		case THINKING:	result = "engine.thinking.tip"; break;
		case PONDERING:	result = "engine.pondering.tip"; break;
		case ANALYZING:	result = "engine.analyzing.tip"; break;
		}
		result = Language.get(result);
		result = StringUtil.replace(result,"%engine%", (pluginName==null) ? "":pluginName);
		return result;
	}

	protected void updateStatus(EngineState state)
	{
		String text = null;
		Icon icon = null;
		if (state==null) {
			//text = "book.title";
			//text = Language.get(text);
			//text = StringUtil.replace(text,"%book%", Application.theApplication.theOpeningLibrary.getTitle());
			text = Application.theApplication.theOpeningLibrary.getTitle();
			icon = iBook;
		}
		else {
			switch (state) {
				default:
				case PAUSED:	text = "engine.paused.title"; break;
				case THINKING:	text = "engine.thinking.title"; break;
				case PONDERING:	text = "engine.pondering.title"; break;
				case ANALYZING:	text = "engine.analyzing.title"; break;
			}
			text = Language.get(text);
			text = StringUtil.replace(text,"%engine%", (pluginName==null) ? "":pluginName);
			icon = iEngine;
		}
		lStatus.setIcon(icon);
		lStatus.setText(text);
	}

	public void init()
	{
		createFontIcons();

		display(PAUSED,null, inBook);
		setOpaque(true);
		setFocusable(false);    //  don't request keyboard focus (or should we ?)

		showHistory = Application.theUserProfile.getBoolean("plugin.pv.history");
		showTooltips = Application.theUserProfile.getBoolean("plugin.pv.tooltips");

		setupStyles();

		if (Application.theApplication.getEnginePlugin() != null)
			connectTo(Application.theApplication.getEnginePlugin());

		//Application.theApplication.updateBook(false,false);   //  well be called by "switch.game", finally
	}

	protected void connectTo(EnginePlugin plugin)
	{
		this.plugin = plugin;
		plugin.addMessageListener(this);

		String name = plugin.getDisplayName(null);
		String author = plugin.getAuthor();

		Map placeholders = new HashMap();
		placeholders.put("name",name);
		if (author!=null)
			placeholders.put("author",author);

		pluginName = Language.get("plugin.name");
		pluginName = StringUtil.replace(pluginName,placeholders);

		lStatus.setToolTipText(plugin.getDisplayName(Version.osDir));

		int cap = plugin.getParseCapabilities();
		analysis = plugin.getAnalysis();
		analysis.reset();
		adjustCapabilities(cap);
	}

	protected void disconnect()
	{
		plugin.removeMessageListener(this);
		this.plugin = null;
		pluginName = null;
		display(PAUSED, null, inBook);
	}

	public void handleMessage(Object who, int what, Object data)
	{
		AnalysisRecord a=null;
		EngineState state=EngineState.valueOf(what);
		if (state!=null)
		{
			switch (state) {
				case THINKING:
				case ANALYZING:
				case PONDERING:
					a = (AnalysisRecord) data;
					if (a!=null) {
						analysis = a;
						if (EnginePlugin.msgSent <= EnginePanel.msgSeen) return; // already handled this message
						EnginePanel.msgSeen = EnginePlugin.msgSent;
					}
					break;
			}

			switch (state) {
				case THINKING:
				case PONDERING:
				case ANALYZING:
					if (analysis!=null)
						broadcastAnalysis(what,analysis);
					//	analysis arrows in board panel
					break;
			}

			switch (state) {
				case THINKING:
				case ANALYZING:
					exitBook();
					if (analysis != null) display(state, analysis, inBook);
					break;
				case PONDERING:
					if (!inBook && analysis != null) display(state, analysis, inBook);
					break;
				case PAUSED:
					if (!inBook)
						display(state, null, inBook);
					else
						display(state, bookmoves, inBook);
					break;
			}
		}

		switch(what) {
		case EnginePlugin.PLUGIN_ELAPSED_TIME:
			//  TODO update elapsed time
			int elapsedTime = Util.toint(data);
			//System.err.println("tick "+((double)elapsedTime/1000));
			setElapsedTime(elapsedTime,null);
			break;

		case EnginePlugin.PLUGIN_HINT:
		//case EnginePlugin.PLUGIN_REQUESTED_HINT:
				//  requested or unrequested hint: show as tool tip
				analysis.ponderMove = (EnginePlugin.FormattedMove) data;
				break;

		case EnginePlugin.PLUGIN_ERROR:
		case EnginePlugin.PLUGIN_FATAL_ERROR:
			showInfo(data.toString());
			break;
		}

		//updateButtonState();
	}

//	public Move getHintMove()
//	{
//		return (Move)bHint.getClientProperty("hint");
//	}

//	public static String getHintTip(Object data)
//	{
//		String tiptext = Language.get("engine.hint.tip")+" ";
//		String moveText = "?";
//		if (data!=null) moveText = data.toString();
//		return StringUtil.replace(tiptext,"%move%", moveText);
//	}


//	private void receiveHint(Object data)
//	{
//		bHint.putClientProperty("hint", data);
//		bHint.setToolTipText(getHintTip(data));
//	}

//    private void hideHint()
//    {
//      bHint.setText(null);
//		bHint.putClientProperty("hint", null);
//        bHint.setToolTipText(Language.getTip("menu.game.hint"));
//    }

    protected void toggleHistory()
    {
        showHistory = !showHistory;

        //  update visibility
        tPVHistory.setVisible(showHistory);

        JoStyledLabel label;
	    showLines(0, !showHistory);

	    label = getInfoLabel(false);
        if (label!=null) label.setVisible(!showHistory);

        //  update profile
        Application.theUserProfile.set("plugin.pv.history",showHistory);

        //  update layout
        pvPanel.revalidate();
        //  scroll
        AWTUtil.scrollDown(pvScroller,pvPanel);
    }

	public void showTooltips(boolean on)
	{
		if (on==showTooltips) return;

		showTooltips = on;
		for(int i=0; i < pvCount; ++i)
			showTooltip(i);

		Application.theUserProfile.set("plugin.pv.tooltips",showTooltips);
	}

	public void showTooltip(int idx)
	{
		JoStyledLabel pv = getPvLabel(idx,false,false);
		if (pv==null) return;
		if (showTooltips)
			pv.tooltip = new PopupBoardWindow(this,idx);
		else
			pv.tooltip = null;
	}

	private void showLines(int from, boolean visible)
	{
		WdlLabel label1;
		JoStyledLabel label2;
		int to = Math.max(lEval.size(), lPrimaryVariation.size());

		for (int i=from; i < to; i++) {
		    label1 = getEvalLabel(i,false,false);
//			label = getDynamicLabel(lEval, i+1, false, false, null);
		    if (label1!=null) label1.setVisible(visible);

		    label2 = getPvLabel(i,false,false);
//			label = getDynamicLabel(lPrimaryVariation, i+1, false, false, null);
		    if (label2!=null) label2.setVisible(visible);
		}
	}

	public void adjustContextMenu(Collection list, MouseEvent event)
	{
		super.adjustContextMenu(list, event);

		list.add(ContextMenu.SEPARATOR);

		list.add("move.start");
		list.add("engine.stop");
		list.add("menu.game.analysis");

        list.add(ContextMenu.SEPARATOR);

//		list.add("menu.game.hint");
		list.add("menu.game.draw");
		list.add("menu.game.resign");

		list.add(ContextMenu.SEPARATOR);

		list.add(showTooltips);
		list.add("plugin.pv.tooltips");		// 	-> user profile

		if (plugin!=null && plugin instanceof UciPlugin)
		{
			UciPlugin uciplug = (UciPlugin)plugin;

			String opt_wdl = uciplug.getOptionValue("UCI_ShowWDL");
			String opt_verb = uciplug.getOptionValue("VerboseMoveStats");

			if (opt_wdl!=null)
			{
				list.add(opt_wdl.equals("true"));
				list.add("plugin.score.wdl");        //	-> engine setting
			}
			if (opt_verb!=null)
			{
				list.add(opt_verb.equals("true"));
				list.add("plugin.verbose.stats");    //	-> engine setting
			}
			//	todo Leela ScoreTypes ?
		}

        list.add(ContextMenu.SEPARATOR);

        //list.add(Util.toBoolean(showHistory));
        //list.add("plugin.pv.history"); expert mode is deprecated

		/** line specific commands  */
		for (int i=0; i < pvCount; i++)
		{
			JoStyledLabel label = getPvLabel(i,false, false);
			if (label!=null && AWTUtil.isInside(event,label))
			{
				String text = getPvText(i);
				if (text!=null) {

					list.add("menu.game.copy.line");
					list.add(new StringBuffer(text));

					list.add("menu.game.paste.line");
					list.add(new StringBuffer(text));
				}
				break;
			}
		}

		list.add(ContextMenu.SEPARATOR);

		list.add("restart.plugin");

		/** show UCI options    */
		if (plugin != null && (plugin instanceof UciPlugin))
		{
			ArrayList<UciPlugin.Option> buttons = ((UciPlugin)plugin).getUciButtons();
			if (buttons!=null) {
				for (UciPlugin.Option option : buttons)
				{
                    String title = StringUtil.trim("plugin.option."+option.name, StringUtil.TRIM_ALL);
					title = Language.get(title, option.name);

                    JMenuItem item = new JMenuItem(title);
					item.setActionCommand("plugin.option");
					item.putClientProperty("action.data",option);
					list.add(item);
				}
			}
		}

		list.add(ContextMenu.SEPARATOR);

		list.add("menu.edit.option");
		list.add(4);
	}

	public void updateLanguage()
	{
		Language.update(infoPanel);
	}

	public void setupActionMap(Map<String, CommandAction> map)
	{
		super.setupActionMap(map);

		CommandAction action = new CommandAction() {
			public void Do(Command cmd) {
				if (plugin!=null) disconnect();
				connectTo((EnginePlugin)cmd.data);
			}
		};
		map.put("new.plugin", action);

		action = new CommandAction() {
			public void Do(Command cmd) {
				if (plugin!=null) disconnect();
				plugin = null;
			}
		};
		map.put("close.plugin", action);

		/**	default action that is performed upon each broadcast	*/
		action = new CommandAction() {
			public void Do(Command cmd) {
				Application.AppMode mode = (Application.AppMode)cmd.data;
				Application.PlayState pstate = (Application.PlayState) cmd.moreData;
				updateButtonState(mode,pstate,null);
			}
		};
		//map.put("on.broadcast",action);
		map.put("app.state.changed",action);
		//map.put("move.notify",action); below

		action = new CommandAction() {
			public void Do(Command cmd)
					throws IOException
			{
				/**	adjust buttons (why ?)	*/
				updateButtonState();
				Application.theApplication.updateBook(false,false);
			}
		};
		map.put("switch.game", action);

        action = new CommandAction() {
			public void Do(Command cmd) throws IOException
			{
				updateButtonState();
				boolean wasEngineMove = cmd.moreData != null && cmd.moreData instanceof EnginePlugin.EvaluatedMove;
				switch(Application.theApplication.theMode)
				{	//	todo move this stuff to Application (but it's called in many places..)
					case USER_ENGINE:
					case ENGINE_ENGINE:
						if (wasEngineMove)
							Application.theApplication.updateBook(true,false);
							// might be overwritten by pondering, but well...
						//else
							// BOOK_PLAY will come soon
						break;
					case USER_INPUT:
						Application.theApplication.updateBook(false,false);
						break;
					case ANALYSIS:
						Application.theApplication.updateBook(false,true);
						break;
				}
			}
		};
		map.put("move.notify",action);

//		action = new CommandAction() {
//            public void Do(Command cmd) {
//                hideHint();
//            }
//        };
//        map.put("hide.hint",action);

		action = new CommandAction() {
			public void Do(Command cmd) {
				//  set (=execute) a UCI option
				if (plugin!=null && plugin instanceof UciPlugin)
					((UciPlugin)plugin).setDefaultOption((UciPlugin.Option)cmd.data);
			}
		};
		map.put("plugin.option",action);

		action = new CommandAction() {
			public void Do(Command cmd) {
				//  copy a text into the clipboard
				ClipboardUtil.setPlainText(cmd.data, EnginePanel.this);
			}
		};
		map.put("menu.game.copy.line",action);

        action = new CommandAction() {
            public void Do(Command cmd)
            {
                //  keep content
                String[] strings = {
                    lCurrentMove.getText(),
                    lDepth.getText(),
                    lElapsedTime.getText(),
                    lNodeCount.getText(),
                    lNodesPerSecond.getText(),
                    lStatus.getText(),
                };

                updateLanguage();

                lCurrentMove.setText(strings[0]);
                lDepth.setText(strings[1]);
                lElapsedTime.setText(strings[2]);
                lNodeCount.setText(strings[3]);
                lNodesPerSecond.setText(strings[4]);
                lStatus.setText(strings[5]);

	            StringMoveFormatter.setDefaultLanguage(Application.theUserProfile.getFigurineLanguage());
            }
        };
        map.put("update.language",action);

        action = new CommandAction() {
            public void Do(Command cmd) {
                StringMoveFormatter.setDefaultLanguage(Application.theUserProfile.getFigurineLanguage());
				JoStyleContext styles = Application.theUserProfile.getStyleContext();
				setupMoveFormatter(styles);
            }
        };
        map.put("styles.modified",action);

		action = new CommandAction() {
			public void Do(Command cmd) {
				boolean dark = (Boolean)cmd.moreData;
				setBgColor(dark);
				applyBgColor();

				createIcons(dark);
				setIcon(bPause,iPause);
				setIcon(bAnalyze,iAnalyze);
				setIcon(bHint,iHint);
				setIcon(bThreat,iBolt);
				updateButtonState();
			}
		};
		map.put("update.ui",action);

        action = new CommandAction() {
            public void Do(Command cmd) {
                //  copy a text into the clipboard
                toggleHistory();
            }
        };
        map.put("plugin.pv.history",action);


		action = new CommandAction() {
			public void Do(Command cmd) throws IOException {
				boolean tooltips = Application.theUserProfile.getBoolean("plugin.pv.tooltips");
				tooltips = !tooltips;
				Application.theUserProfile.set("plugin.pv.tooltips", tooltips);
				showTooltips(tooltips);
			}
		};
		map.put("plugin.pv.tooltips",action);

		action = new CommandAction() {
			public boolean isEnabled(String code) {
				UciPlugin plugin = getUciPlugin();
				return plugin != null && plugin.supportsOption("UCI_ShowWDL");
			}
			public void Do(Command cmd) throws IOException {
				UciPlugin plugin = getUciPlugin();
				if (plugin!=null && plugin.supportsOption("UCI_ShowWDL"))
				{
					String opt_wdl = plugin.getOptionValue("UCI_ShowWDL");
					if (opt_wdl!=null)
					{
						boolean use_wdl = opt_wdl.equals("true");
						use_wdl = !use_wdl;
						EnginePlugin.setOptionValue(plugin.config,"UCI_ShowWDL",Boolean.toString(use_wdl));
						plugin.restartWithOptions(true);
					}
				}
			}
		};
		map.put("plugin.score.wdl",action);


		action = new CommandAction() {
			public boolean isEnabled(String code) {
				UciPlugin plugin = getUciPlugin();
				return plugin != null && plugin.supportsOption("VerboseMoveStats");
			}
			public void Do(Command cmd) throws IOException {
				UciPlugin plugin = getUciPlugin();
				if (plugin!=null && plugin.supportsOption("VerboseMoveStats"))
				{
					String opt_stats = plugin.getOptionValue("VerboseMoveStats");
					if (opt_stats!=null)
					{
						boolean use_stats = opt_stats.equals("true");
						use_stats = !use_stats;
						EnginePlugin.setOptionValue(plugin.config,"VerboseMoveStats",Boolean.toString(use_stats));
						plugin.restartWithOptions(true); // todo analysis? what about thinking ?
					}
				}
			}
		};
		map.put("plugin.verbose.stats",action);

	}

	public void lostOwnership(Clipboard clipboard, Transferable contents)
	{
		//  implements ClipboardOwner
	}

	public void mouseClicked(MouseEvent e)
	{
		//  recognize double clicks on variation lines
		if (ContextMenu.isTrigger(e)) return;   //  ignore right mouse clicks

		if (e.getButton()==MouseEvent.BUTTON1 && e.getClickCount()==2)
		{
			//  double click
			for (int i=0; ; i++)
			{
				JoStyledLabel label = getPvLabel(i,false, false);
				if (label==null) break;
				if (label==e.getSource())
				{
					String text = getPvText(i);
					if (text!=null) {
						Command cmd = new Command("menu.game.paste.line", e, text, Boolean.TRUE);
						Application.theCommandDispatcher.forward(cmd, EnginePanel.this, true);
					}
				}
			}
		}
	}

	public void mousePressed(MouseEvent e)
	{ }

	public void mouseReleased(MouseEvent e)
	{ }

	public void mouseEntered(MouseEvent e)
	{ }

	public void mouseExited(MouseEvent e)
	{ }
}
