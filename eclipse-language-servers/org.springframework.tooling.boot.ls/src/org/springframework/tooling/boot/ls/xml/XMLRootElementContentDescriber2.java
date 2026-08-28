/*******************************************************************************
 * Copyright (c) 2004, 2009 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Jesper Steen Moeller - added namespace support
 *
 *
 * == == == =
 *
 * Copied from org.eclipse.core.runtime.content.XMLRootElementContentDescriber2 because:
 * 1. Cannot be overridden
 * 2. Need to return INVALID status if root element searched for not found. INDETERMINATE status only if there are no root elements 
 * @author aboyko
 *     
 *******************************************************************************/
package org.springframework.tooling.boot.ls.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.core.internal.content.ContentMessages;
import org.eclipse.core.internal.content.XMLRootHandler;
import org.eclipse.core.internal.runtime.RuntimeLog;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.XMLContentDescriber;
import org.eclipse.core.runtime.content.XMLRootElementContentDescriber;
import org.eclipse.osgi.util.NLS;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * A content describer for detecting the name of the top-level element,
 * its namespace and the DTD system identifier in an XML file.
 * <p>
 * This executable extension supports "element" parameter, that
 * can be specified more than once. If the
 * <code>":-"</code> method is used, then the value is treated as
 * "element" (always just one)
 * </p>
 * <p>
 * The value of "element" is specified using such a format
 * <code>{namespace}name/dtd</code>. The namespace or dtd part
 * can be omitted and accepted are values like <code>name/dtd</code>,
 * <code>{ns}name</code> and <code>name</code>.
 * </p>
 * <p>
 * The describer will detect a document, if it matches at least one "element"
 * what means, that dtd, namespace (if specified) and name in "element"
 * match those in the document.
 * </p>
 * <p>
 * If the "element" name part is "*", e.g. <code>{namespace}*</code>,
 * it denotes a wildcard match. If the "element" namespace part is empty,
 * e.g. <code>{}name</code>, only these documents with the root element
 * that belong to the unnamed namespace <code>xmlns=""</code>
 * will be detected.
 * </p>
 * <p>
 * This class should be used instead of {@link XMLRootElementContentDescriber}
 * which doesn't detect namespaces and doesn't allow to specify
 * more than one set of dtds, root element names and namespaces which
 * should be detected.
 * </p>
 * <p>
 * This class is not intended to be subclassed or instantiated by clients,
 * only to be referenced by the "describer" configuration element in
 * extensions to the <code>org.eclipse.core.runtime.contentTypes</code>
 * extension point.
 * </p>
 *
 * @since org.eclipse.core.contenttype 3.3
 * @noinstantiate This class is not intended to be instantiated by clients.
 */
public final class XMLRootElementContentDescriber2 extends XMLContentDescriber implements IExecutableExtension {

	static final String DTD = "org.eclipse.core.runtime.content.XMLRootElementContentDescriber2.dtd"; //$NON-NLS-1$
	static final String NAMESPACE = "org.eclipse.core.runtime.content.XMLRootElementContentDescriber2.namespace"; //$NON-NLS-1$
	static final String ELEMENT = "org.eclipse.core.runtime.content.XMLRootElementContentDescriber2.element"; //$NON-NLS-1$
	static final String RESULT = "org.eclipse.core.runtime.content.XMLRootElementContentDescriber2.result"; //$NON-NLS-1$

	private static final String ELEMENT_TO_FIND = "element"; //$NON-NLS-1$

	/* (Intentionally not included in javadoc)
	 * The top-level elements we are looking for. This value will be initialized
	 * by the <code>setInitializationData</code> method. If no value is
	 * provided, then this means that we don't care what the top-level element
	 * will be. The list is a collection of <code>QualifiedElement</code>.
	 */
	private QualifiedElement[] elementsToFind = null;

	/* (Intentionally not included in javadoc)
	 * Simple value holder for root element name, its namespace and dtd.
	 */
	private class QualifiedElement {
		private String namespace;
		private String element;
		private String dtd;

