package org.briarproject.briar.android.socialbackup.recover;

import android.os.Bundle;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.socialbackup.CustodianRecoveryModeExplainerFragment;

import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import static java.util.logging.Logger.getLogger;

public class CustodianReturnShardActivity extends BriarActivity {
	private CustodianReturnShardViewModel viewModel;

	private static final Logger LOG =
			getLogger(CustodianReturnShardActivity.class.getName());

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(CustodianReturnShardViewModel.class);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

//		byte[] returnShardPayloadBytes =
//				getIntent().getByteArrayExtra(RETURN_SHARD_PAYLOAD);
//		try {
//			ReturnShardPayload returnShardPayload = parseReturnShardPayload(
//					clientHelper.toList(returnShardPayloadBytes));
//			viewModel.setReturnShardPayload(returnShardPayload);
//		} catch (FormatException e) {
//			Toast.makeText(this,
//					"Error reading social backup",
//					Toast.LENGTH_SHORT).show();
//			finish();
//		}
		setContentView(R.layout.activity_fragment_container);
		if (state == null) {
			showInitialFragment(new CustodianRecoveryModeExplainerFragment());
		}
//		viewModel.getCheckPermissions().observeEvent(this, check ->
//				permissionManager.checkPermissions());
//		viewModel.getRequestBluetoothDiscoverable().observeEvent(this, r ->
//				requestBluetoothDiscoverable()); // never false
		viewModel.getShowCameraFragment().observeEvent(this, show -> {
			if (show) showCameraFragment();
		});
//		viewModel.getState()
//				.observe(this, this::onReturnShardStateChanged);
	}

	private void showCameraFragment() {
		// FIXME #824
		FragmentManager fm = getSupportFragmentManager();
		if (fm.findFragmentByTag(CustodianReturnShardFragment.TAG) == null) {
			BaseFragment f = CustodianReturnShardFragment.newInstance();
			fm.beginTransaction()
					.replace(R.id.fragmentContainer, f, f.getUniqueTag())
					.addToBackStack(f.getUniqueTag())
					.commit();
		}
	}
}
