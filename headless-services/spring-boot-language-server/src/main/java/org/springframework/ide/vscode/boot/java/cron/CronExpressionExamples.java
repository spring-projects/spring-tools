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
package org.springframework.ide.vscode.boot.java.cron;

import java.util.List;

/**
 * Examples of CRON expressions offered by the completion proposals in Java code and in properties and YAML files.
 *
 * @author Alex Boyko
 */
public class CronExpressionExamples {

	public record Example(String expression, String description) {
	}

	public static final List<Example> EXAMPLES = List.of(
			new Example("0 0 * * * *", "every hour"),
			new Example("0 0 * * * 1-5", "every hour every day between Monday and Friday"),
			new Example("0 * * * * *", "every minute"),
			new Example("0 */5 * * * *", "every 5 minutes"),
			new Example("0 0 */6 * * *", "every 6 hours at minute 0"),
			new Example("0 0 * * * SUN", "every hour at Sunday day"),
			new Example("0 0 0 * * *", "at 00:00"),
			new Example("0 0 0 * * SAT,SUN", "at 00:00 on Saturday and Sunday"),
			new Example("0 0 0 * * 6,0", "at 00:00 at Saturday and Sunday days"),
			new Example("0 0 0 1-7 * SUN", "at 00:00 every day between 1 and 7 at Sunday day"),
			new Example("0 0 0 1 * *", "at 00:00 at 1 day"),
			new Example("0 0 0 1 1 *", "at 00:00 at 1 day at January month"),
			new Example("0 0 8-18 * * *", "every hour between 8 and 18"),
			new Example("0 0 9 * * MON", "at 09:00 at Monday day"),
			new Example("0 0 10 * * *", "at 10:00"),
			new Example("0 30 9 * JAN MON", "at 09:30 at January month at Monday day"),
			new Example("10 * * * * *", "every minute at second 10"),
			new Example("0 0 8-10 * * *", "every hour between 8 and 10"),
			new Example("0 0/30 8-10 * * *", "every 30 minutes every hour between 8 and 10"),
			new Example("0 0 0 L * *", " at 00:00 last day of month"),
			new Example("0 0 0 1W * *", "at 00:00 the nearest weekday to the 1 of the month"),
			new Example("0 0 0 * * THUL", "at 00:00 last Thursday of every month"),
			new Example("0 0 0 ? * 5#2", "at 00:00 Friday 2 of every month"),
			new Example("0 0 0 ? * MON#1", "at 00:00 Monday 1 of every month")
	);

	private CronExpressionExamples() {
	}

}
