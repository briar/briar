package org.briarproject.android.report;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.collector.CrashReportData;
import org.acra.dialog.BaseCrashReportDialog;
import org.acra.file.CrashReportPersister;
import org.acra.prefs.SharedPreferencesFactory;
import org.briarproject.R;
import org.briarproject.android.util.UserFeedback;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import static android.content.DialogInterface.BUTTON_POSITIVE;
import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static java.util.logging.Level.WARNING;
import static org.acra.ACRAConstants.EXTRA_REPORT_FILE;
import static org.acra.ReportField.ANDROID_VERSION;
import static org.acra.ReportField.APP_VERSION_CODE;
import static org.acra.ReportField.APP_VERSION_NAME;
import static org.acra.ReportField.PACKAGE_NAME;
import static org.acra.ReportField.REPORT_ID;
import static org.acra.ReportField.STACK_TRACE;

public class DevReportActivity extends BaseCrashReportDialog
		implements DialogInterface.OnClickListener,
		DialogInterface.OnCancelListener,
		CompoundButton.OnCheckedChangeListener {

	private static final Logger LOG =
			Logger.getLogger(DevReportActivity.class.getName());

	private static final String PREF_EXCLUDED_FIELDS = "excludedReportFields";
	private static final String STATE_REVIEWING = "reviewing";
	private static final Set<ReportField> requiredFields = new HashSet<>();

	static {
		requiredFields.add(REPORT_ID);
		requiredFields.add(APP_VERSION_CODE);
		requiredFields.add(APP_VERSION_NAME);
		requiredFields.add(PACKAGE_NAME);
		requiredFields.add(ANDROID_VERSION);
		requiredFields.add(STACK_TRACE);
	}

	private SharedPreferencesFactory sharedPreferencesFactory;
	private Set<ReportField> excludedFields;
	private EditText userCommentView = null;
	private EditText userEmailView = null;
	private CheckBox includeDebugReport = null;
	private LinearLayout report = null;
	private View progress = null;
	private View share = null;
	private boolean reviewing = false;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_dev_report);

		sharedPreferencesFactory = new SharedPreferencesFactory(
				getApplicationContext(), getConfig());

		SharedPreferences prefs = sharedPreferencesFactory.create();
		excludedFields = new HashSet<>();
		if (Build.VERSION.SDK_INT >= 11) {
			for (String name : prefs.getStringSet(PREF_EXCLUDED_FIELDS,
					new HashSet<String>())) {
				excludedFields.add(ReportField.valueOf(name));
			}
		}

		TextView title = (TextView) findViewById(R.id.title);
		userCommentView = (EditText) findViewById(R.id.user_comment);
		userEmailView = (EditText) findViewById(R.id.user_email);
		includeDebugReport = (CheckBox) findViewById(R.id.include_debug_report);
		report = (LinearLayout) findViewById(R.id.report_content);
		progress = findViewById(R.id.progress_wheel);
		share = findViewById(R.id.share_dev_report);

		title.setText(isFeedback() ? R.string.feedback_title :
				R.string.crash_report_title);
		userCommentView.setHint(isFeedback() ? R.string.enter_feedback :
				R.string.describe_crash);

		includeDebugReport.setVisibility(isFeedback() ? VISIBLE : GONE);
		report.setVisibility(isFeedback() ? GONE : VISIBLE);

		includeDebugReport.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (includeDebugReport.isChecked())
					refresh();
				else
					report.setVisibility(GONE);
			}
		});
		share.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				processReport();
			}
		});

		String userEmail = prefs.getString(ACRA.PREF_USER_EMAIL_ADDRESS, "");
		userEmailView.setText(userEmail);

		if (state != null)
			reviewing = state.getBoolean(STATE_REVIEWING, false);
	}

	@Override
	public void onResume() {
		super.onResume();
		if (!isFeedback() && !reviewing) showCrashDialog();
		if (!isFeedback() || includeDebugReport.isChecked()) refresh();
	}

	@Override
	public void onSaveInstanceState(Bundle state) {
		super.onSaveInstanceState(state);
		state.putBoolean(STATE_REVIEWING, reviewing);
	}

	@Override
	public void onBackPressed() {
		closeReport();
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (which == BUTTON_POSITIVE) dialog.dismiss();
		else dialog.cancel();
	}

	@Override
	public void onCancel(DialogInterface dialog) {
		closeReport();
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		ReportField field = (ReportField) buttonView.getTag();
		if (field != null) {
			if (isChecked) excludedFields.remove(field);
			else excludedFields.add(field);
		}
	}

	@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
	private boolean isFeedback() {
		return getException() instanceof UserFeedback;
	}

	private void showCrashDialog() {
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
		report.setVisibility(INVISIBLE);
		progress.setVisibility(VISIBLE);
		report.removeAllViews();
		new AsyncTask<Void, Void, CrashReportData>() {

			@Override
			protected CrashReportData doInBackground(Void... args) {
				File reportFile = (File) getIntent().getSerializableExtra(
						EXTRA_REPORT_FILE);
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
						ReportField field = e.getKey();
						boolean required = requiredFields.contains(field);
						boolean excluded = excludedFields.contains(field);
						View v = inflater.inflate(R.layout.list_item_crash,
								report, false);
						CheckBox cb = (CheckBox) v
								.findViewById(R.id.include_in_report);
						cb.setTag(field);
						cb.setChecked(required || !excluded);
						cb.setEnabled(!required);
						cb.setOnCheckedChangeListener(DevReportActivity.this);
						((TextView) v.findViewById(R.id.title))
								.setText(e.getKey().toString());
						((TextView) v.findViewById(R.id.content))
								.setText(e.getValue());
						report.addView(v);
					}
				} else {
					View v = inflater.inflate(
							android.R.layout.simple_list_item_1, report, false);
					((TextView) v.findViewById(android.R.id.text1))
							.setText(R.string.could_not_load_report_data);
					report.addView(v);
				}
				report.setVisibility(VISIBLE);
				progress.setVisibility(GONE);
			}
		}.execute();
	}

	private void processReport() {
		userCommentView.setEnabled(false);
		userEmailView.setEnabled(false);
		share.setEnabled(false);
		progress.setVisibility(VISIBLE);
		final boolean includeReport =
				!isFeedback() || includeDebugReport.isChecked();
		new AsyncTask<Void, Void, Boolean>() {

			@Override
			protected Boolean doInBackground(Void... args) {
				File reportFile = (File) getIntent().getSerializableExtra(
						EXTRA_REPORT_FILE);
				CrashReportPersister persister = new CrashReportPersister();
				try {
					CrashReportData data = persister.load(reportFile);
					if (includeReport) {
						for (ReportField field : excludedFields) {
							LOG.info("Removing field " + field.name());
							data.remove(field);
						}
					} else {
						Iterator<Entry<ReportField, String>> iter =
								data.entrySet().iterator();
						while (iter.hasNext()) {
							Entry<ReportField, String> e = iter.next();
							if (!requiredFields.contains(e.getKey())) {
								iter.remove();
							}
						}
					}
					persister.store(data, reportFile);
					return true;
				} catch (IOException e) {
					LOG.log(WARNING, "Error processing report file", e);
					return false;
				}
			}

			@Override
			protected void onPostExecute(Boolean success) {
				final SharedPreferences prefs =
						sharedPreferencesFactory.create();
				if (Build.VERSION.SDK_INT >= 11) {
					final SharedPreferences.Editor prefEditor =
							prefs.edit();
					Set<String> fields = new HashSet<>();
					for (ReportField field : excludedFields) {
						fields.add(field.name());
					}
					prefEditor.putStringSet(PREF_EXCLUDED_FIELDS, fields);
					prefEditor.commit();
				}

				if (success) {
					// Retrieve user's comment and email address, if any
					String comment = "";
					if (userCommentView != null)
						comment = userCommentView.getText().toString();
					String email = "";
					if (userEmailView != null)
						email = userEmailView.getText().toString();
					sendCrash(comment, email);
				}
				finish();
			}
		}.execute();
	}

	private void closeReport() {
		cancelReports();
		finish();
	}
}
