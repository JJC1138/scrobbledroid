package net.jjc1138.android.scrobbler;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Checkable;
import android.widget.TextView;

public class ScrobblerConfig extends Activity {
	SharedPreferences prefs;
	SharedPreferences unsaved;

	boolean settingsChanged;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		prefs = getSharedPreferences("prefs", 0);
		unsaved = getSharedPreferences("unsaved", 0);
		settingsChanged = false;
		
		setContentView(R.layout.main);
	}

	private void uiToPrefs(SharedPreferences p) {
		SharedPreferences.Editor e = p.edit();
		e.putBoolean("enable",
			((Checkable) findViewById(R.id.enable)).isChecked());
		e.putBoolean("immediate",
			((Checkable) findViewById(R.id.immediate)).isChecked());
		e.putString("username",
			((TextView) findViewById(R.id.username)).getText().toString());
		e.putString("password",
			((TextView) findViewById(R.id.password)).getText().toString());
		e.commit();
	}

	private void prefsToUI(SharedPreferences p) {
		((Checkable) findViewById(R.id.enable)).setChecked(
			p.getBoolean("enable", false));
		((Checkable) findViewById(R.id.immediate)).setChecked(
			p.getBoolean("immediate", false));
		((TextView) findViewById(R.id.username)).setText(
			p.getString("username", ""));
		((TextView) findViewById(R.id.password)).setText(
			p.getString("password", ""));
	}

	private boolean isUISaved() {
		SharedPreferences p = prefs;
		return
			((Checkable) findViewById(R.id.enable)).isChecked() ==
				p.getBoolean("enable", false) &&
			((Checkable) findViewById(R.id.immediate)).isChecked() ==
				p.getBoolean("immediate", false) &&
			((TextView) findViewById(R.id.username)).getText().toString() ==
				p.getString("username", "") &&
			((TextView) findViewById(R.id.password)).getText().toString() ==
				p.getString("password", "");
	}

	@Override
	protected void onPause() {
		super.onPause();
		uiToPrefs(unsaved);
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
	}
}
