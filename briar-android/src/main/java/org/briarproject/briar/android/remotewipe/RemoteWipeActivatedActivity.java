package org.briarproject.briar.android.remotewipe;

import android.os.Bundle;

import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;

import javax.annotation.Nullable;


public class RemoteWipeActivatedActivity extends BriarActivity {

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		signOut(true, true);
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}
}
