package org.briarproject.briar.android.fragment;

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

import java.util.ArrayList;

import javax.annotation.Nullable;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class SFDialogFragment extends DialogFragment {

	public static SFDialogFragment newInstance(ArrayList<String> apps) {
		SFDialogFragment frag = new SFDialogFragment();
		Bundle args = new Bundle();
		args.putStringArrayList("apps", apps);
		frag.setArguments(args);
		return frag;
	}

	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		AlertDialog.Builder builder =
				new AlertDialog.Builder(
						getActivity(),
						R.style.BriarDialogThemeNoFilter);
		builder.setTitle(R.string.screen_filter_title);
		LayoutInflater li = getActivity().getLayoutInflater();
		//Pass null here because it's an AlertDialog
		View v =
				li.inflate(R.layout.alert_dialog_checkbox, null,
						false);
		TextView t = (TextView) v.findViewById(R.id.alert_dialog_text);
		final ArrayList<String> apps =
				getArguments().getStringArrayList("apps");
		t.setText(getString(R.string.screen_filter_body, TextUtils
				.join("\n", apps)));
		final CheckBox cb =
				(CheckBox) v.findViewById(
						R.id.checkBox_screen_filter_reminder);
		builder.setNeutralButton(R.string.continue_button,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog,
							int which) {
						((BaseActivity) getActivity())
								.rememberShownApps(apps, cb.isChecked());
					}
				});
		builder.setView(v);
		return builder.create();
	}
}
