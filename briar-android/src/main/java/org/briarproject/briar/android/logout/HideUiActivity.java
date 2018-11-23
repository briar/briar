package org.briarproject.briar.android.logout;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class HideUiActivity extends Activity {

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);
		finish();
	}
}
