package org.briarproject.briar.android.conversation;

import android.content.Context;
import android.content.Intent;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.Nullable;

import static android.app.Activity.RESULT_CANCELED;
import static android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE;
import static android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION;

@NotNullByDefault
class RequestBluetoothDiscoverable
		extends ActivityResultContract<Integer, Boolean> {

	@Override
	public Intent createIntent(Context context, Integer duration) {
		Intent i = new Intent(ACTION_REQUEST_DISCOVERABLE);
		i.putExtra(EXTRA_DISCOVERABLE_DURATION, duration);
		return i;
	}

	@Override
	public Boolean parseResult(int resultCode, @Nullable Intent intent) {
		return resultCode != RESULT_CANCELED;
	}
}
