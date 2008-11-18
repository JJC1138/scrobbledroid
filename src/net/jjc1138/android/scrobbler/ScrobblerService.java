package net.jjc1138.android.scrobbler;

import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import android.app.Service;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Handler;
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

	@Override
	public String toString() {
		// TODO Add metadata.
		return "Track " + id;
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
	// This is the maximum number of tracks that we can submit in one request:
	static final int MAX_SCROBBLE_TRACKS = 50;
	// This is the number of tracks that we will wait to have queued before we
	// scrobble. It can be larger or smaller than MAX_SCROBBLE_TRACKS.
	static final int SCROBBLE_BATCH_SIZE = MAX_SCROBBLE_TRACKS;
	// This is how long we will wait after music has stopped playing before
	// scrobbling.
	static final int SCROBBLE_WAITING_TIME_MINUTES = 3;

	static final String LOG_TAG = "ScrobbleDroid";
	static final String PREFS = "prefs";

	static final int OK = 0;
	static final int NOT_YET_ATTEMPTED = 1;
	static final int BANNED = 2;
	static final int BADAUTH = 3;
	static final int BADTIME = 4;
	static final int FAILED_NET = 5;
	static final int FAILED_OTHER = 6;

	final RemoteCallbackList<IScrobblerServiceNotificationHandler>
		notificationHandlers =
		new RemoteCallbackList<IScrobblerServiceNotificationHandler>();
	SharedPreferences prefs;

	private int lastScrobbleResult = NOT_YET_ATTEMPTED;
	private BlockingQueue<QueueEntry> queue =
		new LinkedBlockingQueue<QueueEntry>();

	private QueueEntry lastPlaying = null;
	private boolean lastPlayingWasPaused = true;
	private long lastPlayingTimePlayed = 0;
	private long lastResumedTime = -1;

	private ScrobbleThread scrobbleThread = null;
	private Handler handler;
	private boolean bound = false;
	// This is the time of the last "meaningful" event, i.e. a track that was
	// playing being paused, or vice-versa, or a new track being played. So this
	// is the last time the user actually did something (or accepted something
	// happening, as in the case of changing to the next track in a playlist).
	private long lastEventTime = -1;

	@Override
	public void onCreate() {
		super.onCreate();
		prefs = getSharedPreferences(PREFS, 0);
		handler = new Handler();
	}

	private boolean isScrobbling() {
		return scrobbleThread != null && scrobbleThread.inProgress();
	}

	synchronized void updateAllClients() {
		final int N = notificationHandlers.beginBroadcast();
		for (int i = 0; i < N; ++i) {
			updateClient(notificationHandlers.getBroadcastItem(i));
		}
		notificationHandlers.finishBroadcast();
	}

	synchronized void updateClient(IScrobblerServiceNotificationHandler h) {
		try {
			// As far as I can tell this doesn't have to be called from our main
			// event thread.
			h.stateChanged(queue.size(), isScrobbling(), lastScrobbleResult);
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
			handler.post(new Runnable() {
				@Override
				public void run() {
					stopIfIdle();
				}
			});
		}

		@Override
		public void prefsUpdated() throws RemoteException {
			// TODO Remove the cached session ID (if any) to force a
			// rehandshake on the next scrobble.
		}

		@Override
		public void startScrobble() throws RemoteException {
			scrobbleNow();
		}

	};

	@Override
	public IBinder onBind(Intent intent) {
		bound = true;
		return binder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		bound = false;
		return false;
	}

	private void newTrackStarted(Track t, long now) {
		this.lastPlaying = new QueueEntry(t, now);
		lastPlayingWasPaused = false;
		lastPlayingTimePlayed = 0;
		lastResumedTime = now;
		lastEventTime = now;
		Log.v(LOG_TAG, "New track started.");
	}

	private boolean playedWholeTrack() {
		// Put some wiggle room in to compensate for the imprecision of our
		// timekeeping.
		long v =
			lastPlayingTimePlayed - lastPlaying.getTrack().getLength();
		long a = Math.abs(v);
		if (a < 30000) {
			Log.d(LOG_TAG, "Whole track timing error: " + v);
		}
		return a < 3000;
	}

	private boolean playTimeEnoughForScrobble() {
		final long playTime = lastPlayingTimePlayed;
		// For debugging:
		//return playTime >= 5000;
		return playTime >= 30000 && ((playTime >= 240000) ||
			(playTime >= lastPlaying.getTrack().getLength() / 2));
	}

	private void handleIntent(Intent intent) {
		Log.v(LOG_TAG, "Status: " +
			((intent.getBooleanExtra("playing", false) ? "playing" : "stopped")
			+ " track " + intent.getIntExtra("id", -1)));
		
		if (!prefs.getBoolean("enable", true)) {
			return;
		}
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
						lastEventTime = now;
						lastPlayingWasPaused = false;
						Log.v(LOG_TAG, "Previously paused track resumed.");
					} else {
						if (playedWholeTrack() && playTimeEnoughForScrobble()) {
							queue.add(lastPlaying);
							newTrackStarted(t, now);
							Log.v(LOG_TAG, "Enqueued repeating track.");
							updatedQueue();
						} else {
							// lastPlaying track is still playing, but hasn't
							// gotten to the end yet (and so isn't repeating):
							// NOOP.
						}
					}
				} else {
					if (!lastPlayingWasPaused) {
						lastPlayingTimePlayed += now - lastResumedTime;
					}
					final String logState = lastPlayingWasPaused ?
						"paused" : "playing";
					if (playTimeEnoughForScrobble()) {
						queue.add(lastPlaying);
						Log.v(LOG_TAG,
							"Enqueued previously " + logState + " track.");
						updatedQueue();
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
				// A track was playing.
				lastPlayingTimePlayed += now - lastResumedTime;
				lastPlayingWasPaused = true;
				lastEventTime = now;
				Log.v(LOG_TAG, "Track paused/stopped. Total play time so far " +
					"is " + lastPlayingTimePlayed + ".");
				if (playedWholeTrack() && playTimeEnoughForScrobble()) {
					queue.add(lastPlaying);
					lastPlaying = null;
					lastPlayingTimePlayed = 0;
					Log.v(LOG_TAG, "Enqueued paused/stopped track.");
					updatedQueue();
				} else {
					// If the whole track wasn't played then that's okay: the
					// track will still be queued eventually by stopIfIdle()
					// below if it has played for long enough.
					//
					// We queue completed tracks now to make the UI more
					// intuitive. If a playlist ends then the last track will be
					// queued immediately.
					//
					// If we wanted to we could queue any track that has played
					// for long enough at this point. The reason we don't do
					// that is to avoid enqueuing duplicates when a track wasn't
					// really repeated. If we did enqueue partially played
					// tracks now then the following situation could occur:
					// 1) A track plays for 55% of it's play time.
					// 2) User pauses. The track gets enqueued and
					//    lastPlayingTimePlayed is reset to zero.
					// 3) User rewinds it back 10% so it is at 45%. We don't get
					//    informed about this type of event.
					// 4) User resumes.
					// 5) Track plays until the end. 55% of it has played so we
					//    assume that it was repeated. It gets enqueued again.
					// In fact the user had only listened to 110% of the track
					// so they probably don't want to scrobble it twice.
				}
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
		if (!lastPlayingWasPaused) {
			return;
		}
		if (isScrobbling()) {
			return;
		}
		
		if (!prefs.getBoolean("immediate", false)) {
			final long waitingTimeMillis =
				SCROBBLE_WAITING_TIME_MINUTES * 60 * 1000;
			if (System.currentTimeMillis() - lastEventTime <
				waitingTimeMillis) {
				
				// Check again later, because the user might start playing music
				// again soon.
				handler.postDelayed(new Runnable() {
					@Override
					public void run() {
						stopIfIdle();
					}
				}, waitingTimeMillis);
				return;
			}
		}
		
		// Check if the paused/stopped track should be scrobbled.
		if (lastPlaying != null && playTimeEnoughForScrobble()) {
			queue.add(lastPlaying);
			lastPlaying = null;
			lastPlayingTimePlayed = 0;
			Log.v(LOG_TAG, "Enqueued previously paused/stopped track.");
			updatedQueue();
		}
		if (!queue.isEmpty()) {
			scrobbleNow();
			return; // When the scrobble ends this method will be called again.
		}
		
		if (bound) {
			return;
		}
		
		// Not playing, queue empty, not scrobbling, and no clients connected:
		// it looks like we really are idle!
		
		// TODO Save the lastPlaying information (including
		// lastPlayingTimePlayed) and then stopSelf().
	}

	private boolean shouldScrobbleNow() {
		final int queueSize = queue.size();
		if (queueSize == 0) {
			return false;
		}
		if (prefs.getBoolean("immediate", false)) {
			return true;
		}
		if (queueSize >= SCROBBLE_BATCH_SIZE) {
			return true;
		}
		return false;
	}

	private void updatedQueue() {
		if (shouldScrobbleNow()) {
			scrobbleNow();
		}
		updateAllClients();
	}

	private class ScrobbleThread extends Thread {
		private boolean inProgress = true;

		@Override
		public void run() {
			QueueEntry e;
			while ((e = queue.peek()) != null) {
				updateAllClients(); // Update the number of tracks left.
				// TODO Make real:
				try {
					sleep(2500);
				} catch (InterruptedException e1) {}
				Log.v(LOG_TAG, "(Pretend) Scrobbling track: " +
					e.getTrack().toString());
				queue.remove();
			}
			lastScrobbleResult = OK;
			inProgress = false;
			updateAllClients();
			// TODO If it failed for a transient reason then post a delayed
			// scrobbleNow() to try again.
			handler.post(new Runnable() {
				@Override
				public void run() {
					stopIfIdle();
				}
			});
		}

		public boolean inProgress() {
			// You might think that we could just use Thread.isAlive() instead
			// of having this method. We use this method so that when we are
			// finished we can call updateAllClients() from this thread and have
			// it report that we have finished scrobbling even though the thread
			// hasn't actually terminated yet.
			return inProgress;
		}
	}

	synchronized private void scrobbleNow() {
		if (!isScrobbling()) {
			scrobbleThread = new ScrobbleThread();
			scrobbleThread.start();
			updateAllClients();
		}
	}

}
