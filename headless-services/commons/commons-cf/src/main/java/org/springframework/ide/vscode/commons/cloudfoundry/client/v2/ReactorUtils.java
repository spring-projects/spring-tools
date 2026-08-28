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
package org.springframework.ide.vscode.commons.cloudfoundry.client.v2;

import java.io.IOException;
import java.time.Duration;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.cloudfoundry.client.v2.CancelationTokens.CancelationToken;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * @author Kris De Volder
 */
public class ReactorUtils {
	
	private static final Logger log = LoggerFactory.getLogger(ReactorUtils.class);

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(45); // reflects default timeout of Mono.block in reactor 2.x.
	public static boolean DUMP_STACK_ON_TIMEOUT = false;

	// TODO: uncommented when cancellation is handled in vscode
//	/**
//	 * Convert a {@link CancelationToken} into a Mono that raises
//	 * an {@link OperationCanceledException} when the token is canceled.
//	 */
//	public static <T> Mono<T> toMono(CancelationToken cancelToken) {
//		return Mono.delay(Duration.ofSeconds(1))
//		.then((ping) ->
//			cancelToken.isCanceled()
//				? Mono.<T>error(new OperationCanceledException())
//				: Mono.empty()
//		)
//		.repeatWhenEmpty((x) -> x);
//	}

