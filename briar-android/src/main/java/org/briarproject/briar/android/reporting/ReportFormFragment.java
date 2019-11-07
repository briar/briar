package org.briarproject.briar.android.reporting;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.acra.ReportField;
import org.acra.collector.CrashReportData;
import org.acra.file.CrashReportPersister;
import org.acra.model.Element;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import static android.view.MenuItem.SHOW_AS_ACTION_ALWAYS;
import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.acra.ACRAConstants.EXTRA_REPORT_FILE;
import static org.acra.ReportField.ANDROID_VERSION;
import static org.acra.ReportField.APP_VERSION_CODE;
import static org.acra.ReportField.APP_VERSION_NAME;
import static org.acra.ReportField.PACKAGE_NAME;
import static org.acra.ReportField.REPORT_ID;
import static org.acra.ReportField.STACK_TRACE;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ReportFormFragment extends Fragment
		implements OnCheckedChangeListener {

	private static final Logger LOG =
			getLogger(ReportFormFragment.class.getName());
	private static final String IS_FEEDBACK = "isFeedback";
	private static final Set<ReportField> requiredFields = new HashSet<>();
	private static final Set<ReportField> excludedFields = new HashSet<>();
	static {
		requiredFields.add(REPORT_ID);
		requiredFields.add(APP_VERSION_CODE);
		requiredFields.add(APP_VERSION_NAME);
		requiredFields.add(PACKAGE_NAME);
		requiredFields.add(ANDROID_VERSION);
		requiredFields.add(STACK_TRACE);
	}

	private boolean isFeedback;
	private File reportFile;

	private EditText userCommentView;
	private EditText userEmailView;
	private CheckBox includeDebugReport;
	private Button chevron;
	private LinearLayout report;
	private View progress;
	@Nullable
	private MenuItem sendReport;

	static ReportFormFragment newInstance(boolean isFeedback,
			File reportFile) {
		ReportFormFragment f = new ReportFormFragment();
		Bundle args = new Bundle();
		args.putBoolean(IS_FEEDBACK, isFeedback);
		args.putSerializable(EXTRA_REPORT_FILE, reportFile);
		f.setArguments(args);
		return f;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_report_form, container,
				false);

		userCommentView = v.findViewById(R.id.user_comment);
		userEmailView = v.findViewById(R.id.user_email);
		includeDebugReport = v.findViewById(R.id.include_debug_report);
		chevron = v.findViewById(R.id.chevron);
		report = v.findViewById(R.id.report_content);
		progress = v.findViewById(R.id.progress_wheel);

		Bundle args = requireNonNull(getArguments());
		isFeedback = args.getBoolean(IS_FEEDBACK);
		reportFile =
				(File) requireNonNull(args.getSerializable(EXTRA_REPORT_FILE));

		if (isFeedback) {
			includeDebugReport
					.setText(getString(R.string.include_debug_report_feedback));
			userCommentView.setHint(R.string.enter_feedback);
		} else {
			includeDebugReport.setChecked(true);
			userCommentView.setHint(R.string.describe_crash);
		}

		chevron.setOnClickListener(view -> {
			boolean show = chevron.getText().equals(getString(R.string.show));
			if (show) {
				chevron.setText(R.string.hide);
				refresh();
			} else {
				chevron.setText(R.string.show);
				report.setVisibility(GONE);
			}
		});

		return v;
	}

	@Override
	public void onStart() {
		super.onStart();
		if (chevron.isSelected()) refresh();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.dev_report_actions, menu);
		sendReport = menu.findItem(R.id.action_send_report);
		// calling setShowAsAction() shouldn't be needed, but for some reason is
		sendReport.setShowAsAction(SHOW_AS_ACTION_ALWAYS);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_send_report) {
			processReport();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		ReportField field = (ReportField) buttonView.getTag();
		if (field != null) {
			if (isChecked) excludedFields.remove(field);
			else excludedFields.add(field);
		}
	}

	private void refresh() {
		report.setVisibility(INVISIBLE);
		progress.setVisibility(VISIBLE);
		report.removeAllViews();
		new AsyncTask<Void, Void, CrashReportData>() {

			@Override
			protected CrashReportData doInBackground(Void... args) {
				CrashReportPersister persister = new CrashReportPersister();
				try {
					return persister.load(reportFile);
				} catch (IOException | JSONException e) {
					LOG.log(WARNING, "Could not load report file", e);
					return null;
				}
			}

			@Override
			protected void onPostExecute(CrashReportData crashData) {
				LayoutInflater inflater = getLayoutInflater();
				if (crashData != null) {
					for (Map.Entry<ReportField, Element> e : crashData
							.entrySet()) {
						ReportField field = e.getKey();
						StringBuilder valueBuilder = new StringBuilder();
						for (String pair : e.getValue().flatten()) {
							valueBuilder.append(pair).append("\n");
						}
						String value = valueBuilder.toString();
						boolean required = requiredFields.contains(field);
						boolean excluded = excludedFields.contains(field);
						View v = inflater.inflate(R.layout.list_item_crash,
								report, false);
						CheckBox cb = v.findViewById(R.id.include_in_report);
						cb.setTag(field);
						cb.setChecked(required || !excluded);
						cb.setEnabled(!required);
						cb.setOnCheckedChangeListener(ReportFormFragment.this);
						cb.setText(field.toString());
						TextView content = v.findViewById(R.id.content);
						content.setText(value);
						report.addView(v);
					}
				} else {
					View v = inflater.inflate(
							android.R.layout.simple_list_item_1, report, false);
					TextView error = v.findViewById(android.R.id.text1);
					error.setText(R.string.could_not_load_report_data);
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
		requireNonNull(sendReport).setEnabled(false);
		progress.setVisibility(VISIBLE);
		boolean includeReport = !isFeedback || includeDebugReport.isChecked();
		new AsyncTask<Void, Void, Boolean>() {

			@Override
			protected Boolean doInBackground(Void... args) {
				CrashReportPersister persister = new CrashReportPersister();
				try {
					CrashReportData data = persister.load(reportFile);
					if (includeReport) {
						for (ReportField field : excludedFields) {
							LOG.info("Removing field " + field.name());
							data.remove(field);
						}
					} else {
						Iterator<Map.Entry<ReportField, Element>> iter =
								data.entrySet().iterator();
						while (iter.hasNext()) {
							Map.Entry<ReportField, Element> e = iter.next();
							if (!requiredFields.contains(e.getKey())) {
								iter.remove();
							}
						}
					}
					persister.store(data, reportFile);
					return true;
				} catch (IOException | JSONException e) {
					LOG.log(WARNING, "Error processing report file", e);
					return false;
				}
			}

			@Override
			protected void onPostExecute(Boolean success) {
				if (success) {
					// Retrieve user's comment and email address, if any
					String comment = "";
					if (userCommentView != null)
						comment = userCommentView.getText().toString();
					String email = "";
					if (userEmailView != null) {
						email = userEmailView.getText().toString();
					}
					getDevReportActivity().sendCrashReport(comment, email);
				}
				if (getActivity() != null) getDevReportActivity().exit();
			}
		}.execute();
	}

	private DevReportActivity getDevReportActivity() {
		return (DevReportActivity) requireNonNull(getActivity());
	}

}
