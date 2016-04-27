package org.briarproject.android;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.acra.ACRA;
import org.acra.ACRAConstants;
import org.acra.ReportField;
import org.acra.collector.CrashReportData;
import org.acra.dialog.BaseCrashReportDialog;
import org.acra.file.CrashReportPersister;
import org.acra.prefs.SharedPreferencesFactory;
import org.briarproject.R;
import org.briarproject.api.reporting.DevReporter;

import java.io.File;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static java.util.logging.Level.WARNING;

public class CrashReportActivity extends BaseCrashReportDialog
		implements DialogInterface.OnClickListener,
		DialogInterface.OnCancelListener {

	private static final Logger LOG =
			Logger.getLogger(CrashReportActivity.class.getName());

	private static final String STATE_REVIEWING = "reviewing";

	private SharedPreferencesFactory sharedPreferencesFactory;
	private EditText userCommentView = null;
	private EditText userEmailView = null;
	private LinearLayout status = null;
	private View progress = null;

	@Inject
	protected DevReporter reporter;

	boolean reviewing;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		setContentView(R.layout.activity_crash);

		((BriarApplication) getApplication()).getApplicationComponent()
				.inject(this);

		sharedPreferencesFactory =
				new SharedPreferencesFactory(getApplicationContext(),
						getConfig());

		userCommentView = (EditText) findViewById(R.id.user_comment);
		userEmailView = (EditText) findViewById(R.id.user_email);
		status = (LinearLayout) findViewById(R.id.crash_status);
		progress = findViewById(R.id.progress_wheel);

		findViewById(R.id.share_crash_report).setOnClickListener(
				new OnClickListener() {
					@Override
					public void onClick(View v) {
						processReport();
					}
				});

		final SharedPreferences prefs = sharedPreferencesFactory.create();
		String userEmail = prefs.getString(ACRA.PREF_USER_EMAIL_ADDRESS, "");
		userEmailView.setText(userEmail);

		if (state != null)
			reviewing = state.getBoolean(STATE_REVIEWING, false);
	}

	@Override
	public void onResume() {
		super.onResume();
		if (!reviewing) showDialog();
		refresh();
	}

	@Override
	public void onSaveInstanceState(Bundle state) {
		super.onSaveInstanceState(state);
		state.putBoolean(STATE_REVIEWING, reviewing);
	}

	@Override
	public void onBackPressed() {
		// show home screen, otherwise we are crashing again
		//Intent intent = new Intent(Intent.ACTION_MAIN);
		//intent.addCategory(Intent.CATEGORY_HOME);
		//startActivity(intent);
		closeReport();
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (which == DialogInterface.BUTTON_POSITIVE) {
			dialog.dismiss();
		} else {
			dialog.cancel();
		}
	}

	@Override
	public void onCancel(DialogInterface dialog) {
		closeReport();
	}

	private void showDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this,
				R.style.BriarDialogTheme);
		builder.setTitle(R.string.dialog_title_share_crash_report)
				.setIcon(R.drawable.ic_warning_black_24dp)
				.setMessage(R.string.dialog_message_share_crash_report)
				.setPositiveButton(R.string.dialog_button_ok, this)
				.setNegativeButton(R.string.cancel_button, this);
		AlertDialog dialog = builder.create();
		dialog.setCanceledOnTouchOutside(false);
		dialog.setOnCancelListener(this);
		dialog.show();
	}

	private void refresh() {
		status.setVisibility(INVISIBLE);
		progress.setVisibility(VISIBLE);
		status.removeAllViews();
		new AsyncTask<Void, Void, CrashReportData>() {

			@Override
			protected CrashReportData doInBackground(Void... args) {
				File reportFile = (File) getIntent().getSerializableExtra(
						ACRAConstants.EXTRA_REPORT_FILE);
				final CrashReportPersister persister =
						new CrashReportPersister();
				try {
					return persister.load(reportFile);
				} catch (IOException e) {
					LOG.log(WARNING, "Could not load report file", e);
					return null;
				}
			}

			@Override
			protected void onPostExecute(CrashReportData crashData) {
				LayoutInflater inflater = getLayoutInflater();
				if (crashData != null) {
					for (Entry<ReportField, String> e : crashData.entrySet()) {
						View v = inflater.inflate(R.layout.list_item_crash,
								status, false);
						((TextView) v.findViewById(R.id.title))
								.setText(e.getKey().toString());
						((TextView) v.findViewById(R.id.content))
								.setText(e.getValue());
						status.addView(v);
					}
				} else {
					View v = inflater.inflate(
							android.R.layout.simple_list_item_1, status, false);
					((TextView) v.findViewById(android.R.id.text1))
							.setText(R.string.could_not_load_crash_data);
					status.addView(v);
				}
				status.setVisibility(VISIBLE);
				progress.setVisibility(GONE);
			}
		}.execute();
	}

	private void processReport() {
		// Retrieve user comment
		final String comment = userCommentView != null ?
				userCommentView.getText().toString() : "";

		// Store the user email
		final String userEmail;
		final SharedPreferences prefs = sharedPreferencesFactory.create();
		if (userEmailView != null) {
			userEmail = userEmailView.getText().toString();
			final SharedPreferences.Editor prefEditor = prefs.edit();
			prefEditor.putString(ACRA.PREF_USER_EMAIL_ADDRESS, userEmail);
			prefEditor.commit();
		} else {
			userEmail = prefs.getString(ACRA.PREF_USER_EMAIL_ADDRESS, "");
		}
		sendCrash(comment, userEmail);
		finish();
	}

	private void closeReport() {
		cancelReports();
		finish();
	}
}
