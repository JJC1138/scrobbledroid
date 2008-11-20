package net.jjc1138.android.scrobbler;

import net.jjc1138.android.scrobbler.IScrobblerServiceNotificationHandler;

interface IScrobblerService {
	void registerNotificationHandler(IScrobblerServiceNotificationHandler h);
	void unregisterNotificationHandler(IScrobblerServiceNotificationHandler h);

	void startScrobble();
}
