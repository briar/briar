package org.briarproject.briar.android.blog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.BaseActivity;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class RssFeedImportFailedDialogFragment extends DialogFragment {
	final static String TAG = RssFeedImportFailedDialogFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;
	private RssFeedViewModel viewModel;

	static RssFeedImportFailedDialogFragment newInstance() {
		return new RssFeedImportFailedDialogFragment();
	}

	@Override
	public void onAttach(Context ctx) {
		super.onAttach(ctx);
		((BaseActivity) requireActivity()).getActivityComponent().inject(this);

		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(RssFeedViewModel.class);
	}

	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		AlertDialog.Builder builder =
				new AlertDialog.Builder(requireActivity(),
						R.style.BriarDialogTheme);
		builder.setMessage(R.string.blogs_rss_feeds_import_error);
		builder.setNegativeButton(R.string.cancel, null);
		builder.setPositiveButton(R.string.try_again_button,
				(dialog, which) -> viewModel.retryImportFeed());

		return builder.create();
	}
}
