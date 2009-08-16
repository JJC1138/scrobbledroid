package net.jjc1138.android.scrobbler;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

/**
 * This service connects to the Music application, gets its status, and then
 * broadcasts that in our format (to be received by StatusBroadcastReceiver).
 */
public class MusicStatusFetcher extends Service {
	static final String FROM_MUSIC_STATUS_FETCHER =
		"net.jjc1138.android.scrobbler.from_music_status_fetcher";
	static final String BROADCAST_ACTION =
		"net.jjc1138.android.scrobbler.broadcast_action";

	private int starts = 0;

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		
		++starts;
		
		final String broadcastAction = intent.getStringExtra(BROADCAST_ACTION);
		final boolean htc;
		final String vendor;
		if (broadcastAction.startsWith("com.htc.")) {
			vendor = "htc";
			htc = true;
		} else {
			vendor = "android";
			htc = false;
		}
		bindService(new Intent().setClassName(
			"com." + vendor + ".music",
			"com." + vendor + ".music.MediaPlaybackService"),
			new ServiceConnection() {

			private void getDetails(
				final Intent out, final IBinder binder) {
				
				com.android.music.IMediaPlaybackService s =
					com.android.music.IMediaPlaybackService.Stub.asInterface(
						binder);
				try {
					out.putExtra("playing", s.isPlaying());
					out.putExtra("id", s.getAudioId());
				} catch (RemoteException e) {
					out.putExtra("playing", false);
				}
			}

			private void getHTCDetails(
				final Intent out, final IBinder binder) {
				
				com.htc.music.IMediaPlaybackService s =
					com.htc.music.IMediaPlaybackService.Stub.asInterface(
						binder);
				try {
					out.putExtra("playing", s.isPlaying());
					out.putExtra("id", s.getAudioId());
				} catch (RemoteException e) {
					out.putExtra("playing", false);
				}
			}

			@Override
			public void onServiceConnected(ComponentName comp, IBinder binder) {
				Intent i = new Intent(
					StatusBroadcastReceiver.ACTION_MUSIC_STATUS);
				// I tried doing this with reflection, but it required a whole
				// bunch of ugly calls:
				if (htc) {
					getHTCDetails(i, binder);
				} else {
					getDetails(i, binder);
				}
				MusicStatusFetcher.this.unbindService(this);
				i.putExtra(FROM_MUSIC_STATUS_FETCHER, true);
				i.putExtra(BROADCAST_ACTION, broadcastAction);
				sendBroadcast(i);
				
				--starts;
				// If starts > 0 then this service was started more than once
				// and this onServiceConnected() method will be called again in
				// a moment.
				if (starts == 0) {
					MusicStatusFetcher.this.stopSelf();
				}
			}

			@Override
			public void onServiceDisconnected(ComponentName comp) {}
		}, 0);
	}

	@Override
	public IBinder onBind(Intent intent) {
		// Binding to this service is not used/allowed.
		return null;
	}

}
