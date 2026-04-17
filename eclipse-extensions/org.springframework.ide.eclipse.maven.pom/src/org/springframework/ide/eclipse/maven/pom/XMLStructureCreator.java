/*******************************************************************************
 * Copyright (c) 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.maven.pom;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.eclipse.compare.IEditableContent;
import org.eclipse.compare.IEncodedStreamContentAccessor;
import org.eclipse.compare.IStreamContentAccessor;
import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.compare.structuremergeviewer.IDiffContainer;
import org.eclipse.compare.structuremergeviewer.IStructureComparator;
import org.eclipse.compare.structuremergeviewer.IStructureCreator;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.springsource.ide.eclipse.commons.livexp.util.Log;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

/**
 * Implementation is based on the Team XML Compare example
 * 
 * @author Alex Boyko
 *
 */
public class XMLStructureCreator implements IStructureCreator {

	protected static final boolean DEBUG_MODE= false;
	
	public static final String DEFAULT_NAME= "XML Compare"; 

	private boolean fRemoveWhiteSpace;
	
	final private IdProviderRegistry idProviders;

	public XMLStructureCreator(IdProviderRegistry idProviders) {
		this.idProviders = idProviders;
		fRemoveWhiteSpace= false;
	}

	/*
	 * This title will be shown in the title bar of the structure compare pane.
	 */
	@Override
	public String getName() {
		return DEFAULT_NAME;
	}

	/*
	 * Returns the XML parse tree of the input.
	 */
	@Override
	public IStructureComparator getStructure(Object input) {
		if (XMLStructureCreator.DEBUG_MODE)
			System.out.println("Starting parse"); //$NON-NLS-1$

		if (!(input instanceof IStreamContentAccessor))
			return null;

		IStreamContentAccessor sca= (IStreamContentAccessor) input;

		try {
			// Input parsed with parser.parse(new InputSource(sca.getContents));	

			String contents= readString(sca);
			if (contents == null)
				contents= ""; //$NON-NLS-1$
			return createStructure(new Document(contents));
		} catch (CoreException e) {
			Log.log(e);
		}
		return null;
	}
	
	public DomStructureComparable createStructure(IDocument doc) {
		try {

			SAXParserFactory factory= SAXParserFactory.newInstance();
			factory.setNamespaceAware(true);
			SAXParser parser= factory.newSAXParser();
			SaxToStructureHandler handler = new SaxToStructureHandler(doc, idProviders);
			if (doc.getLength() == 0) {
				return null;
			}
			parser.parse(new InputSource(new StringReader(doc.get())), handler);

			if (XMLStructureCreator.DEBUG_MODE)
				System.out.println("End of parse"); //$NON-NLS-1$
			
			return handler.getRoot();
		} catch (SAXParseException e) {
			Log.log(e);
			return null;
		} catch (Exception e) {
			//				MessageDialog.openError(PomPlugin.getActiveWorkbenchShell(),"Error in XML parser","An error occurred in the XML parser.\nNo structured compare can be shown");
			Log.log(e);
			return null;
		}
	}

	public boolean canSave() {
		return true;
	}

	public boolean canRewriteTree() {
		return false;
	}

	public void rewriteTree(Differencer differencer, IDiffContainer root) {
			// nothing to do
	}

	@Override
	public void save(IStructureComparator structure, Object input) {
		if (input instanceof IEditableContent && structure instanceof DomStructureComparable) {
			IDocument document= ((DomStructureComparable) structure).getDocument();
			IEditableContent bca= (IEditableContent) input;
			String contents= document.get();
			String encoding= null;
			if (input instanceof IEncodedStreamContentAccessor) {
				try {
					encoding= ((IEncodedStreamContentAccessor)input).getCharset();
				} catch (CoreException e1) {
					// ignore
				}
			}
			if (encoding == null)
				encoding= "UTF-8"; //$NON-NLS-1$
			try {
				bca.setContent(contents.getBytes(encoding));
			} catch (UnsupportedEncodingException e) {
				bca.setContent(contents.getBytes());	
			}
		}
	}

	@Override
	public String getContents(Object node, boolean ignoreWhitespace) {
		if (node instanceof DomStructureComparable) {
			String s = ((DomStructureComparable)node).getTextContent();
			if (ignoreWhitespace)
				s= s.trim();
			return s;
		}
		return null;
	}

	@Override
	public IStructureComparator locate(Object path, Object source) {
		return null;
	}

	static String readString(IStreamContentAccessor sa) throws CoreException {
		InputStream is= sa.getContents();
		String encoding= null;
		if (sa instanceof IEncodedStreamContentAccessor)
			encoding= ((IEncodedStreamContentAccessor) sa).getCharset();
		if (encoding == null)
			encoding= "UTF-8"; //$NON-NLS-1$
		return readString(is, encoding);
	}

	/*
	 * Returns null if an error occurred.
	 */
	private static String readString(InputStream is, String encoding) {
		if (is == null)
			return null;
		BufferedReader reader= null;
		try {
			StringBuilder buffer= new StringBuilder();
			char[] part= new char[2048];
			int read= 0;
			reader= new BufferedReader(new InputStreamReader(is, encoding));

			while ((read= reader.read(part)) != -1)
				buffer.append(part, 0, read);

			return buffer.toString();

		} catch (IOException ex) {
			// NeedWork
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException ex) {
					// silently ignored
				}
			}
		}
		return null;
	}

	protected boolean isWhiteSpace(char c) {
		return c == '\t' || c == '\n' || c == '\r' || c == ' ';
	}

	protected String removeWhiteSpace(String str) {
		str= trimWhiteSpace(str);
		StringBuilder retStr= new StringBuilder();
		int start= 0, end= 0;
		outer_while: while (true) {
			while (end < str.length() && !isWhiteSpace(str.charAt(end))) {
				end++;
			}
			if (end > str.length())
				break outer_while;
			if (start != 0)
				retStr.append(' ');
			retStr.append(str.substring(start, end));
			end++;
			while (end < str.length() && isWhiteSpace(str.charAt(end))) {
				end++;
			}
			start= end;
		}
		return retStr.toString();
	}

	protected String trimWhiteSpace(String str) {
		int start= 0, end= str.length() - 1;
		while (start < str.length() && isWhiteSpace(str.charAt(start))) {
			start++;
		}
		if (start == str.length())
			return ""; //$NON-NLS-1$
		while (end >= 0 && isWhiteSpace(str.charAt(end))) {
			end--;
		}
		return str.substring(start, end + 1);
	}

	public void setRemoveWhiteSpace(boolean removeWhiteSpace) {
		fRemoveWhiteSpace= removeWhiteSpace;
	}

	public boolean getRemoveWhiteSpace() {
		return fRemoveWhiteSpace;
	}
}