		public QualifiedElement(String qualifiedElement) {
			// Extract namespace part
			int openBrace = qualifiedElement.indexOf('{');
			int closeBrace = qualifiedElement.indexOf('}');
			if (openBrace == 0 && closeBrace >= 1) {
				namespace = qualifiedElement.substring(1, closeBrace);
				qualifiedElement = qualifiedElement.substring(closeBrace + 1);
			}
			// Extract dtd part
			int dtdSlash = qualifiedElement.indexOf('/');
			if (dtdSlash > 0) {
				dtd = qualifiedElement.substring(dtdSlash + 1);
				qualifiedElement = qualifiedElement.substring(0, dtdSlash);
			}
			// Check if the name is a wildcard
			element = ("*".equals(qualifiedElement) ? null : qualifiedElement); //$NON-NLS-1$
		}

		public boolean matches(String someNamespace, String someElement, String someDtd) {
			boolean nsMatch = this.namespace != null ? this.namespace.equals(someNamespace) : true;
			boolean elementEquals = this.element != null ? this.element.equals(someElement) : true;
			boolean dtdEquals = this.dtd != null ? this.dtd.equals(someDtd) : true;
			return nsMatch && elementEquals && dtdEquals;
		}
	}

	/* (Intentionally not included in javadoc)
	 * Determines the validation status for the given contents.
	 *
	 * @param contents the contents to be evaluated
	 * @return one of the following:<ul>
	 * <li><code>VALID</code></li>,
	 * <li><code>INVALID</code></li>,
	 * <li><code>INDETERMINATE</code></li>
	 * </ul>
	 * @throws IOException
	 */
	private int checkCriteria(InputSource contents, Map<String, Object> properties) throws IOException {
		if (!isProcessed(properties))
			fillContentProperties(contents, properties);
		return checkCriteria(properties);
	}

	private int checkCriteria(Map<String, Object> properties) throws IOException {
		Boolean result = (Boolean) properties.get(RESULT);
		if (!result.booleanValue())
			return INDETERMINATE;
		// Check to see if we matched our criteria.
		if (elementsToFind != null) {
			boolean foundOne = false;
			for (int i = 0; i < elementsToFind.length && !foundOne; ++i) {
				String dtd = (String) properties.get(DTD);
				String namespace = (String) properties.get(NAMESPACE);
				String element = (String) properties.get(ELEMENT);
				foundOne |= elementsToFind[i].matches(namespace, element, dtd);
			}
			if (!foundOne)
				// !!!!!!!!! This is the difference from the original
				return properties.get(ELEMENT) == null ? INDETERMINATE : INVALID;
		}
		// We must be okay then.
		return VALID;
	}

	@Override
	public int describe(InputStream contents, IContentDescription description) throws IOException {
		return describe(contents, description, new HashMap<String, Object>());
	}

	/**
	 * @noreference This method is not intended to be referenced by clients.
	 */
	public int describe(InputStream contents, IContentDescription description, Map<String, Object> properties) throws IOException {
		// call the basic XML describer to do basic recognition
		if (superDescribe2(contents, description, properties) == INVALID)
			return INVALID;
		// super.describe will have consumed some chars, need to rewind
		contents.reset();
		// Check to see if we matched our criteria.
		return checkCriteria(new InputSource(contents), properties);
	}

	@Override
	public int describe(Reader contents, IContentDescription description) throws IOException {
		return describe(contents, description, new HashMap<String, Object>());
	}

	/**
	 * @noreference This method is not intended to be referenced by clients.
	 */
	public int describe(Reader contents, IContentDescription description, Map<String, Object> properties) throws IOException {
		// call the basic XML describer to do basic recognition
		if (superDescribe2(contents, description, properties) == INVALID)
			return INVALID;
		// super.describe will have consumed some chars, need to rewind
		contents.reset();
		// Check to see if we matched our criteria.
		return checkCriteria(new InputSource(contents), properties);
	}
	
