/*******************************************************************************
 * Copyright (c) 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.test.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.springframework.ide.eclipse.boot.wizard.content.CodeSet;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.UIThreadDownloadDisallowed;

public class CopyFromFolder extends CodeSet {

	public class FileEntry extends CodeSetEntry {

		private File file;

		public FileEntry(File file) {
			this.file = file;
		}

		@Override
		public IPath getPath() {
			return new Path(file.toString()).makeRelativeTo(rootPath());
		}

		@Override
		public boolean isDirectory() {
			return false;
		}

		@Override
		public InputStream getData() throws IOException {
			return new FileInputStream(file);
		}

	}

	public class DirEntry extends CodeSetEntry {

		private File dir;

		public DirEntry(File dir) {
			this.dir = dir;
		}

		@Override
		public IPath getPath() {
			return new Path(dir.toString()).makeRelativeTo(rootPath()).makeAbsolute();
		}

		@Override
		public boolean isDirectory() {
			return true;
		}

		@Override
		public InputStream getData() throws IOException {
			throw new IOException("Not a file");
		}
	}

	private File root;

	private IPath rootPath() {
		return new Path(root.toString());
	}

	public CopyFromFolder(String name, File root) {
		super(name);
		this.root = root;
	}

	@Override
	public boolean exists() throws Exception {
		return root.exists();
	}

	@Override
	public boolean hasFile(IPath path) throws UIThreadDownloadDisallowed {
		return file(path).isFile();
	}

	private File file(IPath path) {
		return new File(rootPath().append(path).toString());
	}

	@Override
	public boolean hasFolder(IPath path) {
		return file(path).isDirectory();
	}

	@Override
	public <T> T each(Processor<T> processor) throws Exception {
		return each(root, processor);
	}

	private <T> T each(File target, Processor<T> processor) throws Exception {
		T result = null;
		if (target.isDirectory()) {
			result = processor.doit(new DirEntry(target));
			if (result != null) {
				return result;
			}
			for (File child : target.listFiles()) {
				result = each(child, processor);
				if (result != null) {
					return result;
				}
			}
			return result;
		} else if (target.isFile()) {
			return processor.doit(new FileEntry(target));
		} else {
			throw new IOException("What is this? Not a file, not a directory?"+target);
		}
	}

	@Override
	public <T> T readFileEntry(final String path, Processor<T> processor) throws Exception {
		File f = file(path);
		if (f.isFile()) {
			FileEntry entry = new FileEntry(f);
			return processor.doit(entry);
		} else {
			throw new IOException("Not a file: "+path);
		}
	}

	private File file(String path) {
		return null;
	}

}
