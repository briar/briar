package org.briarproject.briar.android.keyagreement;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.acra.ACRA;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.util.UserFeedback;

import javax.inject.Inject;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ContactExchangeErrorFragment extends BaseFragment {

	public static final String TAG =
			ContactExchangeErrorFragment.class.getName();
	private static final String ERROR_MSG = "errorMessage";

	public static ContactExchangeErrorFragment newInstance(String message) {
		ContactExchangeErrorFragment f = new ContactExchangeErrorFragment();
		Bundle args = new Bundle();
		args.putString(ERROR_MSG, message);
		f.setArguments(args);
		return f;
	}

	@Inject
	AndroidExecutor androidExecutor;

	private String errorMessage;

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle args = getArguments();
		if (args == null) {
			throw new IllegalArgumentException("Use newInstance()");
		}
		errorMessage = args.getString(ERROR_MSG);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View v = inflater
				.inflate(R.layout.fragment_error_contact_exchange, container,
						false);

		// make feedback link clickable
		TextView explanation = v.findViewById(R.id.errorMessage);
		SpannableStringBuilder ssb =
				new SpannableStringBuilder(explanation.getText());
		ClickableSpan[] spans =
				ssb.getSpans(0, ssb.length(), ClickableSpan.class);
		if (spans.length != 1) throw new AssertionError();
		ClickableSpan span = spans[0];
		int start = ssb.getSpanStart(span);
		int end = ssb.getSpanEnd(span);
		ssb.removeSpan(span);
		ClickableSpan cSpan = new ClickableSpan() {
			@Override
			public void onClick(View v) {
				triggerFeedback();
			}
		};
		ssb.setSpan(cSpan, start + 1, end, 0);
		explanation.setText(ssb);
		explanation.setMovementMethod(new LinkMovementMethod());

		// technical error message
		TextView msg = v.findViewById(R.id.errorMessageTech);
		msg.setText(errorMessage);

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
		androidExecutor.runOnBackgroundThread(
				() -> ACRA.getErrorReporter()
						.handleException(new UserFeedback(), false));
	}

}
