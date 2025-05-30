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

package de.jose.pgn;

import de.jose.Application;
import de.jose.util.file.FileUtil;

import java.util.Enumeration;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.GZIPInputStream;
import java.io.*;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

/**
 * @author Peter Sch�fer
 */

public class TarEnumeration
        implements Enumeration
{
	protected TarArchiveInputStream tin;
	protected TarArchiveEntry next;

	protected FilenameFilter filter;

	/**
	 * @return true of the given file contains any PGN data
	 */
	static public boolean contains(File f, FilenameFilter filter)
	{
		TarEnumeration en = null;
		try {
			en = new TarEnumeration(f,filter);
			return en.hasMoreElements();
		} catch (Throwable ioex) {
			ioex.printStackTrace();
			return false;
		} finally {
			if (en!=null) en.close();
		}
	}

	static public boolean matches(TarArchiveEntry ety, FilenameFilter filter)
	{
		String entryName = ety.getName();
		if (filter!=null)
			return filter.accept(null,entryName);
		else
			return true;
	}


	public static TarArchiveInputStream createTarInputStream(File file) throws IOException
	{
		return createTarInputStream(new FileInputStream(file), file.getName());
	}

	public static TarArchiveInputStream createTarInputStream(InputStream fin, String fileName) throws IOException
	{
		String trimmedName = FileUtil.trimExtension(fileName);

		BufferedInputStream bin = new BufferedInputStream(fin);

		if (FileUtil.hasExtension(fileName,"tar"))
			return new TarArchiveInputStream(bin);

		if (FileUtil.hasExtension(fileName,"tgz") ||
		    FileUtil.hasExtension(fileName,"tgzip") ||
		        FileUtil.hasExtension(trimmedName,"tar") && FileUtil.hasExtension(fileName,"gz") ||
		        FileUtil.hasExtension(trimmedName,"tar") && FileUtil.hasExtension(fileName,"gzip"))
		{
			GZIPInputStream gin = new GZIPInputStream(bin);
			return new TarArchiveInputStream(gin);
		}

		if (FileUtil.hasExtension(fileName,"tbz") ||
		    FileUtil.hasExtension(fileName,"tbz2") ||
		        FileUtil.hasExtension(trimmedName,"tar") && FileUtil.hasExtension(fileName,"bz") ||
		        FileUtil.hasExtension(trimmedName,"tar") && FileUtil.hasExtension(fileName,"bz2"))
		{
			BZip2CompressorInputStream bzin = FileUtil.createBZipInputStream(bin);
			return new TarArchiveInputStream(bzin);
		}

		if (FileUtil.hasExtension(fileName,"zstd") ||
				FileUtil.hasExtension(fileName,"zst") ||
				FileUtil.hasExtension(trimmedName,"tar") && FileUtil.hasExtension(fileName,"zstd") ||
				FileUtil.hasExtension(trimmedName,"tar") && FileUtil.hasExtension(fileName,"zst"))
		{
			throw new UnsupportedOperationException("zstd not yet supported. but could be.");
		}

//		throw new IllegalArgumentException(file+" is not a tar file");  //  or is it ?
		return new TarArchiveInputStream(bin);
	}

	/**
	 * enumerates ZipEntries within a ZIP file
	 */
	public TarEnumeration(File f, FilenameFilter filter) throws IOException
	{
		this.filter = filter;
		tin = createTarInputStream(f);
		next = null;
	}

	public boolean hasMoreElements()
	{
		if (next==null) fetchNext();
		return next != null;
	}

	public Object nextElement()
	{
		if (next==null) fetchNext();
		Object result = next;
		next = null;
		return result;
	}

	public final TarArchiveEntry nextTarEntry()	{
		return (TarArchiveEntry) nextElement();
	}

	/**
	 * return a *fresh* input stream for the current zip entry
	 */
	public final static InputStream getInputStream(File f, String name)
		throws IOException
	{
		TarArchiveInputStream tin = createTarInputStream(f);
		TarArchiveEntry ety = tin.getNextEntry();
		while (ety != null) {
			if (ety.getName().equals(name))
				return tin;
			else
				ety = tin.getNextEntry();
		}
		tin.close();
		return null;
	}

	public void close()
	{
		try {
			tin.close();
		} catch (IOException ioex) {
			//	who cares ?
		}
	}

	protected void fetchNext()
	{
		for(;;) {
			try {
				next = tin.getNextEntry();
			} catch (IOException ioex) {
				throw new RuntimeException(ioex.getMessage());
			}

			if (next==null) return;
			if (matches(next,filter)) return;
		}
	}
}
