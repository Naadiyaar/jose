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

package de.jose.sax;

import de.jose.export.ExportConfig;
import de.jose.util.FontUtil;
import de.jose.util.file.FileUtil;
import de.jose.util.xml.XMLUtil;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.File;
import java.io.IOException;
import java.awt.print.PageFormat;
import java.awt.*;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;

import de.jose.Application;
import de.jose.Util;
import de.jose.export.ExportContext;
import de.jose.view.style.JoStyleContext;
import de.jose.view.style.JoFontConstants;

import javax.swing.text.StyleContext;
import javax.swing.text.StyleConstants;
import javax.swing.text.MutableAttributeSet;

/**
 * @author Peter Sch�fer
 */

public class CSSXMLReader extends AbstractObjectReader
{
	protected ExportContext context;

	protected static AttributesImpl INHERITED = new AttributesImpl();
	static {
		INHERITED.addAttribute(null,"inherited","inherited","CDATA",null);
	};

	public Set<String> fontFamilies = new TreeSet<>();

	public CSSXMLReader(ExportContext context)
	{
		this.context = context;
	}

	public void parse(InputSource input) throws IOException, SAXException
	{
//		if (games == null)
//			throw new NullPointerException("Parameter GameSource must not be null");
		if (handler == null)
			throw new IllegalStateException("ContentHandler not set");

//Start the document
		handler.setContext(this.context);
		handler.startDocument();
		handler.startElement("jose-export");

		//  style info
		toSAX(context.styles,handler);
		saxOptions(context.config,handler);

//End the document
		handler.endElement("jose-export");
		handler.endDocument();
	}

	public void toSAX(JoStyleContext styles, JoContentHandler handler) throws SAXException
	{
		handler.startElement("styles");
		StyleContext.NamedStyle base = (StyleContext.NamedStyle) styles.getStyle("base");
		fontFamilies.clear();
		toSAX(base,handler);
		//	always provide FontAwesome
		fontFamilies.add(FontUtil.awesomeName());
		toSAX(fontFamilies,handler);
		handler.endElement("styles");
	}

	private void toSAX(StyleContext.NamedStyle style, JoContentHandler handler) throws SAXException
	{
		handler.startElement("style");
			handler.element("name", style.getName());
			//  dump attributes
			Enumeration attrNames = style.getAttributeNames();
			while (attrNames.hasMoreElements())
			{
				Object key = attrNames.nextElement();
				//  don't dump these attributes:
				if (key.equals(StyleConstants.ResolveAttribute)) continue;
				if (key.equals(StyleConstants.NameAttribute)) continue;
				if (key.equals("children")) continue;

				Object value = style.getAttribute(key);
				if (value instanceof Color)
					handler.keyValue("a",key.toString(), toString((Color)value));
				else
					handler.keyValue("a",key.toString(), value.toString());

				if (key.toString().equals(StyleConstants.FontConstants.Family.toString()))	//	not that FontConstants does not implement equals()
					fontFamilies.add(value.toString());
			}

			//  dump /some/ inherited attributes
			//  Font Family, Size, Style, Weight, Color
			if (!style.isDefined(StyleConstants.FontConstants.Family))
				handler.keyValue("a", INHERITED, StyleConstants.FontConstants.Family.toString(), JoFontConstants.getFontFamily(style));
			if (!style.isDefined(StyleConstants.FontConstants.Size))
				handler.keyValue("a",INHERITED,
						StyleConstants.FontConstants.Size.toString(),
						String.valueOf(JoFontConstants.getFontSize(style)));
			if (!style.isDefined(StyleConstants.FontConstants.Bold))
				handler.keyValue("a",INHERITED,
						StyleConstants.FontConstants.Bold.toString(),
						Boolean.toString(StyleConstants.FontConstants.isBold(style)));
			if (!style.isDefined(StyleConstants.FontConstants.Italic))
				handler.keyValue("a",INHERITED,
						StyleConstants.FontConstants.Italic.toString(),
						Boolean.toString(StyleConstants.FontConstants.isItalic(style)));
			if (!style.isDefined(StyleConstants.ColorConstants.Foreground))
				handler.keyValue("a",INHERITED,
						StyleConstants.ColorConstants.Foreground.toString(),
						toString(StyleConstants.ColorConstants.getForeground(style)));

			//  dump children
			java.util.List children = JoStyleContext.getChildren(style);
			if (children != null) {
				if (children.size() > 20) {
					System.err.println("WARNING: Suspicious style with "+children.size()+" children: "+style.getName());
				}
				for (int i = 0; i < children.size(); i++) {
					StyleContext.NamedStyle child = (StyleContext.NamedStyle) children.get(i);
					toSAX(child, handler);
				}
			}

		handler.endElement("style");
	}


