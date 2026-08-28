/*******************************************************************************
 * Copyright (c) 2017 Spring IDE Developers
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Spring IDE Developers - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.test.util;

import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;

import reactor.core.publisher.Flux;

public class LiveExpToFlux {

//	private static final boolean DEBUG = (""+Platform.getLocation()).contains("kdvolder");
//
//	private static void debug(String string) {
//		if (DEBUG) {
//			System.out.println(string);
//		}
//	}

	/**
	 * Convert a LiveExp into a Flux. The flux delivers all non-null livexp values in its
	 * `onNext` event. The current value is sent immediately upon subscribing and subsequent
	 * `onNext` fire whenever the value changes.
	 * <p>
	 * The flux completes normally when the targetted LiveExp is disposed. (This means that
	 * when a LiveExp is already disposed, susbcribers will receive 'onComplete' event immediately.
	 * I.e. the subcriber will perceive the stream of livexp values as a empty stream.
	 */
	public static <T> Flux<T> toFlux(LiveExpression<T> exp) {
		return Flux.create(sink -> {
//			debug("Creating LiveExpFlux");
			ValueListener<T> valueListener = (e, v) -> {
//				debug("LiveExpFlux <- "+v);
				if (v != null) {
					sink.next(v);
				}
			};
			sink.onDispose(() -> exp.removeListener(valueListener));
			exp.addListener(valueListener);
			exp.onDispose((d) -> {
//				debug("LiveExpFlux LiveExp DISPOSED");
				sink.complete();
			});
		});
	}

}
