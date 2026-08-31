package org.test;

import org.springframework.scheduling.annotation.Scheduled;

public class ConcatenatedCronScheduler {

	@Scheduled(cron = "0 0 " + "* * * *")
	public void performTask1() {
	}

	@Scheduled(cron = "0 0 " + CronConstants.HOURS_8_TO_10)
	public void performTask2() {
	}

}
