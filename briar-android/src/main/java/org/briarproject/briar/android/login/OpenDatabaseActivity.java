package org.briarproject.briar.android.login;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState;
import org.briarproject.bramble.api.lifecycle.event.LifecycleEvent;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.navdrawer.NavDrawerActivity;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.inject.Inject;

import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.MIGRATING_DATABASE;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.STARTING_SERVICES;

@ParametersAreNonnullByDefault
public class OpenDatabaseActivity extends BriarActivity
		implements EventListener {

	@Inject
	LifecycleManager lifecycleManager;
	@Inject
	EventBus eventBus;

	private TextView textView;
	private ImageView imageView;
	private boolean showingMigration = false;

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);
		setContentView(R.layout.activity_open_database);
		textView = findViewById(R.id.textView);
		imageView = findViewById(R.id.imageView);
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onStart() {
		super.onStart();
		LifecycleState state = lifecycleManager.getLifecycleState();
		if (state.isAfter(STARTING_SERVICES)) {
			finishAndStartApp();
		} else {
			if (state == MIGRATING_DATABASE) showMigration();
			eventBus.addListener(this);
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		eventBus.removeListener(this);
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof LifecycleEvent) {
			LifecycleState state = ((LifecycleEvent) e).getLifecycleState();
			if (state.isAfter(STARTING_SERVICES))
				runOnUiThreadUnlessDestroyed(this::finishAndStartApp);
			else if (state == MIGRATING_DATABASE)
				runOnUiThreadUnlessDestroyed(this::showMigration);
		}
	}

	private void showMigration() {
		if (showingMigration) return;
		textView.setText(R.string.startup_migrate_database);
		imageView.setImageResource(R.drawable.startup_migration);
		showingMigration = true;
	}

	private void finishAndStartApp() {
		startActivity(new Intent(this, NavDrawerActivity.class));
		supportFinishAfterTransition();
	}

}
