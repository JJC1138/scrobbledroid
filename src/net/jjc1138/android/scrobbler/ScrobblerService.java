package net.jjc1138.android.scrobbler;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

public class ScrobblerService extends Service {
	static final String LOG_TAG = "ScrobbleDroid";
	static final int SUCCESSFUL = 0;
	static final int NOT_YET_ATTEMPTED = 1;
	static final int FAILED_AUTH = 2;
	static final int FAILED_NET = 3;
	static final int FAILED_OTHER = 4;
	
	final RemoteCallbackList<IScrobblerServiceNotificationHandler>
		notificationHandlers =
		new RemoteCallbackList<IScrobblerServiceNotificationHandler>();
	
	private int lastScrobbleResult = NOT_YET_ATTEMPTED;
	private Queue<Object> queue = new LinkedBlockingQueue<Object>();
	
	private final IScrobblerService.Stub binder = new IScrobblerService.Stub() {

		@Override
		public void registerNotificationHandler(
			IScrobblerServiceNotificationHandler h) throws RemoteException {

			notificationHandlers.register(h);
			synchronized (ScrobblerService.this) {
				h.stateChanged(queue.size(), false, lastScrobbleResult);
			}
		}

		@Override
		public void unregisterNotificationHandler(
			IScrobblerServiceNotificationHandler h) throws RemoteException {

			notificationHandlers.unregister(h);
		}

		@Override
		public void prefsUpdated() throws RemoteException {
			// TODO Auto-generated method stub
		}

		@Override
		public void startScrobble() throws RemoteException {
			// TODO Auto-generated method stub
		}
		
	};
	
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		Log.v(LOG_TAG,
			((intent.getBooleanExtra("playing", false) ? "playing" : "stopped")
			+ " track " + intent.getIntExtra("trackID", -1)));
	}

}
