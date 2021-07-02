package org.briarproject.briar.android.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument;
import androidx.activity.result.contract.ActivityResultContracts.GetContent;
import androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents;
import androidx.annotation.Nullable;

import static android.app.Activity.RESULT_CANCELED;
import static android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE;
import static android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION;
import static android.content.Intent.EXTRA_MIME_TYPES;
import static android.os.Build.VERSION.SDK_INT;
import static org.briarproject.bramble.util.AndroidUtils.getSupportedImageContentTypes;

@NotNullByDefault
public class ActivityLaunchers {

	public static class CreateDocumentAdvanced extends CreateDocument {
		@Override
		public Intent createIntent(Context context, String input) {
			Intent i = super.createIntent(context, input);
			putShowAdvancedExtra(i);
			return i;
		}
	}

	public static class GetContentAdvanced extends GetContent {
		@Override
		public Intent createIntent(Context context, String input) {
			Intent i = super.createIntent(context, input);
			putShowAdvancedExtra(i);
			return i;
		}
	}

	public static class GetImageAdvanced extends GetContent {
		@Override
		public Intent createIntent(Context context, String input) {
			Intent i = super.createIntent(context, input);
			putShowAdvancedExtra(i);
			i.setType("image/*");
			if (SDK_INT >= 19)
				i.putExtra(EXTRA_MIME_TYPES, getSupportedImageContentTypes());
			return i;
		}
	}

	@TargetApi(18)
	public static class GetMultipleImagesAdvanced extends GetMultipleContents {
		@Override
		public Intent createIntent(Context context, String input) {
			Intent i = super.createIntent(context, input);
			putShowAdvancedExtra(i);
			i.setType("image/*");
			if (SDK_INT >= 19)
				i.putExtra(EXTRA_MIME_TYPES, getSupportedImageContentTypes());
			return i;
		}
	}

	public static class RequestBluetoothDiscoverable
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

	private static void putShowAdvancedExtra(Intent i) {
		i.putExtra(SDK_INT <= 28 ? "android.content.extra.SHOW_ADVANCED" :
				"android.provider.extra.SHOW_ADVANCED", true);
	}

}
