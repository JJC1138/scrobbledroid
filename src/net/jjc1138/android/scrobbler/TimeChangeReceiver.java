package net.jjc1138.android.scrobbler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * This receiver acts as a bridge, because it takes the unprivileged
 * time change intents and notifies our private service.
 */
public class TimeChangeReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		Intent i = new Intent(context, ScrobblerService.class);
		i.setAction(intent.getAction());
		context.startService(i);
	}
}
