package org.briarproject.briar.android.socialbackup.recover;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Bundle;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.account.DozeFragment;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.fragment.BaseFragment;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.lifecycle.ViewModelProvider;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_TASK_ON_HOME;
import static org.briarproject.briar.android.BriarApplication.ENTRY_ACTIVITY;
import static org.briarproject.briar.android.socialbackup.recover.RestoreAccountViewModel.State;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class RestoreAccountActivity extends BaseActivity
		implements BaseFragment.BaseFragmentListener {

	@Inject
	ViewModelProvider.Factory viewModelFactory;
	RestoreAccountViewModel viewModel;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);

		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(RestoreAccountViewModel.class);
		viewModel.getState().observeEvent(this, this::onStateChanged);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);
		setContentView(R.layout.activity_fragment_container);
	}

	private void onStateChanged(RestoreAccountViewModel.State state) {
		if (state == State.SET_PASSWORD) {
			showInitialFragment(RestoreAccountSetPasswordFragment.newInstance());
		} else if (state == State.DOZE) {
			showDozeFragment();
		} else if (state == State.CREATED || state == State.FAILED) {
			// TODO: Show an error if failed
			showApp();
		}
	}

	@TargetApi(23)
	void showDozeFragment() {
		showNextFragment(DozeFragment.newInstance());
	}

	void showApp() {
		Intent i = new Intent(this, ENTRY_ACTIVITY);
		i.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_TASK_ON_HOME |
				FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(i);
		supportFinishAfterTransition();
		overridePendingTransition(R.anim.screen_new_in, R.anim.screen_old_out);
	}

	@Override
	@Deprecated
	public void runOnDbThread(Runnable runnable) {
		throw new RuntimeException("Don't use this deprecated method here.");
	}

}
