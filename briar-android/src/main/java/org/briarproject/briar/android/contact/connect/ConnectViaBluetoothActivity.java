package org.briarproject.briar.android.contact.connect;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.FinalFragment;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import static java.util.Objects.requireNonNull;
import static org.briarproject.briar.android.conversation.ConversationActivity.CONTACT_ID;
import static org.briarproject.briar.android.util.UiUtils.showFragment;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ConnectViaBluetoothActivity extends BriarActivity {

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private ConnectViaBluetoothViewModel viewModel;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);

		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(ConnectViaBluetoothViewModel.class);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = requireNonNull(getIntent());
		int contactId = intent.getIntExtra(CONTACT_ID, -1);
		if (contactId == -1) throw new IllegalArgumentException("ContactId");
		viewModel.setContactId(new ContactId(contactId));

		setContentView(R.layout.activity_fragment_container);

		viewModel.getState().observeEvent(this, this::onStateChanged);

		if (savedInstanceState == null) {
			Fragment f = new BluetoothIntroFragment();
			String tag = BluetoothIntroFragment.TAG;
			getSupportFragmentManager().beginTransaction()
					.replace(R.id.fragmentContainer, f, tag)
					.commitNow();
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		viewModel.reset();
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void onStateChanged(ConnectViaBluetoothState state) {
		Fragment f;
		String tag = FinalFragment.TAG;
		if (state instanceof ConnectViaBluetoothState.Connecting) {
			f = new BluetoothProgressFragment();
			tag = BluetoothProgressFragment.TAG;
		} else if (state instanceof ConnectViaBluetoothState.Success) {
			f = FinalFragment.newInstance(
					R.string.connect_via_bluetooth_success,
					R.drawable.ic_check_circle_outline,
					R.color.briar_brand_green,
					0
			);
		} else if (state instanceof ConnectViaBluetoothState.Error) {
			f = FinalFragment.newInstance(
					R.string.error,
					R.drawable.alerts_and_states_error,
					R.color.briar_red_500,
					((ConnectViaBluetoothState.Error) state).errorRes
			);
		} else throw new AssertionError();
		showFragment(getSupportFragmentManager(), f, tag, false);
	}

}
