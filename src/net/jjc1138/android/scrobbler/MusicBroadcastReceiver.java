package net.jjc1138.android.scrobbler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MusicBroadcastReceiver extends BroadcastReceiver {
	static final String LOG_TAG = ScrobblerService.LOG_TAG + "R";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		Log.v(LOG_TAG, intent.toString());
	}

}