	private static void toSAX(Set<String> families, JoContentHandler handler) throws SAXException {
		for (String family : families)
		{
			//	get local font file
			File file = FontUtil.getTrueTypeFile(family,false,false,true);
			if (file!=null) {
				handler.startElement("font-face");
					handler.element("family", family);
					handler.element("path", file.getPath());
					handler.element("file", file.getName());
				handler.endElement("font-face");
			}
		}
	}


	private static String toString(Color color)
	{
		int red = color.getRed();
		int green = color.getGreen();
		int blue = color.getBlue();

		StringBuffer buf = new StringBuffer("#");
		buf.append(Integer.toHexString(red/16));
		buf.append(Integer.toHexString(red%16));
		buf.append(Integer.toHexString(green/16));
		buf.append(Integer.toHexString(green%16));
		buf.append(Integer.toHexString(blue/16));
		buf.append(Integer.toHexString(blue%16));
		return buf.toString();
	}

	public void saxOptions(Element cfg, JoContentHandler handler) throws SAXException
	{
		handler.startElement("options");

		switch (ExportConfig.getOutput(cfg)) {
		case ExportConfig.OUTPUT_HTML:
		case ExportConfig.OUTPUT_XML:
			//  standard HTML options
			//  figurine format ("tt" for TrueType, "img" for Image)
			String figs = context.profile.getString("xsl.html.figs","tt");
			//  standalone css ?
			//boolean cssStandalone = context.profile.getBoolean("xsl.css.standalone");
			//  collateral dir (contains images & css)
			String collpath = "";
            if (context.collateral != null) {
                if (context.target instanceof File) {
                    //	relative collateral path (from html to auxiliary dir)
                    File targetFile = (File) context.target;
                    File targetDir = targetFile.getParentFile();
                    collpath = FileUtil.getRelativePath(targetDir, context.collateral, "/");
                } else {
                    //	server mode
                    collpath = ".";//context.collateral.getAbsolutePath();
                }
            }

			if (!collpath.isEmpty()) {
				handler.keyValue("option", "xsl.html.img.dir",collpath+"/nav");
				handler.keyValue("option", "xsl.css.standalone", "true");	//@deprecated
				handler.keyValue("option", "xsl.html.css.dir", collpath);
				handler.keyValue("option", "xsl.html.font.dir", "fonts");	//	always relative to *.css, right?
				handler.keyValue("option", "xsl.html.js.dir", collpath);
			} else {
				//	default paths into working dir
				collpath = Application.theWorkingDirectory.getAbsolutePath();
				handler.keyValue("option", "xsl.html.img.dir",collpath+"/images/nav");
				handler.keyValue("option", "xsl.css.standalone", "true");	//@deprecated
				handler.keyValue("option", "xsl.html.css.dir", collpath);
				handler.keyValue("option", "xsl.html.font.dir", "fonts");	//	always relative to *.css, right?
				handler.keyValue("option", "xsl.html.js.dir", collpath+"/xsl");
			}

			break;
		}

		//  custom options defined in style sheet:
		NodeList options = cfg.getElementsByTagName("jose:option");
		for (int i=0; i < options.getLength(); i++)
		{
			Element option = (Element)options.item(i);
			String key = XMLUtil.getChildValue(option,"jose:key");
			Object value = context.profile.get(key);
			if (value==null)
				value = XMLUtil.getChildValue(option,"jose:default");

			if (value != null)
				handler.keyValue("option",key,value.toString());
		}
		handler.endElement("options");
	}
}
