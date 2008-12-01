package net.jjc1138.android.scrobbler;

import java.net.URLEncoder;
import java.text.ChoiceFormat;
import java.text.MessageFormat;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ScrobblerConfig extends Activity {
	private SharedPreferences prefs;
	private SharedPreferences unsaved;

	private CheckBox enable;
	private CheckBox immediate;
	private EditText username;
	private EditText password;

	private TextView sign_up;
	private TextView view_user_page;

	private LinearLayout settingsChanged;
	private TextView scrobble_user_error;
	private TextView update_link;
	private TextView queue_status;
	private TextView scrobble_when;
	private Button scrobble_now;
	private TextView scrobble_status;

	private String scrobbleWaiting;

	private final Handler handler = new Handler();

	private static void show(View v) {
		v.setVisibility(View.VISIBLE);
	}

	private static void hide(View v) {
		v.setVisibility(View.GONE);
	}

	private final IScrobblerServiceNotificationHandler.Stub notifier =
		new IScrobblerServiceNotificationHandler.Stub() {
			@Override
			public void stateChanged(final int queueSize,
				final boolean scrobbling, final int lastScrobbleResult)
				
				throws RemoteException {
				
				handler.post(new Runnable() {
					@Override
					public void run() {
						queue_status.setText(MessageFormat.format(
							new ChoiceFormat(getString(R.string.tracks_ready))
								.format(queueSize), queueSize));
						
						// This is perhaps a tad verbose, but at least it's easy
						// to read:
						
						if (queueSize > 0) {
							show(scrobble_now);
						} else {
							hide(scrobble_now);
						}
						show(scrobble_when);
						
						if (scrobbling) {
							scrobble_status.setText(
								getString(R.string.scrobbling_in_progress));
							hide(scrobble_user_error);
							hide(update_link);
							hide(scrobble_now);
							show(scrobble_status);
						} else if (lastScrobbleResult ==
							ScrobblerService.NOT_YET_ATTEMPTED) {
							
							hide(scrobble_user_error);
							hide(update_link);
							hide(scrobble_status);
						} else {
							if (lastScrobbleResult ==
									ScrobblerService.BANNED ||
								lastScrobbleResult ==
									ScrobblerService.BADAUTH ||
								lastScrobbleResult ==
									ScrobblerService.BADTIME) {
								
								hide(scrobble_now);
								hide(scrobble_when);
								
								show(scrobble_user_error);
								hide(scrobble_status);
							} else {
								hide(scrobble_user_error);
								show(scrobble_status);
							}
							if (lastScrobbleResult == ScrobblerService.BANNED) {
								show(update_link);
							} else {
								hide(update_link);
							}
							
							switch (lastScrobbleResult) {
							case ScrobblerService.OK:
								scrobble_status.setText(getString(
									R.string.scrobbling_ok));
								break;
							case ScrobblerService.BANNED:
								scrobble_user_error.setText(getString(
									R.string.scrobbling_banned));
								break;
							case ScrobblerService.BADAUTH:
								scrobble_user_error.setText(getString(
									R.string.scrobbling_badauth));
								break;
							case ScrobblerService.BADTIME:
								scrobble_user_error.setText(getString(
									R.string.scrobbling_badtime));
								break;
							case ScrobblerService.FAILED_NET:
								scrobble_status.setText(getString(
									R.string.scrobbling_failed_net));
								break;
							case ScrobblerService.FAILED_OTHER:
								scrobble_status.setText(getString(
									R.string.scrobbling_failed_other));
								break;
							
							default:
								break;
							}
						}
					}
				});
			}
		};
	private ServiceConnection serviceConnection;
	private IScrobblerService service;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		prefs = getSharedPreferences(ScrobblerService.PREFS, 0);
		unsaved = getSharedPreferences("unsaved", 0);
		CompoundButton.OnCheckedChangeListener checkWatcher =
			new CompoundButton.OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView,
				boolean isChecked) {
				
				settingsChanged();
			}
		};
		TextWatcher textWatcher = new TextWatcher() {
			@Override
			public void afterTextChanged(Editable s) {
				settingsChanged();
			}
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
				int after) {}
			@Override
			public void onTextChanged(CharSequence s, int start, int before,
				int count) {}
		};
		
		setContentView(R.layout.main);
		enable = (CheckBox) findViewById(R.id.enable);
		immediate = (CheckBox) findViewById(R.id.immediate);
		username = (EditText) findViewById(R.id.username);
		password = (EditText) findViewById(R.id.password);
		
		sign_up = (TextView) findViewById(R.id.sign_up);
		view_user_page = (TextView) findViewById(R.id.view_user_page);
		
		settingsChanged = (LinearLayout) findViewById(R.id.settings_changed);
		scrobble_user_error = (TextView) findViewById(R.id.scrobble_user_error);
		update_link = (TextView) findViewById(R.id.update_link);
		queue_status = (TextView) findViewById(R.id.queue_status);
		scrobble_when = (TextView) findViewById(R.id.scrobble_when);
		scrobble_now = (Button) findViewById(R.id.scrobble_now);
		scrobble_status = (TextView) findViewById(R.id.scrobble_status);
		
		for (TextView tv : new TextView[] {
			sign_up, view_user_page, update_link
		}) {
			Spannable text = (Spannable) tv.getText();
			text.setSpan(new UnderlineSpan(), 0, text.length(), 0);
		}
		
		scrobbleWaiting =
			MessageFormat.format(getString(R.string.scrobble_when),
				MessageFormat.format(
					new ChoiceFormat(getString(R.string.scrobble_when_minutes))
						.format(ScrobblerService.SCROBBLE_WAITING_TIME_MINUTES),
					ScrobblerService.SCROBBLE_WAITING_TIME_MINUTES),
				MessageFormat.format(
					new ChoiceFormat(getString(R.string.scrobble_when_tracks))
						.format(ScrobblerService.SCROBBLE_BATCH_SIZE),
					ScrobblerService.SCROBBLE_BATCH_SIZE));
		
		enable.setOnCheckedChangeListener(checkWatcher);
		immediate.setOnCheckedChangeListener(checkWatcher);
		username.addTextChangedListener(textWatcher);
		password.addTextChangedListener(textWatcher);
		((Button) findViewById(R.id.save)).setOnClickListener(
			new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					uiToPrefs(prefs);
					settingsChanged();
				}
			});
		((Button) findViewById(R.id.revert)).setOnClickListener(
			new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					prefsToUI(prefs);
					settingsChanged();
				}
			});
		scrobble_now.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				try {
					service.startScrobble();
				} catch (RemoteException e) {}
			}
		});
		sign_up.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivity((new Intent(Intent.ACTION_VIEW,
					Uri.parse("https://www.last.fm/join"))));
			}
		});
		view_user_page.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivity((new Intent(Intent.ACTION_VIEW,
					Uri.parse("http://m.last.fm/user/" +
						URLEncoder.encode(username.getText().toString())))));
			}
		});
		update_link.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivity((new Intent(Intent.ACTION_VIEW,
					Uri.parse("market://search?q=pname:" +
						URLEncoder.encode(getPackageName())))));
			}
		});
	}

	protected void settingsChanged() {
		settingsChanged.setVisibility(isUISaved() ? View.GONE : View.VISIBLE);
		boolean hasUsername = username.getText().length() != 0;
		sign_up.setVisibility(hasUsername ? View.GONE : View.VISIBLE);
		view_user_page.setVisibility(hasUsername ? View.VISIBLE : View.GONE);
		scrobble_when.setText(immediate.isChecked() ?
			getString(R.string.scrobble_immediate) : scrobbleWaiting);
	}

	private void uiToPrefs(SharedPreferences p) {
		SharedPreferences.Editor e = p.edit();
		e.putBoolean("enable", enable.isChecked());
		e.putBoolean("immediate", immediate.isChecked());
		e.putString("username", username.getText().toString());
		e.putString("password", password.getText().toString());
		e.commit();
	}

	private void prefsToUI(SharedPreferences p) {
		enable.setChecked(p.getBoolean("enable", false));
		immediate.setChecked(p.getBoolean("immediate", false));
		username.setText(p.getString("username", ""));
		password.setText(p.getString("password", ""));
	}

	private boolean isUISaved() {
		SharedPreferences p = prefs;
		return
			enable.isChecked() == p.getBoolean("enable", false) &&
			immediate.isChecked() == p.getBoolean("immediate", false) &&
			username.getText().toString().equals(p.getString("username", "")) &&
			password.getText().toString().equals(p.getString("password", ""));
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (settingsChanged.getVisibility() == View.VISIBLE) {
			uiToPrefs(unsaved);
		}
		try {
			if (service != null && notifier != null) {
				service.unregisterNotificationHandler(notifier);
			}
		} catch (RemoteException e) {}
		unbindService(serviceConnection);
	}

	@Override
	protected void onResume() {
		super.onResume();
		
		final String examplePref = "enable";
		if (unsaved.contains(examplePref)) {
			prefsToUI(unsaved);
			SharedPreferences.Editor e = unsaved.edit();
			e.clear();
			e.commit();
		} else if (prefs.contains(examplePref)) {
			prefsToUI(prefs);
		} else {
			// Store defaults:
			uiToPrefs(prefs);
		}
		settingsChanged();
		
		serviceConnection = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName comp, IBinder binder) {
				service = IScrobblerService.Stub.asInterface(binder);
				try {
					service.registerNotificationHandler(notifier);
				} catch (RemoteException e) {}
				// There's no need for notifications now that the user is
				// looking at the UI:
				((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
					.cancelAll();
			}

			@Override
			public void onServiceDisconnected(ComponentName comp) {}
		};
		bindService(new Intent(this, ScrobblerService.class),
			serviceConnection, Context.BIND_AUTO_CREATE);
	}
}
