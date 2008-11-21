package net.jjc1138.android.scrobbler;

import java.net.URLEncoder;
import java.text.ChoiceFormat;
import java.text.MessageFormat;

import android.app.Activity;
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
	private TextView queue_status;
	private Button scrobble_now;
	private TextView scrobble_status;

	private final Handler handler = new Handler();

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
						
						// TODOLATER BADTIME should also prevent further
						// handshakes according to the spec., but we don't yet
						// have a way of resetting from BADTIME when the time is
						// updated.
						scrobble_now.setVisibility(
							(!scrobbling && queueSize > 0 &&
								lastScrobbleResult != ScrobblerService.BANNED &&
								lastScrobbleResult != ScrobblerService.BADAUTH)
								? View.VISIBLE : View.GONE);
						
						scrobble_status.setVisibility(
							(!scrobbling && lastScrobbleResult == 
								ScrobblerService.NOT_YET_ATTEMPTED) ?
							
							View.GONE : View.VISIBLE);
						
						if (scrobbling) {
							scrobble_status.setText(
								getString(R.string.scrobbling_in_progress));
						} else {
							switch (lastScrobbleResult) {
							case ScrobblerService.OK:
								scrobble_status.setText(getString(
									R.string.scrobbling_ok));
								break;
							case ScrobblerService.BANNED:
								scrobble_status.setText(getString(
									R.string.scrobbling_banned));
								break;
							case ScrobblerService.BADAUTH:
								scrobble_status.setText(getString(
									R.string.scrobbling_badauth));
								break;
							case ScrobblerService.BADTIME:
								scrobble_status.setText(getString(
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
		queue_status = (TextView) findViewById(R.id.queue_status);
		scrobble_now = (Button) findViewById(R.id.scrobble_now);
		scrobble_status = (TextView) findViewById(R.id.scrobble_status);
		
		Spannable text = (Spannable) sign_up.getText();
		text.setSpan(new UnderlineSpan(), 0, text.length(), 0);
		text = (Spannable) view_user_page.getText();
		text.setSpan(new UnderlineSpan(), 0, text.length(), 0);
		
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
					Uri.parse("https://m.last.fm/join"))));
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
	}

	protected void settingsChanged() {
		settingsChanged.setVisibility(isUISaved() ? View.GONE : View.VISIBLE);
		boolean hasUsername = username.getText().length() != 0;
		sign_up.setVisibility(hasUsername ? View.GONE : View.VISIBLE);
		view_user_page.setVisibility(hasUsername ? View.VISIBLE : View.GONE);
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
			service.unregisterNotificationHandler(notifier);
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
			}

			@Override
			public void onServiceDisconnected(ComponentName comp) {}
		};
		bindService(new Intent(this, ScrobblerService.class),
			serviceConnection, Context.BIND_AUTO_CREATE);
	}
}
