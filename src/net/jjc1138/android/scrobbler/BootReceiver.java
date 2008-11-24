package net.jjc1138.android.scrobbler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * This receiver acts as a bridge, because it takes the unprivileged
 * BOOT_COMPLETED intent and starts up our private service.
 */
public class BootReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		context.startService(new Intent(context, ScrobblerService.class));
	}
}
