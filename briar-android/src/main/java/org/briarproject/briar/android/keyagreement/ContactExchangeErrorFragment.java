package org.briarproject.briar.android.keyagreement;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.util.UiUtils;

import javax.inject.Inject;

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

	@Inject
	AndroidExecutor androidExecutor;

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_error_contact_exchange,
				container, false);

		// set humanized error message
		TextView explanation = v.findViewById(R.id.errorMessage);
		Bundle args = getArguments();
		if (args == null) {
			throw new IllegalArgumentException("Use newInstance()");
		}
		explanation.setText(args.getString(ERROR_MSG));

		// make feedback link clickable
		TextView sendFeedback = v.findViewById(R.id.sendFeedback);
		onSingleLinkClick(sendFeedback, this::triggerFeedback);

		// buttons
		Button tryAgain = v.findViewById(R.id.tryAgainButton);
		tryAgain.setOnClickListener(view -> {
			if (getActivity() != null) getActivity().onBackPressed();
		});
		Button cancel = v.findViewById(R.id.cancelButton);
		cancel.setOnClickListener(view -> finish());
		return v;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	private void triggerFeedback() {
		finish();
		UiUtils.triggerFeedback(androidExecutor);
	}

}
