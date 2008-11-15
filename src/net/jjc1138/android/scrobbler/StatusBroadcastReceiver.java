package net.jjc1138.android.scrobbler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StatusBroadcastReceiver extends BroadcastReceiver {
	public static final String ACTION = "net.jjc1138.android.musicplayerstatus";

	@Override
	public void onReceive(Context context, Intent in) {
		Intent out = new Intent(context, ScrobblerService.class);
		out.putExtras(in);
		context.startService(out);
	}

}
