package org.briarproject.android;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.contact.ContactListActivity;
import org.briarproject.android.forum.ForumListActivity;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.TransportId;
import org.briarproject.api.android.ReferenceManager;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.TransportDisabledEvent;
import org.briarproject.api.event.TransportEnabledEvent;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.plugins.Plugin;
import org.briarproject.api.plugins.PluginManager;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.view.Gravity.CENTER;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;

public class DashboardActivity extends BriarActivity implements EventListener {

	private static final Logger LOG =
			Logger.getLogger(DashboardActivity.class.getName());

	private List<Transport> transports;
	private BaseAdapter transportsAdapter;

	@Inject private ReferenceManager referenceManager;
	@Inject private PluginManager pluginManager;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile IdentityManager identityManager;
	@Inject private volatile EventBus eventBus;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		handleIntent(getIntent());
	}

	@Override
	public void onResume() {
		super.onResume();

		eventBus.addListener(this);
	}

	@Override
	public void onPause() {
		super.onPause();

		eventBus.removeListener(this);
	}

	@Override
	public void onNewIntent(Intent i) {
		super.onNewIntent(i);
		handleIntent(i);
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof TransportEnabledEvent) {
			TransportId id = ((TransportEnabledEvent) e).getTransportId();
			if (LOG.isLoggable(INFO)) {
				LOG.info("TransportEnabledEvent: " + id.getString());
			}
			setTransport(id, true);
		} else if (e instanceof TransportDisabledEvent) {
			TransportId id = ((TransportDisabledEvent) e).getTransportId();
			if (LOG.isLoggable(INFO)) {
				LOG.info("TransportDisabledEvent: " + id.getString());
			}
			setTransport(id, false);
		}
	}

	private void handleIntent(Intent i) {
		boolean failed = i.getBooleanExtra("briar.STARTUP_FAILED", false);
		long handle = i.getLongExtra("briar.LOCAL_AUTHOR_HANDLE", -1);
		if (failed) {
			finish();
			LOG.info("Exiting");
			System.exit(0);
		} else if (handle == -1) {
			// The activity has been launched before
			showButtons();
		} else {
			// The activity was launched from the setup wizard
			LocalAuthor a = referenceManager.removeReference(handle,
					LocalAuthor.class);
			// The reference may be null if the activity has been recreated,
			// for example due to screen rotation
			if (a == null) {
				showButtons();
			} else {
				showSpinner();
				storeLocalAuthor(a);
			}
		}
	}

	private void showButtons() {
		ListView.LayoutParams matchMatch =
				new ListView.LayoutParams(MATCH_PARENT, MATCH_PARENT);
		final List<Button> buttons = new ArrayList<Button>();

		Button contactsButton = new Button(this);
		contactsButton.setLayoutParams(matchMatch);
		contactsButton.setBackgroundResource(0);
		contactsButton.setCompoundDrawablesWithIntrinsicBounds(0,
				R.drawable.social_person, 0, 0);
		contactsButton.setText(R.string.contact_list_button);
		contactsButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				startActivity(new Intent(DashboardActivity.this,
						ContactListActivity.class));
			}
		});
		buttons.add(contactsButton);

		Button forumsButton = new Button(this);
		forumsButton.setLayoutParams(matchMatch);
		forumsButton.setBackgroundResource(0);
		forumsButton.setCompoundDrawablesWithIntrinsicBounds(0,
				R.drawable.social_chat, 0, 0);
		forumsButton.setText(R.string.forums_button);
		forumsButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				startActivity(new Intent(DashboardActivity.this,
						ForumListActivity.class));
			}
		});
		buttons.add(forumsButton);

		Button settingsButton = new Button(this);
		settingsButton.setLayoutParams(matchMatch);
		settingsButton.setBackgroundResource(0);
		settingsButton.setCompoundDrawablesWithIntrinsicBounds(0,
				R.drawable.action_settings, 0, 0);
		settingsButton.setText(R.string.settings_button);
		settingsButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				startActivity(new Intent(DashboardActivity.this,
						SettingsActivity.class));
			}
		});
		buttons.add(settingsButton);

		Button signOutButton = new Button(this);
		signOutButton.setLayoutParams(matchMatch);
		signOutButton.setBackgroundResource(0);
		signOutButton.setCompoundDrawablesWithIntrinsicBounds(0,
				R.drawable.device_access_accounts, 0, 0);
		signOutButton.setText(R.string.sign_out_button);
		signOutButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				showSpinner();
				signOut();
			}
		});
		buttons.add(signOutButton);

		int pad = LayoutUtils.getPadding(this);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(LinearLayout.VERTICAL);

		GridView grid = new GridView(this);
		LinearLayout.LayoutParams params =
				new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, 1f);
		grid.setLayoutParams(params);
		grid.setGravity(CENTER);
		grid.setPadding(pad, pad, pad, pad);
		Resources res = getResources();
		grid.setBackgroundColor(res.getColor(R.color.dashboard_background));
		grid.setNumColumns(2);
		grid.setAdapter(new BaseAdapter() {

			public int getCount() {
				return buttons.size();
			}

			public Object getItem(int position) {
				return buttons.get(position);
			}

			public long getItemId(int position) {
				return 0;
			}

			public View getView(int position, View convertView,
					ViewGroup parent) {
				return buttons.get(position);
			}
		});
		layout.addView(grid);

		// inflate transports layout
		LayoutInflater inflater = (LayoutInflater) getSystemService
				(Context.LAYOUT_INFLATER_SERVICE);
		ViewGroup transportsLayout = (ViewGroup) inflater.
				inflate(R.layout.transports_list, layout);

		initializeTransports();

		GridView transportsView = (GridView) transportsLayout.findViewById(
				R.id.transportsView);
		transportsView.setAdapter(transportsAdapter);

		setContentView(layout);
	}

	private void showSpinner() {
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setGravity(CENTER);

		ProgressBar progress = new ProgressBar(this);
		progress.setIndeterminate(true);
		layout.addView(progress);

		setContentView(layout);
	}

	private void storeLocalAuthor(final LocalAuthor a) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					identityManager.addLocalAuthor(a);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Storing author took " + duration + " ms");
					runOnUiThread(new Runnable() {
						public void run() {
							showButtons();
						}
					});
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void initializeTransports() {
		transports = new ArrayList<Transport>(3);

		Transport tor = new Transport();
		tor.id = new TransportId("tor");
		Plugin torPlugin = pluginManager.getPlugin(tor.id);
		if (torPlugin == null) tor.enabled = false;
		else tor.enabled = torPlugin.isRunning();
		tor.iconId = R.drawable.transport_tor;
		tor.textId = R.string.transport_tor;
		transports.add(tor);

		Transport bt = new Transport();
		bt.id = new TransportId("bt");
		Plugin btPlugin = pluginManager.getPlugin(bt.id);
		if (btPlugin == null) bt.enabled = false;
		else bt.enabled = btPlugin.isRunning();
		bt.iconId = R.drawable.transport_bt;
		bt.textId = R.string.transport_bt;
		transports.add(bt);

		Transport lan = new Transport();
		lan.id = new TransportId("lan");
		Plugin lanPlugin = pluginManager.getPlugin(lan.id);
		if (lanPlugin == null) lan.enabled = false;
		else lan.enabled = lanPlugin.isRunning();
		lan.iconId = R.drawable.transport_lan;
		lan.textId = R.string.transport_lan;
		transports.add(lan);

		transportsAdapter = new BaseAdapter() {
			@Override
			public int getCount() {
				return transports.size();
			}

			@Override
			public Transport getItem(int position) {
				return transports.get(position);
			}

			@Override
			public long getItemId(int position) {
				return 0;
			}

			@Override
			public View getView(int position, View convertView,
					ViewGroup parent) {
				LayoutInflater inflater = (LayoutInflater) getSystemService(
						Context.LAYOUT_INFLATER_SERVICE);
				ViewGroup view = (ViewGroup) inflater
						.inflate(R.layout.list_item_transport, parent, false);

				Transport t = getItem(position);
				Resources r = getResources();

				int c;
				if (t.enabled) {
					c = r.getColor(R.color.briar_green_light);
				} else {
					c = r.getColor(android.R.color.tertiary_text_light);
				}

				ImageView icon = (ImageView) view.findViewById(R.id.imageView);
				icon.setImageDrawable(r.getDrawable(t.iconId));
				icon.setColorFilter(c);

				TextView text = (TextView) view.findViewById(R.id.textView);
				text.setText(getString(t.textId));
				text.setTextColor(c);

				return view;
			}
		};
	}

	private void setTransport(final TransportId id, final boolean enabled) {
		runOnUiThread(new Runnable() {
			public void run() {
				if (transports == null || transportsAdapter == null) return;

				for (Transport t : transports) {
					if (t.id.equals(id)) {
						t.enabled = enabled;
						break;
					}
				}

				transportsAdapter.notifyDataSetChanged();
			}
		});
	}

	private static class Transport {
		TransportId id;
		boolean enabled;
		int iconId;
		int textId;
	}
}
