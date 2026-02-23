import { NotificationType } from "vscode-languageclient";

/**
 * Common information provided by all live process notifications, for all types
 * of events and for all types of processes.
 */
export interface LiveProcess {
	type: string;
	processKey: string;
	processName: string;
}

/**
 * Information returned by notification for updated log level for the live process
 */
export interface LiveProcessUpdatedLogLevel {
	type: string;
	processKey: string;
	processName: string;
    packageName: string;
    effectiveLevel: string;
    configuredLevel: string;
}

/**
 * Specialized interface for type 'local' LiveProcess.
 */
export interface LocalLiveProcess extends LiveProcess {
	type: "local"
	pid: string
}

export interface IndexUpdateDetails {
    affectedProjects?: string[]
}

export const LiveProcessConnectedNotification =
	new NotificationType<LiveProcess>('sts/liveprocess/connected');

export const LiveProcessDisconnectedNotification =
	new NotificationType<LiveProcess>('sts/liveprocess/disconnected');

export const LiveProcessUpdatedNotification =
	new NotificationType<LiveProcess>('sts/liveprocess/updated');

export const LiveProcessGcPausesMetricsUpdatedNotification =
	new NotificationType<LiveProcess>('sts/liveprocess/gcpauses/metrics/updated');

export const LiveProcessMemoryMetricsUpdatedNotification =
	new NotificationType<LiveProcess>('sts/liveprocess/memory/metrics/updated');

export const SpringIndexUpdatedNotification =
	new NotificationType<IndexUpdateDetails>('spring/index/updated');

export const LiveProcessLogLevelUpdatedNotification =
	new NotificationType<LiveProcessUpdatedLogLevel>('sts/liveprocess/loglevel/updated');
