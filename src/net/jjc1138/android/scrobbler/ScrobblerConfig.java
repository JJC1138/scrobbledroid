package net.jjc1138.android.scrobbler;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;

public class ScrobblerConfig extends Activity {
	public static final String UINOTIFICATION_ACTION =
		"net.jjc1138.android.scrobbler.action.uinotification";
	
	SharedPreferences prefs;
	SharedPreferences unsaved;

	CheckBox enable;
	CheckBox immediate;
	EditText username;
	EditText password;

	LinearLayout settingsChanged;
	
	public class ScrobblerNotificationReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			Log.v("ScrobAct", "Received broadcast.");
		}
		
	};
	private BroadcastReceiver notificationReceiver =
		new ScrobblerNotificationReceiver();
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		prefs = getSharedPreferences("prefs", 0);
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

		settingsChanged = (LinearLayout) findViewById(R.id.settings_changed);
		
		enable.setOnCheckedChangeListener(checkWatcher);
		immediate.setOnCheckedChangeListener(checkWatcher);
		username.addTextChangedListener(textWatcher);
		password.addTextChangedListener(textWatcher);
		((Button) findViewById(R.id.save)).setOnClickListener(
			new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					uiToPrefs(prefs);
					settingsChanged.setVisibility(View.GONE);
				}
			});
		((Button) findViewById(R.id.revert)).setOnClickListener(
			new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					prefsToUI(prefs);
					settingsChanged.setVisibility(View.GONE);
				}
			});
	}

	protected void settingsChanged() {
		settingsChanged.setVisibility(isUISaved() ? View.GONE : View.VISIBLE);
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
	protected void onResume() {
		super.onResume();
		
		final String examplePref = "enable";
		if (unsaved.contains(examplePref)) {
			prefsToUI(unsaved);
			SharedPreferences.Editor e = unsaved.edit();
			e.clear();
			e.commit();
			settingsChanged.setVisibility(View.VISIBLE);
		} else if (prefs.contains(examplePref)) {
			prefsToUI(prefs);
			// Reset this because it is set by prefsToUI changing things:
			settingsChanged.setVisibility(View.GONE);
		} else {
			// Store defaults:
			uiToPrefs(prefs);
		}
		
		registerReceiver(notificationReceiver, new IntentFilter(
			UINOTIFICATION_ACTION));
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (settingsChanged.getVisibility() == View.VISIBLE) {
			uiToPrefs(unsaved);
		}
		
		unregisterReceiver(notificationReceiver);
	}
}
