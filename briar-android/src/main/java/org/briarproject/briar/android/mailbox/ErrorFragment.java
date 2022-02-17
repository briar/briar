package org.briarproject.briar.android.mailbox;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.fragment.FinalFragment;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import static org.briarproject.briar.android.AppModule.getAndroidComponent;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ErrorFragment extends FinalFragment {

	public static ErrorFragment newInstance(@StringRes int title,
			@StringRes int text) {
		ErrorFragment f = new ErrorFragment();
		Bundle args = new Bundle();
		args.putInt(ARG_TITLE, title);
		args.putInt(ARG_ICON, R.drawable.alerts_and_states_error);
		args.putInt(ARG_ICON_TINT, R.color.briar_red_500);
		args.putInt(ARG_TEXT, text);
		f.setArguments(args);
		return f;
	}

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private MailboxViewModel viewModel;

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		FragmentActivity activity = requireActivity();
		getAndroidComponent(activity).inject(this);
		viewModel = new ViewModelProvider(activity, viewModelFactory)
				.get(MailboxViewModel.class);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View v = super.onCreateView(inflater, container, savedInstanceState);
		onBackPressedCallback.remove();
		buttonView.setText(R.string.try_again_button);
		buttonView.setOnClickListener(view -> {
			getParentFragmentManager().popBackStackImmediate();
			viewModel.tryAgainAfterError();
		});
		return v;
	}

	@Override
	protected boolean shouldHideActionBarBackButton() {
		return false;
	}
}
