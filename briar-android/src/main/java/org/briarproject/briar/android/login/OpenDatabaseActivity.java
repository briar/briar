package org.briarproject.briar.android.login;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.bramble.api.db.DatabaseMigrationEvent;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.lifecycle.event.StartupEvent;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.inject.Inject;

import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.MIGRATING;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.RUNNING;

@ParametersAreNonnullByDefault
public class OpenDatabaseActivity extends BaseActivity
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
	public void onBackPressed() {
		// do not let the user bail out of here
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (lifecycleManager.getLifecycleState() == RUNNING) {
			supportFinishAfterTransition();
		} else {
			if (lifecycleManager.getLifecycleState() == MIGRATING) {
				showMigration();
			}
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
		if (e instanceof StartupEvent) {
			runOnUiThreadUnlessDestroyed(this::supportFinishAfterTransition);
		} else if (e instanceof DatabaseMigrationEvent) {
			runOnUiThreadUnlessDestroyed(this::showMigration);
		}
	}

	private void showMigration() {
		if (showingMigration) return;
		textView.setText(R.string.startup_migrate_database);
		imageView.setImageResource(R.drawable.startup_migration);
		showingMigration = true;
	}

}