	/**
	 * Similar to Mono.get but logs a more traceable version of the exception to Eclipse's error
	 * log before 'rethrowing' it.
	 * <p>
	 * This is useful because the actual exception is pretty hard to trace. It doesn't even 'point'
	 * to the line where 'get' was called.
	 */
	public static <T> T get(Mono<T> mono) throws Exception {
		try {
			return mono.block(DEFAULT_TIMEOUT);
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	/**
	 * Similar to Mono.get but logs a more traceable version of the exception to Eclipse's error
	 * log before 'rethrowing' it.
	 * <p>
	 * This is useful because the actual exception is pretty hard to trace. It doesn't even 'point'
	 * to the line where 'get' was called.
	 */
	public static <T> T get(Duration timeout, CancelationToken cancelationToken, Mono<T> mono) throws Exception {
		try {
			return mono
					// TODO: uncomment when cancellation properly supported in vscode
//					Mono
//					.first(mono, 
//					toMono(cancelationToken))
//			.otherwise(errorFilter(cancelationToken))
			.block(timeout);
		} catch (Exception e) {
			log.error("", e);
			throw new IOException(e);
		}
	}

	public static <T> List<T> get(Duration t, Mono<List<T>> m) throws IOException {
		try {
			return m.block(t);
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	private static void dumpStacks() {
		if (DUMP_STACK_ON_TIMEOUT) {
			System.out.println(getStackDumps().toString());
		}
	}

	/**
	 * A 'filter' to use as a Mono.otherwise hanlder. It transforms any exception into {@link OperationCanceledException}
	 * when cancelationToken has been canceled.
	 */
	private static <T> Function<Throwable, Mono<T>> errorFilter(CancelationToken cancelationToken) {
//		return (Throwable e) -> cancelationToken.isCanceled()?Mono.error(new OperationCanceledException()):Mono.error(e);
		return (Throwable e) -> Mono.error(e);

	}

	/**
	 * Deprecated because this is really the same as Mono.justOrEmpty, so use that instead.
	 */
	@Deprecated
	public static <T> Mono<T> just(T it) {
		return it == null ? Mono.empty() : Mono.just(it);
	}

	/**
	 * @return A function that can be passed to Mono.otherwise to convert a specific exception type into
	 * Mono.empty().
	 */
	public static <T> Function<Throwable, Mono<T>> suppressException(Class<? extends Throwable> exceptionType) {
		return (Throwable caught) -> {
			if (exceptionType.isAssignableFrom(caught.getClass())) {
				return Mono.empty();
			} else {
				return Mono.error(caught);
			}
		};
	}

	/**
	 * Build a Mono<Void> that executes a given number of Mono<Void> one after the
	 * other.
	 */
	@SafeVarargs
	public static Mono<Void> sequence(Mono<Void>... tasks) {
		Mono<Void> seq = Mono.empty();
		for (Mono<Void> t : tasks) {
			seq = seq.then(t);
		}
		return seq;
	}

	/**
	 * Execute a bunch of mono in parallel. All monos are executed to completion (rather than canceled early
	 * when one of them fails)
	 * <p>
	 * When at least one operation has failed then, upon completion or failure of the last Mono we guarantee that at least
	 * one of the exceptions is propagated.
	 */
	public static Mono<Void> safeMerge(Flux<Mono<Void>> operations, int concurrency) {
		AtomicReference<Throwable> failure =  new AtomicReference<>(null);
		return Flux.merge(
			operations
			.map((Mono<Void> op) -> {
				return op.onErrorResume((e) -> {
					failure.compareAndSet(null, e);
					return Mono.empty();
				});
			}),
			concurrency //limit concurrency otherwise troubles (flooding/choking request broker?)
		)
		.then(Mono.defer(() -> {
			Throwable error = failure.get();
			if (error != null) {
				return Mono.error(error);
			} else {
				return Mono.empty();
			}
		}));
	}

	/**
	 * Attach a timestamp to each element in a Stream
	 */
	public static <T> Flux<Tuple2<T,Long>> timestamp(Flux<T> stream) {
		return stream.map((e) -> Tuples.of(e, System.currentTimeMillis()));
	}

	/**
	 * Sorts the elements in a flux in a moving time window. I.e. this assumes element order may be
	 * scrambled but the scrambling has a certain 'time localilty' to it. So we only need to consider
	 * sorting of elements that arrive 'close to eachother'.
	 * <p>
	 * WARNING: The returned flux is intended for a single subscriber. It only maintains a
	 * single buffer for sorting stream elements. This buffer is consumed when elements
	 * are released to any subscriber. Therefore if one subscriber received a element it is gone
	 * from the buffer and will not be delivered to the other subscribers.
	 *
	 * @param stream The stream to be sorted
	 * @param comparator Compare function to sort with
	 * @param bufferTime The 'window' of time beyond which we don't need to compare elements.
	 */
	public static <T> Flux<T> sort(Flux<T> stream, Comparator<T> comparator, Duration bufferTime) {

		class SorterAccumulator {

			final PriorityQueue<Tuple2<T, Long>> holdingPen = new PriorityQueue<>((Tuple2<T, Long> o1, Tuple2<T, Long> o2) -> {
				return comparator.compare(o1.getT1(), o2.getT1());
			});

			final Flux<T> released = Flux.fromIterable(() -> new Iterator<T>() {
				@Override
				public boolean hasNext() {
					Tuple2<T, Long> nxt;
					synchronized (holdingPen) {
						nxt = holdingPen.peek();
					}
					return nxt != null && isOldEnough(nxt);
				}

				private boolean isOldEnough(Tuple2<T, Long> nxt) {
					long age = System.currentTimeMillis() - nxt.getT2();
					return age > bufferTime.toMillis();
				}

				@Override
				public T next() {
					synchronized (holdingPen) {
						return holdingPen.remove().getT1();
					}
				}
			});

			public SorterAccumulator next(Flux<Tuple2<T, Long>> window) {
				window.subscribe((e) -> {
					synchronized (holdingPen) {
						holdingPen.add(e);
					}
				});
				return this;
			}

			public Flux<T> getReleased() {
				return released;
			}

			public Publisher<? extends T> drain() {
				return Flux.fromIterable(holdingPen)
				.map(Tuple2::getT1);
			}
		}

		SorterAccumulator sorter = new SorterAccumulator();
		return timestamp(stream)
		.window(bufferTime)
		.scan(sorter, SorterAccumulator::next)
		.concatMap(SorterAccumulator::getReleased)
		.concatWith(sorter.drain());
	}

	protected static StringBuffer getStackDumps() {
		StringBuffer sb = new StringBuffer();
		Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
		for (Map.Entry<Thread, StackTraceElement[]> entry : traces.entrySet()) {
			sb.append(entry.getKey().toString());
			sb.append("\n");
			for (StackTraceElement element : entry.getValue()) {
				sb.append("  ");
				sb.append(element.toString());
				sb.append("\n");
			}
			sb.append("\n");
		}
		return sb;
	}


	/**
	 * Connect a mono to a CompletableFuture so that the result of the mono
	 * can be retrieved from the {@link CompletableFuture} by calling it's 'get'
	 * method.
	 * 
	 * WARNING: this method looks like it has a bug and doesn't complete the
	 * future for a Mono.empty() case (i.e. a Mono that terminates without either
	 * a onNext or a onComplete call.
	 */
	public static <T> void completeWith(CompletableFuture<T> future, Mono<T> mono) {
		mono.doOnNext((T v) -> {
			future.complete(v);
		})
		.doOnError((Throwable e) -> {
			future.completeExceptionally(e);
		})
		.subscribeOn(Schedulers.boundedElastic())
		.subscribe();
	}
}
