package org.briarproject.briar.android.contact.add.nearby;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.util.UiUtils;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.view.View.GONE;
import static org.briarproject.briar.android.util.UiUtils.onSingleLinkClick;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ContactExchangeErrorFragment extends BaseFragment {

	public static final String TAG =
			ContactExchangeErrorFragment.class.getName();
	private static final String ERROR_MSG = "errorMessage";

	public static ContactExchangeErrorFragment newInstance(String errorMsg) {
		ContactExchangeErrorFragment f = new ContactExchangeErrorFragment();
		Bundle args = new Bundle();
		args.putString(ERROR_MSG, errorMsg);
		f.setArguments(args);
		return f;
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_error_contact_exchange,
				container, false);

		// set optional error message
		TextView explanation = v.findViewById(R.id.errorMessage);
		Bundle args = getArguments();
		String errorMessage = args == null ? null : args.getString(ERROR_MSG);
		if (errorMessage == null) explanation.setVisibility(GONE);
		else explanation.setText(args.getString(ERROR_MSG));

		// make feedback link clickable
		TextView sendFeedback = v.findViewById(R.id.sendFeedback);
		onSingleLinkClick(sendFeedback, this::triggerFeedback);

		// buttons
		Button tryAgain = v.findViewById(R.id.tryAgainButton);
		tryAgain.setOnClickListener(view -> {
			// Recreate the activity so we return to the intro fragment
			FragmentActivity activity = requireActivity();
			Intent i = new Intent(activity, ContactExchangeActivity.class);
			i.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
			activity.startActivity(i);
		});
		Button cancel = v.findViewById(R.id.cancelButton);
		cancel.setOnClickListener(view -> finish());
		return v;
	}

	private void triggerFeedback() {
		UiUtils.triggerFeedback(requireContext());
		finish();
	}

}
