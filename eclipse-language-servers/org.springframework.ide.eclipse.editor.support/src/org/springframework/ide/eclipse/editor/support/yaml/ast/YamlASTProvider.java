/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.yaml.ast;

import java.io.StringReader;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.yaml.snakeyaml.Yaml;

/**
 * Responsible for providing a AST for a document containing yml formatted text.
 *
 * @author Kris De Volder
 */
public class YamlASTProvider {

	private YamlFileAST cached = null;

	/**
	 * Tracks the source from which cached AST got parsed.
	 */
	private IDocument cachedFor = null;

	/**
	 * For cache invalidation
	 */
	private IDocumentListener listener = new IDocumentListener() {

		public void documentChanged(final DocumentEvent event) {
			Job job = new Job("Clear YamlASTProvider Cache") {
				protected IStatus run(IProgressMonitor monitor) {
					changed(event.getDocument());
					return Status.OK_STATUS;
				}
			};
			job.setPriority(Job.INTERACTIVE);
			job.setSystem(true);
			job.schedule();
		}
		public void documentAboutToBeChanged(DocumentEvent event) {
			//Don't care.
		}
	};

	private Yaml yaml;

	public YamlASTProvider(Yaml yaml) {
		this.yaml = yaml;
	}

	private synchronized void changed(IDocument doc) {
		if (doc == cachedFor) {
			doc.removeDocumentListener(listener);
			cachedFor = null;
			cached = null;
		}
	}

	public synchronized YamlFileAST getAST(IDocument doc) {
		if (doc == cachedFor) {
			return cached;
		} else {
			if (cachedFor != null) {
				cachedFor.removeDocumentListener(listener);
			}
			doc.addDocumentListener(listener);
			cached = new YamlFileAST(doc, yaml.composeAll(new StringReader(doc.get())));
			cachedFor = doc;
		}
		return cached;
	}

}
