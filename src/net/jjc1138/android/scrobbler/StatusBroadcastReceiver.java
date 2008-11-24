package net.jjc1138.android.scrobbler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StatusBroadcastReceiver extends BroadcastReceiver {
	public static final String ACTION_MUSIC_STATUS =
		"net.jjc1138.android.scrobbler.action.MUSIC_STATUS";

	@Override
	public void onReceive(Context context, Intent in) {
		Intent out = new Intent(context, ScrobblerService.class);
		out.putExtras(in);
		context.startService(out);
	}

}