	private int superDescribe2(Reader contents, IContentDescription description, Map<String, Object> properties) {
		try {
			Method m = getClass().getSuperclass().getDeclaredMethod("describe2", new Class<?>[]{ Reader.class, IContentDescription.class, Map.class});
	        m.setAccessible(true);
	        return (int) m.invoke(this, new Object[] {contents, description, properties});
		} catch (InvocationTargetException | IllegalAccessException | IllegalArgumentException | NoSuchMethodException | SecurityException e) {			
			return INDETERMINATE;
		}
	}

	private int superDescribe2(InputStream input, IContentDescription description, Map<String, Object> properties) {
		try {
			Method m = getClass().getSuperclass().getDeclaredMethod("describe2", new Class<?>[]{ InputStream.class, IContentDescription.class, Map.class});
	        m.setAccessible(true);
	        return (int) m.invoke(this, new Object[] {input, description, properties});
		} catch (InvocationTargetException | IllegalAccessException | IllegalArgumentException | NoSuchMethodException | SecurityException e) {			
			return INDETERMINATE;
		}
	}
	
	static boolean isProcessed(Map<String, Object> properties) {
		Boolean result = (Boolean) properties.get(RESULT);
		// It can be set to false which means that content can't be parsed
		if (result != null)
			return true;
		return false;
	}

	static void fillContentProperties(InputSource input, Map<String, Object> properties) throws IOException {
		XMLRootHandler xmlHandler = new XMLRootHandler(true);
		try {
			if (!xmlHandler.parseContents(input)) {
				properties.put(RESULT, Boolean.FALSE);
				return;
			}
		} catch (SAXException e) {
			// we may be handed any kind of contents... it is normal we fail to parse
			properties.put(RESULT, Boolean.FALSE);
			return;
		} catch (ParserConfigurationException e) {
			// some bad thing happened - force this describer to be disabled
			String message = ContentMessages.content_parserConfiguration;
			RuntimeLog.log(new Status(IStatus.ERROR, ContentMessages.OWNER_NAME, 0, message, e));
			throw new RuntimeException(message);
		}
		String element = xmlHandler.getRootName();
		if (element != null)
			properties.put(ELEMENT, element);
		String dtd = xmlHandler.getDTD();
		if (dtd != null)
			properties.put(DTD, dtd);
		String namespace = xmlHandler.getRootNamespace();
		if (namespace != null)
			properties.put(NAMESPACE, namespace);
		properties.put(RESULT, Boolean.TRUE);
	}

	@Override
	public void setInitializationData(final IConfigurationElement config, final String propertyName, final Object data) throws CoreException {
		if (data instanceof String)
			elementsToFind = new QualifiedElement[] {new QualifiedElement((String) data)};
		else if (data instanceof Hashtable) {
			List<QualifiedElement> elements = null;

			// the describer parameters have to be read again, because "element" parameter can be specified multiple times
			// and the given hashtable carries only one of them
			IConfigurationElement describerElement = config.getChildren("describer")[0]; //$NON-NLS-1$
			IConfigurationElement[] params = describerElement.getChildren("parameter"); //$NON-NLS-1$
			String pname = null;
			for (IConfigurationElement param : params) {
				pname = param.getAttribute("name"); //$NON-NLS-1$
				if (ELEMENT_TO_FIND.equals(pname)) {
					if (elements == null)
						elements = new LinkedList<>();
					elements.add(new QualifiedElement(param.getAttribute("value"))); //$NON-NLS-1$
				}
			}

			List<QualifiedElement> qualifiedElements = new ArrayList<>();

			// create list of qualified elements
			if (elements != null) {
				for (QualifiedElement qualifiedElement : elements) {
					qualifiedElements.add(qualifiedElement);
				}
			}
			elementsToFind = qualifiedElements.toArray(new QualifiedElement[qualifiedElements.size()]);
		}

		if (elementsToFind.length == 0) {
			String message = NLS.bind(ContentMessages.content_badInitializationData, XMLRootElementContentDescriber2.class.getName());
			throw new CoreException(new Status(IStatus.ERROR, ContentMessages.OWNER_NAME, 0, message, null));
		}
	}

}
