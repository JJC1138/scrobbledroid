package net.jjc1138.android.scrobbler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class MusicBroadcastReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		if (Build.VERSION.SDK_INT >= 9) {
			final Intent out =
				new Intent(StatusBroadcastReceiver.ACTION_MUSIC_STATUS);
			out.putExtras(intent);
			context.sendBroadcast(out);
		} else {
			final Intent out = new Intent(context, MusicStatusFetcher.class);
			out.putExtra(
				MusicStatusFetcher.BROADCAST_ACTION, intent.getAction());
			context.startService(out);
		}
	}

}
