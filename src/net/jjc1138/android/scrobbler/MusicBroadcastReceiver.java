package net.jjc1138.android.scrobbler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MusicBroadcastReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.v("Scrob", intent.toString());
		Log.v("ScrobE", intent.getExtras().keySet().toString());
		
		context.sendBroadcast(
			new Intent(ScrobblerConfig.UINOTIFICATION_ACTION));
	}

}
