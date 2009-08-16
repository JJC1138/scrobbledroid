package net.jjc1138.android.scrobbler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MusicBroadcastReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		final Intent out = new Intent(context, MusicStatusFetcher.class);
		out.putExtra(MusicStatusFetcher.BROADCAST_ACTION, intent.getAction());
		context.startService(out);
	}

}
