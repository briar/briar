package org.briarproject.briar.android.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.api.android.ScreenFilterMonitor;
import org.briarproject.briar.api.android.ScreenFilterMonitor.AppDetails;

import java.util.ArrayList;
import java.util.Collection;

import javax.annotation.Nullable;
import javax.inject.Inject;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ScreenFilterDialogFragment extends DialogFragment {

	public static final String TAG = ScreenFilterDialogFragment.class.getName();

	@Inject
	ScreenFilterMonitor screenFilterMonitor;

	DismissListener dismissListener = null;

	public static ScreenFilterDialogFragment newInstance(
			Collection<AppDetails> apps) {
		ScreenFilterDialogFragment frag = new ScreenFilterDialogFragment();
		Bundle args = new Bundle();
		ArrayList<String> appNames = new ArrayList<>();
		for (AppDetails a : apps) appNames.add(a.name);
		args.putStringArrayList("appNames", appNames);
		ArrayList<String> packageNames = new ArrayList<>();
		for (AppDetails a : apps) packageNames.add(a.packageName);
		args.putStringArrayList("packageNames", packageNames);
		frag.setArguments(args);
		return frag;
	}

	public void setDismissListener(DismissListener dismissListener) {
		this.dismissListener = dismissListener;
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Activity activity = getActivity();
		if (activity == null) throw new IllegalStateException();
		((BaseActivity) activity).getActivityComponent().inject(this);
	}

	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		Activity activity = getActivity();
		if (activity == null) throw new IllegalStateException();
		AlertDialog.Builder builder = new AlertDialog.Builder(activity,
				R.style.BriarDialogThemeNoFilter);
		builder.setTitle(R.string.screen_filter_title);
		Bundle args = getArguments();
		if (args == null) throw new IllegalStateException();
		ArrayList<String> appNames = args.getStringArrayList("appNames");
		ArrayList<String> packageNames =
				args.getStringArrayList("packageNames");
		if (appNames == null || packageNames == null)
			throw new IllegalStateException();
		LayoutInflater inflater = activity.getLayoutInflater();
		// See https://stackoverflow.com/a/24720976/6314875
		@SuppressLint("InflateParams")
		View dialogView = inflater.inflate(R.layout.dialog_screen_filter, null);
		builder.setView(dialogView);
		TextView message = dialogView.findViewById(R.id.screen_filter_message);
		message.setText(getString(R.string.screen_filter_body,
				TextUtils.join("\n", appNames)));
		CheckBox allow = dialogView.findViewById(R.id.screen_filter_checkbox);
		builder.setNeutralButton(R.string.continue_button, (dialog, which) -> {
			if (allow.isChecked()) screenFilterMonitor.allowApps(packageNames);
			dialog.dismiss();
		});
		builder.setCancelable(false);
		return builder.create();
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		super.onDismiss(dialog);
		if (dismissListener != null) dismissListener.onDialogDismissed();
	}

	public interface DismissListener {
		void onDialogDismissed();
	}
}
