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

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntaxApiReader;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntaxReader;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntaxReaders;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinTreeSitterReader;

/**
 * Benchmark and checks for the Kotlin parsers, see README.md.
 * <pre>
 * Bench run &lt;parser&gt; &lt;corpus directory&gt; &lt;passes&gt; [threads]   time, allocation and memory of reading a corpus
 * Bench verify &lt;corpus directory&gt;                         the parsers read every file the same way
 * Bench verify-skip &lt;corpus directory&gt;...                 skipping the bodies of functions changes nothing
 * </pre>
 * The parser is a name of {@link KotlinSyntaxReaders}, or {@code tree-sitter-bodies} and
 * {@code syntax-api-bodies} for those parsers with the bodies of functions parsed. Memory is measured in the process that runs, so run one
 * parser and one corpus per JVM.
 *
 * @author Martin Lippert
 */
public class Bench {

	public static void main(String[] args) throws Exception {
		switch (args[0]) {
		case "run" -> run(args[1], Path.of(args[2]), Integer.parseInt(args[3]), args.length > 4 ? Integer.parseInt(args[4]) : 1);
		case "verify" -> verify(Path.of(args[1]));
		case "verify-skip" -> verifySkip(args);
		default -> throw new IllegalArgumentException(args[0]);
		}
	}

	private static KotlinSyntaxReader reader(String parser) {
		return switch (parser) {
		case "tree-sitter-bodies" -> new KotlinTreeSitterReader(false);
		case "syntax-api-bodies" -> new KotlinSyntaxApiReader(false);
		default -> KotlinSyntaxReaders.create(parser);
		};
	}

	private static List<Path> files(Path directory) throws Exception {
		try (Stream<Path> files = Files.list(directory)) {
			return files.sorted().collect(Collectors.toList());
		}
	}

	// ---- run

