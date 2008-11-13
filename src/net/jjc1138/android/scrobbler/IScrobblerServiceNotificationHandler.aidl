package net.jjc1138.android.scrobbler;

interface IScrobblerServiceNotificationHandler {
	void stateChanged(
		int queueSize, boolean scrobbling, int lastScrobbleResult);
}
