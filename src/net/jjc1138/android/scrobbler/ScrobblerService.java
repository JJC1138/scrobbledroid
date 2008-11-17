package net.jjc1138.android.scrobbler;

import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import android.app.Service;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.util.Log;

class IncompleteMetadataException extends Exception {
	private static final long serialVersionUID = 1L;
}

class Track {
	public Track(Intent i, Context c) throws IncompleteMetadataException {
		id = i.getIntExtra("id", -1);
		
		if (id != -1) {
			// TODO Only fetch the columns we use.
			Cursor cur = c.getContentResolver().query(
				ContentUris.withAppendedId(
					MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
				null, null, null, null);
			try {
				if (cur == null) {
					throw new NoSuchElementException();
				}
				
				cur.moveToFirst();
				length = cur.getLong(cur.getColumnIndex(
					MediaStore.Audio.AudioColumns.DURATION));
			} finally {
				cur.close();
			}
		} else {
			// TODO Add support for fully specified metadata. Throw if it is
			// incomplete.
			throw new IncompleteMetadataException();
		}
	}

	public long getLength() {
		return length;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Track)) {
			return false;
		}
		Track other = (Track) o;
		if (id != -1) {
			return id == other.id;
		}
		// TODO Check other metadata fields.
		return false;
	}

	@Override
	public int hashCode() {
		return id;
	}

	private int id;
	private long length;
}

class QueueEntry {
	public QueueEntry(Track track, long startTime) {
		this.track = track;
		this.startTime = startTime;
	}

	public Track getTrack() {
		return track;
	}

	public long getStartTime() {
		return startTime;
	}

	private Track track;
	private long startTime;
}

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
	private BlockingQueue<QueueEntry> queue =
		new LinkedBlockingQueue<QueueEntry>();

	private QueueEntry lastPlaying = null;
	private boolean lastPlayingWasPaused = false;
	private long lastPlayingTimePlayed = 0;
	private long lastResumedTime = -1;

	synchronized void updateAllClients() {
		final int N = notificationHandlers.beginBroadcast();
		for (int i = 0; i < N; ++i) {
			updateClient(notificationHandlers.getBroadcastItem(i));
		}
		notificationHandlers.finishBroadcast();
	}

	synchronized void updateClient(IScrobblerServiceNotificationHandler h) {
		try {
			// TODO Does this have to be called from the main event thread?
			h.stateChanged(queue.size(), false, lastScrobbleResult);
		} catch (RemoteException e) {}
	}

	private final IScrobblerService.Stub binder = new IScrobblerService.Stub() {

		@Override
		public void registerNotificationHandler(
			IScrobblerServiceNotificationHandler h) throws RemoteException {

			notificationHandlers.register(h);
			updateClient(h);
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

	private void newTrackStarted(Track t, long now) {
		this.lastPlaying = new QueueEntry(t, now);
		lastPlayingWasPaused = false;
		lastPlayingTimePlayed = 0;
		lastResumedTime = now;
		Log.v(LOG_TAG, "New track started.");
	}

	private boolean playTimeEnoughForScrobble() {
		final long playTime = lastPlayingTimePlayed;
		return playTime >= 30000 && ((playTime >= 240000) ||
			(playTime >= lastPlaying.getTrack().getLength() / 2));
	}

	private void handleIntent(Intent intent) {
		Log.v(LOG_TAG, "Status: " +
			((intent.getBooleanExtra("playing", false) ? "playing" : "stopped")
			+ " track " + intent.getIntExtra("id", -1)));
		
		if (!intent.hasExtra("playing")) {
			// That one is mandatory.
			return;
		}
		Track t;
		try {
			t = new Track(intent, this);
		} catch (IncompleteMetadataException e) {
			return;
		} catch (NoSuchElementException e) {
			return;
		}
		long now = System.currentTimeMillis();
		
		if (intent.getBooleanExtra("playing", false)) {
			if (lastPlaying == null) {
				newTrackStarted(t, now);
			} else {
				if (lastPlaying.getTrack().equals(t)) {
					if (lastPlayingWasPaused) {
						lastResumedTime = now;
						lastPlayingWasPaused = false;
						Log.v(LOG_TAG, "Previously paused track resumed.");
					} else {
						// lastPlaying track is still playing: NOOP.
					}
				} else {
					if (!lastPlayingWasPaused) {
						lastPlayingTimePlayed += now - lastResumedTime;
					}
					final String logState = lastPlayingWasPaused ?
						"paused" : "playing";
					if (playTimeEnoughForScrobble()) {
						queue.add(lastPlaying);
						updatedQueue();
						Log.v(LOG_TAG,
							"Enqueued previously " + logState + " track.");
					} else {
						Log.v(LOG_TAG, "Previously " + logState +
							" track wasn't playing long enough to scrobble.");
					}
					newTrackStarted(t, now);
				}
			}
		} else {
			// Paused/stopped.
			if (lastPlaying == null || lastPlayingWasPaused) {
				// We weren't playing before and we aren't playing now: NOOP.
			} else {
				// A track is currently playing.
				lastPlayingTimePlayed += now - lastResumedTime;
				lastPlayingWasPaused = true;
				Log.v(LOG_TAG, "Track paused. Total play time so far is " +
					lastPlayingTimePlayed + ".");
			}
		}
		
		// TODO Make sure we're sensibly handling (maliciously) malformed
		// Intents.
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		
		handleIntent(intent);
		stopIfIdle();
	}

	private void stopIfIdle() {
		// TODO stopSelf() if not playing, queue empty and not scrobbling. Save
		// the lastPlaying information (including lastPlayingTimePlayed) before
		// stopping.
	}

	private void updatedQueue() {
		// TODO Launch scrobbling if appropriate.
		updateAllClients();
	}

}
