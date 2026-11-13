/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jetbrains.kotlin.com.intellij.psi.PsiElement;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtPsiFactory;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinPsiReader;

/**
 * The PSI reader with the bodies of functions parsed as well. PSI creates the bodies lazily, and
 * the reader never asks for them, so this walks the whole PSI tree of a file before the reader
 * runs, which parses all of it. It uses the reader through reflection, to run exactly its code.
 * <pre>
 * BenchPsiReader lazy|full &lt;corpus directory&gt; &lt;passes&gt;
 * </pre>
 *
 * @author Martin Lippert
 */
public class BenchPsiReader {

	private static void parseEverything(PsiElement element) {
		for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
			parseEverything(child);
		}
	}

	public static void main(String[] args) throws Exception {
		boolean full = args[0].equals("full");
		List<Path> files;
		try (Stream<Path> stream = Files.list(Path.of(args[1]))) {
			files = stream.sorted().collect(Collectors.toList());
		}
		int passes = Integer.parseInt(args[2]);
		String[] texts = new String[files.size()];
		long chars = 0;
		for (int i = 0; i < texts.length; i++) {
			texts[i] = Files.readString(files.get(i));
			chars += texts[i].length();
		}

		KotlinPsiReader reader = new KotlinPsiReader();
		reader.read("First.kt", texts[0]); // sets up the parser

		Field factoryField = KotlinPsiReader.class.getDeclaredField("psiFactory");
		factoryField.setAccessible(true);
		KtPsiFactory factory = (KtPsiFactory) factoryField.get(reader);

		Class<?> readerClass = Class.forName(KotlinPsiReader.class.getName() + "$Reader");
		Class<?> mapperClass = Class.forName(KotlinPsiReader.class.getPackageName() + ".OffsetMapper");
		Constructor<?> mapperConstructor = mapperClass.getDeclaredConstructor(String.class);
		mapperConstructor.setAccessible(true);
		Constructor<?> readerConstructor = readerClass.getDeclaredConstructor(mapperClass, int.class);
		readerConstructor.setAccessible(true);
		Method read = readerClass.getDeclaredMethod("read", KtFile.class);
		read.setAccessible(true);
		Method normalizedText = mapperClass.getDeclaredMethod("normalizedText");
		normalizedText.setAccessible(true);

		List<Double> passMs = new ArrayList<>();
		long allocatedFirstPass = 0;
		for (int pass = 0; pass < passes; pass++) {
			long allocatedBefore = Bench.allocatedBytes();
			long start = System.nanoTime();
			for (int i = 0; i < texts.length; i++) {
				Object mapper = mapperConstructor.newInstance(texts[i]);
				KtFile file = factory.createFile("F" + i + ".kt", (String) normalizedText.invoke(mapper));
				if (full) {
					parseEverything(file);
				}
				read.invoke(readerConstructor.newInstance(mapper, texts[i].length()), file);
			}
			passMs.add((System.nanoTime() - start) / 1e6);
			if (pass == 0) {
				allocatedFirstPass = Bench.allocatedBytes() - allocatedBefore;
			}
		}

		double warm = Collections.min(passMs.subList(Math.max(1, passes - 3), passes));
		System.out.printf("RESULT parser=psi-%s files=%d chars=%.2fM firstPassMs=%.0f warmPassMs=%.0f msPerFileWarm=%.3f MBperSec=%.1f allocPerFileKB=%.0f%n",
				full ? "bodies" : "lazy", texts.length, chars / 1e6, passMs.get(0), warm, warm / texts.length, chars / 1e6 / (warm / 1000),
				allocatedFirstPass / 1024.0 / texts.length);
		System.out.printf("MEMORY parser=psi-%s rssAfterMB=%d peakRssMB=%d%n", full ? "bodies" : "lazy", Bench.status("VmRSS") / 1024, Bench.status("VmHWM") / 1024);
	}

}
