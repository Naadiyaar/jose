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

import de.jose.AbstractApplication;
import de.jose.Language;
import de.jose.Util;
import de.jose.util.AWTUtil;
import de.jose.util.ClipboardUtil;
import de.jose.chess.Board;
import de.jose.chess.Constants;
import de.jose.chess.EngUtil;
import de.jose.image.ImgUtil;
import de.jose.image.Surface;
import de.jose.profile.UserProfile;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.SoftBevelBorder;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.TextAction;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;

/**
 *
 * @author Peter Sch�fer
 */
public class BoardEditView
		extends BoardView2D
        implements ClipboardOwner
{
	protected Surface BACKGROUND;

	protected static double WIDTH_RATIO       = 10.4;
	protected static double HEIGHT_RATIO      = 8.4;
	protected static double PREFERRED_RATIO   = WIDTH_RATIO/HEIGHT_RATIO;

	/**
	 * TODO clipboard: copy  & paste FEN strings
	 * doesn't work, yet
	 */
	class CopyToClipboardAction extends TextAction
	{
		CopyToClipboardAction(String name)              { super(name); }
		public void actionPerformed(ActionEvent e)	    { copyToClipboard(); }
	}

	class PasteFromClipboardAction extends TextAction
	{
		PasteFromClipboardAction(String name)           { super(name); }
		public void actionPerformed(ActionEvent e)      { pasteFromClipboard(); }
	}

	public BoardEditView(IBoardAdapter board)
	{
		super(board,false);
		Color bgColor 	= UIManager.getColor("Panel.background");
		BACKGROUND 		= Surface.newColor(bgColor);

		/** set up cut/copy/paste actions
		 *  TODO why is this not working ??
		 * */
		ActionMap amap = getActionMap();
		InputMap imap = getInputMap();

		imap.put(AWTUtil.getMenuKeyStroke("CTRL X"), DefaultEditorKit.cutAction);
		amap.put(DefaultEditorKit.cutAction, new CopyToClipboardAction(DefaultEditorKit.cutAction));

		imap.put(AWTUtil.getMenuKeyStroke("CTRL C"), DefaultEditorKit.copyAction);
		amap.put(DefaultEditorKit.copyAction, new CopyToClipboardAction(DefaultEditorKit.copyAction));

		imap.put(AWTUtil.getMenuKeyStroke("CTRL V"), DefaultEditorKit.pasteAction);
		amap.put(DefaultEditorKit.pasteAction, new PasteFromClipboardAction(DefaultEditorKit.pasteAction));
	}


	public void flip(boolean on) {
		//	never flip, ignore parameter
		super.flip(false);
	}

	public void showCoords(boolean on) {
		//	always show coords, ignore parameter
		super.showCoords(true);
	}

	public void showEvalbar(boolean on) {
		//	never show eval bar
		super.showEvalbar(false);
	}

	public void updateProfile(UserProfile prf)
	{
		super.updateProfile(prf);
		//	fix background
		currentBackground = BACKGROUND;
	}

	/*	clear board */
	public void clearPosition()         { setup(Constants.EMPTY_POSITION); }

	/*	initial board */
	public void initialPosition()       { setup(Constants.START_POSITION); }

	/*	copy from main board */
	public void defaultPosition()       {
		setup(AbstractApplication.theAbstractApplication.theGame.getPosition());
	}

	public void setup(String fen)
	{
		board.getPosition().setup(fen);
//		adjustControls();

		refresh(true);
		board.userMove(null);   //  indicates complete reset
	}

	public int setup(int frcVariant, int frcIndex)
	{
		frcIndex = board.getPosition().setupInitial(frcVariant,frcIndex);

		refresh(true);
		board.userMove(null);   //  indicates complete reset

		return frcIndex;
	}

	protected void setup(Board that)
	{
		board.getPosition().setup(that);
//		adjustControls();

		refresh(true);
		board.userMove(null);   //  indicates complete reset
	}

	public boolean pasteFromClipboard()
	{
		String text = ClipboardUtil.getPlainText(this);
		if (text != null) {
			//  note that this is not necessarily a vaild FEN string
			try {
				board.getPosition().setup(text);
				refresh(true);
				board.userMove(null);   //  indicates complete reset
				return true;
			} catch (Throwable e) {
				/** parse error in FEN string - don't mind */
				AWTUtil.beep(this);  //  "beep"
			}
		}
		//  else:
		AWTUtil.beep(this);  //  "beep"
		return false;
	}

	public void copyToClipboard()
	{
		ClipboardUtil.setPlainText(board.getPosition(), this);
	}

	/**	calculate square size and inset after resize	 */
	public void recalcSize(Graphics2D g)
	{
		super.recalcSize(g);

		Point2D.Double scale = getBufferScaleFactor(g);
		devInset.y = 0;
		userInset.y = 0;

		devInset.x = Math.round(0.4f*devSquareSize);	//	board is always left aligned
		userInset.x = devInset.x / scale.getX();
		//Point p0 = origin(KING,0);
	}




	protected void paintHook(boolean redraw)
	{
		//	paint piece box

		if (redraw) {
			Graphics2D g = getBufferGraphics();

			g.setColor(Color.black);
			Border b = new SoftBevelBorder(BevelBorder.RAISED);
			Point2D p0 = origin(PAWN,0,false);
			b.paintBorder(this,g, (int)p0.getX()-2, (int)p0.getY()-2,
					2*devSquareSize+4,6*devSquareSize+4);

			for (int pc = PAWN; pc <= KING; pc++) {
				paint(g, WHITE+pc, EngUtil.square(pc,0));
				paint(g, BLACK+pc, EngUtil.square(pc,1));
			}
		}
	}

	protected Surface getBackground(int square)
	{
		switch (EngUtil.rowOf(square))
		{
		case 0:
		case 1:			return BACKGROUND;
		default:		return super.getBackground(square);
		}
	}

	public void set(Graphics2D g, int piece, int square)
	{
		//	can't edit BOX pieces
		if (EngUtil.rowOf(square) <= 1)
			return;
		else
			super.set(g,piece,square);
	}

	public int findSquare(double x, double y, boolean userSpace)
	{
		double squareSize = userSpace ? userSquareSize : devSquareSize;
		Point2D p0 = origin(PAWN,0,userSpace);
		if (x >= p0.getX() && y >= p0.getY()) {
			int pc = PAWN + (int)((y-p0.getY())/squareSize);
			int col = (int)((x-p0.getX())/squareSize);

			if ((pc>=PAWN) && (pc<=KING) && (col>=0) && (col<=1))
				return EngUtil.square(pc,col);
		}
		//	else
		int result = super.findSquare(x,y,userSpace);
		if (result==0)
			result = EngUtil.square(0,KING+1); //	= delete piece
		return result;
	}

	protected Point2D origin(int file, int row, boolean userSpace)
	{
		double squareSize = userSpace ? userSquareSize : devSquareSize;
		Point2D inset = userSpace ? userInset : devInset;

		switch (row) {
		case 0:
				if ((file==0) || (file>KING))
					return new Point2D.Double(-squareSize,-squareSize);
				else
					return new Point2D.Double(inset.getX()+8*squareSize+24, inset.getY()+(file-PAWN)*squareSize);
		case 1:
				return new Point2D.Double(inset.getX()+9*squareSize+24, inset.getY()+(file-PAWN)*squareSize);
		default:
				return super.origin(file,row,userSpace);
		}
	}

	public void lostOwnership(Clipboard clipboard, Transferable contents)
	{
		//  implements ClipboardOwner
	}
}