	static long usedHeap() {
		for (int i = 0; i < 3; i++) {
			System.gc();
			try {
				Thread.sleep(100);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		Runtime runtime = Runtime.getRuntime();
		return runtime.totalMemory() - runtime.freeMemory();
	}

	/** a value of /proc/self/status in kB: VmRSS (the process memory now), VmHWM (its peak) */
	static long status(String key) throws Exception {
		for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
			if (line.startsWith(key)) {
				return Long.parseLong(line.replaceAll("[^0-9]", ""));
			}
		}
		return -1;
	}

	static long allocatedBytes() {
		return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getThreadAllocatedBytes(Thread.currentThread().threadId());
	}

	static double mb(long bytes) {
		return bytes / 1048576.0;
	}

	private static void run(String parser, Path corpus, int passes, int threads) throws Exception {
		List<Path> files = files(corpus);
		String[] texts = new String[files.size()];
		long chars = 0;
		for (int i = 0; i < texts.length; i++) {
			texts[i] = Files.readString(files.get(i));
			chars += texts[i].length();
		}

		long heapBase = usedHeap();
		long rssBase = status("VmRSS");
		KotlinSyntaxReader reader = reader(parser);

		long start = System.nanoTime();
		reader.read("First.kt", texts[0]);
		double initMs = (System.nanoTime() - start) / 1e6;
		long rssAfterInit = status("VmRSS");
		long heapAfterInit = usedHeap();

		List<Object> retained = new ArrayList<>();
		List<Double> passMs = new ArrayList<>();
		long allocatedFirstPass = 0;
		for (int pass = 0; pass < passes; pass++) {
			long allocatedBefore = allocatedBytes();
			start = System.nanoTime();
			for (int i = 0; i < texts.length; i++) {
				Object result = reader.read("F" + i + ".kt", texts[i]);
				if (pass == 0) {
					retained.add(result);
				}
			}
			passMs.add((System.nanoTime() - start) / 1e6);
			if (pass == 0) {
				allocatedFirstPass = allocatedBytes() - allocatedBefore;
			}
		}
		long heapWithResults = usedHeap();
		long rssAfter = status("VmRSS");
		long peakRss = status("VmHWM");
		retained = null;
		long heapWithoutResults = usedHeap();

		double parallelMs = -1;
		if (threads > 1) {
			ExecutorService executor = Executors.newFixedThreadPool(threads);
			for (int repetition = 0; repetition < 2; repetition++) {
				start = System.nanoTime();
				List<Future<?>> futures = new ArrayList<>();
				for (int i = 0; i < texts.length; i++) {
					int index = i;
					futures.add(executor.submit(() -> reader.read("F" + index + ".kt", texts[index])));
				}
				for (Future<?> future : futures) {
					future.get();
				}
				parallelMs = (System.nanoTime() - start) / 1e6;
			}
			executor.shutdown();
		}

		double warm = Collections.min(passMs.subList(Math.max(1, passes - 3), passes));
		System.out.printf("RESULT parser=%s files=%d chars=%.2fM initMs=%.0f firstPassMs=%.0f warmPassMs=%.0f msPerFileWarm=%.3f MBperSec=%.1f allocPerFileKB=%.0f%n",
				parser, texts.length, chars / 1e6, initMs, passMs.get(0), warm, warm / texts.length, chars / 1e6 / (warm / 1000), allocatedFirstPass / 1024.0 / texts.length);
		System.out.printf("MEMORY parser=%s parserHeapMB=%.1f resultsHeapMB=%.1f rssBaseMB=%.0f rssAfterInitMB=%.0f rssAfterMB=%.0f peakRssMB=%.0f parallel%dMs=%.0f%n",
				parser, mb(heapWithoutResults - heapBase), mb(heapWithResults - heapWithoutResults), rssBase / 1024.0, rssAfterInit / 1024.0, rssAfter / 1024.0,
				peakRss / 1024.0, threads, parallelMs);
	}

	// ---- checks

	/**
	 * What the parsers differ in on purpose: the range of a declaration next to comments (the PSI
	 * parser binds them to the declaration), everything else has to be the same.
	 */
	static String withoutDeclarationRanges(String syntax) {
		return syntax.replaceAll("(Function\\[name=[^,]*, )range=Range\\[start=\\d+, length=\\d+\\]", "$1range=*")
				.replaceAll("(ClassDeclaration\\[kind=\\w+, name=[^,]*, )range=Range\\[start=\\d+, length=\\d+\\]", "$1range=*");
	}

	private static void verify(Path corpus) throws Exception {
		KotlinSyntaxReader psi = KotlinSyntaxReaders.create(KotlinSyntaxReaders.PSI);
		boolean failed = false;

		for (String name : KotlinSyntaxReaders.names()) {
			if (name.equals(KotlinSyntaxReaders.PSI)) {
				continue;
			}

			// tree-sitter does not bind comments to declarations the way PSI does, the parser that has the grammar of PSI reads exactly the same
			boolean exact = name.equals(KotlinSyntaxReaders.SYNTAX_API);
			KotlinSyntaxReader parser = KotlinSyntaxReaders.create(name);
			int same = 0;
			int different = 0;

			for (Path file : files(corpus)) {
				String text = Files.readString(file);
				String expected = psi.read("a.kt", text).toString();
				String actual = parser.read("a.kt", text).toString();
				if (exact ? expected.equals(actual) : withoutDeclarationRanges(expected).equals(withoutDeclarationRanges(actual))) {
					same++;
				}
				else if (++different <= 3) {
					System.out.println("DIFFERENT " + name + " " + file);
				}
			}
			System.out.println("VERIFY " + corpus.getFileName() + " " + name + (exact ? " (exactly)" : "") + " identical=" + same + " different=" + different);
			failed |= different > 0;
		}
		if (failed) {
			System.exit(1);
		}
	}

	private static void verifySkip(String[] args) throws Exception {
		List<KotlinSyntaxReader[]> pairs = List.of(new KotlinSyntaxReader[] { new KotlinTreeSitterReader(false), new KotlinTreeSitterReader(true) },
				new KotlinSyntaxReader[] { new KotlinSyntaxApiReader(false), new KotlinSyntaxApiReader(true) });
		int same = 0;
		int different = 0;

		for (int i = 1; i < args.length; i++) {
			for (Path file : files(Path.of(args[i]))) {
				String text = Files.readString(file);
				for (KotlinSyntaxReader[] pair : pairs) {
					if (pair[0].read("a.kt", text).equals(pair[1].read("a.kt", text))) {
						same++;
					}
					else if (++different <= 3) {
						System.out.println("DIFFERENT " + pair[0].getClass().getSimpleName() + " " + file);
					}
				}
			}
		}
		System.out.println("VERIFY-SKIP identical=" + same + " different=" + different);
		if (different > 0) {
			System.exit(1);
		}
	}

}
