package org.briarproject.briar.android.account;

import android.content.Context;

import org.briarproject.android.dontkillmelib.AbstractDoNotKillMeFragment;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;

import javax.inject.Inject;

import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import static org.briarproject.briar.android.AppModule.getAndroidComponent;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class DoNotKillMeFragment extends AbstractDoNotKillMeFragment {

	@Inject
	ViewModelProvider.Factory viewModelFactory;
	SetupViewModel viewModel;

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		FragmentActivity activity = requireActivity();
		getAndroidComponent(activity).inject(this);
		viewModel = new ViewModelProvider(activity, viewModelFactory)
				.get(SetupViewModel.class);
	}

	@Override
	protected void onButtonClicked() {
		viewModel.dozeExceptionConfirmed();
	}

}
