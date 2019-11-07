package org.briarproject.briar.android.widget;


import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;

import java.util.List;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import static android.content.Intent.ACTION_VIEW;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static java.util.Objects.requireNonNull;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class LinkDialogFragment extends DialogFragment {

	private static final String TAG = LinkDialogFragment.class.getName();

	private String url;

	public static LinkDialogFragment newInstance(String url) {
		LinkDialogFragment f = new LinkDialogFragment();

		Bundle args = new Bundle();
		args.putString("url", url);
		f.setArguments(args);

		return f;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle args = requireNonNull(getArguments());
		url = requireNonNull(args.getString("url"));

		setStyle(STYLE_NO_TITLE, R.style.BriarDialogTheme);
	}

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {

		View v = inflater.inflate(R.layout.fragment_link_dialog, container,
				false);

		TextView urlView = v.findViewById(R.id.urlView);
		urlView.setText(url);

		// prepare normal intent or intent chooser
		Intent i = new Intent(ACTION_VIEW, Uri.parse(url));
		PackageManager packageManager =
				requireNonNull(getContext()).getPackageManager();
		List activities = packageManager.queryIntentActivities(i,
				MATCH_DEFAULT_ONLY);
		boolean choice = activities.size() > 1;
		Intent intent = choice ? Intent.createChooser(i,
				getString(R.string.link_warning_open_link)) : i;

		Button openButton = v.findViewById(R.id.openButton);
		openButton.setOnClickListener(v1 -> {
			startActivity(intent);
			getDialog().dismiss();
		});

		Button cancelButton = v.findViewById(R.id.cancelButton);
		cancelButton.setOnClickListener(v1 -> getDialog().cancel());

		return v;
	}

	public String getUniqueTag() {
		return TAG;
	}

}
