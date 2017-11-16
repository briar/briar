package org.briarproject.briar.android.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;

import java.util.ArrayList;

import javax.annotation.Nullable;

@NotNullByDefault
public class ScreenFilterDialogFragment extends DialogFragment {

	public static ScreenFilterDialogFragment newInstance(
			ArrayList<String> apps) {
		ScreenFilterDialogFragment frag = new ScreenFilterDialogFragment();
		Bundle args = new Bundle();
		args.putStringArrayList("apps", apps);
		frag.setArguments(args);
		return frag;
	}

	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(),
				R.style.BriarDialogThemeNoFilter);
		builder.setTitle(R.string.screen_filter_title);
		ArrayList<String> apps = getArguments().getStringArrayList("apps");
		builder.setMessage(getString(R.string.screen_filter_body,
				TextUtils.join("\n", apps)));
		builder.setNeutralButton(R.string.continue_button,
				(dialog, which) -> dialog.dismiss());
		return builder.create();
	}
}
