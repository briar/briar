package org.briarproject.briar.android.mailbox;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class OfflineStatusFragment extends OfflineFragment {

	public static final String TAG = OfflineStatusFragment.class.getName();

	@Override
	protected void onTryAgainClicked() {
		viewModel.checkIfOnlineWhenPaired();
	}

}
