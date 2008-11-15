package net.jjc1138.android.scrobbler;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.music.IMediaPlaybackService;

/**
 * This service connects to the Music application, gets its status, and then
 * broadcasts that in our format (to be received by StatusBroadcastReceiver).
 */
public class MusicStatusFetcher extends Service {
	int starts = 0;

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		
		++starts;
		
		bindService(new Intent().setClassName(
			"com.android.music", "com.android.music.MediaPlaybackService"),
			new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName comp, IBinder binder) {
				IMediaPlaybackService s =
					IMediaPlaybackService.Stub.asInterface(binder);
				
				Intent i = new Intent(StatusBroadcastReceiver.ACTION);
				try {
					i.putExtra("playing", s.isPlaying());
					i.putExtra("id", s.getAudioId());
				} catch (RemoteException e) {
					i.putExtra("playing", false);
				}
				MusicStatusFetcher.this.unbindService(this);
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
