package org.briarproject.briar.android.socialbackup.recover;

import android.os.Bundle;
import android.widget.Toast;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.contact.add.nearby.AddNearbyContactPermissionManager;
import org.briarproject.briar.android.socialbackup.OwnerRecoveryModeMainFragment;
import org.briarproject.briar.api.socialbackup.ReturnShardPayload;

import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.lifecycle.ViewModelProvider;

import static java.util.logging.Logger.getLogger;
import static org.briarproject.briar.android.socialbackup.CustodianHelpRecoverActivity.RETURN_SHARD_PAYLOAD;

public class CustodianSendShardActivity extends BriarActivity {
	private CustodianSendShardViewModel viewModel;

	private static final Logger LOG =
			getLogger(CustodianSendShardActivity.class.getName());

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(CustodianSendViewModel.class);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		byte[] returnShardPayloadBytes =
				getIntent().getByteArrayExtra(RETURN_SHARD_PAYLOAD);
		try {
			ReturnShardPayload returnShardPayload = parseReturnShardPayload(
					clientHelper.toList(returnShardPayloadBytes));
//			viewModel.setReturnShardPayload(returnShardPayload);
		} catch (FormatException e) {
			Toast.makeText(this,
					"Error reading social backup",
					Toast.LENGTH_SHORT).show();
			finish();
		}
		setContentView(R.layout.activity_fragment_container);
		if (state == null) {
			showInitialFragment(new OwnerRecoveryModeExplainerFragment());
		}
		viewModel.getCheckPermissions().observeEvent(this, check ->
				permissionManager.checkPermissions());
		viewModel.getRequestBluetoothDiscoverable().observeEvent(this, r ->
				requestBluetoothDiscoverable()); // never false
		viewModel.getShowQrCodeFragment().observeEvent(this, show -> {
			if (show) showQrCodeFragment();
		});
		viewModel.getState()
				.observe(this, this::onReturnShardStateChanged);
	}
}
