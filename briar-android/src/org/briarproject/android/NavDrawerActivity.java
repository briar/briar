package org.briarproject.android;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.contact.ContactListFragment;
import org.briarproject.android.forum.ForumListFragment;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.util.CustomAnimations;
import org.briarproject.api.TransportId;
import org.briarproject.android.api.ReferenceManager;
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

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

public class NavDrawerActivity extends BriarFragmentActivity implements
		BaseFragment.BaseFragmentListener, EventListener {

	public static final String INTENT_CONTACTS = "intent_contacts";
	public static final String INTENT_FORUMS = "intent_forums";

	private static final Logger LOG =
			Logger.getLogger(NavDrawerActivity.class.getName());

	private ActionBarDrawerToggle drawerToggle;

	@Inject
	protected ReferenceManager referenceManager;
	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile IdentityManager identityManager;
	@Inject
	protected PluginManager pluginManager;
	@Inject
	protected volatile EventBus eventBus;

	private Toolbar toolbar;
	private DrawerLayout drawerLayout;
	private GridView transportsView;
	private TextView progressTitle;
	private ViewGroup progressViewGroup;

	private List<Transport> transports;
	private BaseAdapter transportsAdapter;

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		if (!isStartupFailed(intent)) {
			checkAuthorHandle(intent);
			clearBackStack();
			if (intent.getBooleanExtra(INTENT_FORUMS, false))
				startFragment(activityComponent.newForumListFragment());
			else if (intent.getBooleanExtra(INTENT_CONTACTS, false))
				startFragment(activityComponent.newContactListFragment());
		}
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@SuppressWarnings("ConstantConditions")
	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		if (isStartupFailed(getIntent()))
			return;

		setContentView(R.layout.activity_nav_drawer);

		toolbar = (Toolbar)findViewById(R.id.toolbar);
		drawerLayout = (DrawerLayout)findViewById(R.id.drawer_layout);
		transportsView = (GridView)findViewById(R.id.transportsView);
		progressTitle = (TextView)findViewById(R.id.title_progress_bar);
		progressViewGroup = (ViewGroup)findViewById(R.id.container_progress);

		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);

		drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,
				R.string.nav_drawer_open_description,
				R.string.nav_drawer_close_description
		) {

			public void onDrawerClosed(View view) {
				super.onDrawerClosed(view);
			}

			public void onDrawerOpened(View drawerView) {
				super.onDrawerOpened(drawerView);
			}
		};
		drawerLayout.setDrawerListener(drawerToggle);
		if (state == null) startFragment(activityComponent.newContactListFragment());
		checkAuthorHandle(getIntent());

		initializeTransports(getLayoutInflater());
		transportsView.setAdapter(transportsAdapter);

		welcomeMessageCheck();
	}

	private void welcomeMessageCheck() {
		SharedPreferences prefs = getSharedPreferences(PREFS_NAME,
				MODE_PRIVATE);
		if (!prefs.getBoolean(PREF_SEEN_WELCOME_MESSAGE, false)) {
			showMessageDialog(R.string.dialog_title_welcome,
					R.string.dialog_welcome_message);
			prefs.edit().putBoolean(PREF_SEEN_WELCOME_MESSAGE, true).apply();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		eventBus.addListener(this);
		updateTransports();
	}

	@Override
	protected void onPause() {
		super.onPause();
		eventBus.removeListener(this);
	}

	private void checkAuthorHandle(Intent intent) {
		long handle = intent.getLongExtra(KEY_LOCAL_AUTHOR_HANDLE, -1);
		if (handle != -1) {
			// The activity was launched from the setup wizard
			LocalAuthor a = referenceManager.removeReference(handle,
					LocalAuthor.class);
			if (a != null) {
				showLoadingScreen(true, R.string.progress_title_please_wait);
				storeLocalAuthor(a);
			}
		}
	}

	private boolean isStartupFailed(Intent intent) {
		if (intent.getBooleanExtra(KEY_STARTUP_FAILED, false)) {
			finish();
			LOG.info("Exiting");
			System.exit(0);
			return true;
		}
		return false;
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
							hideLoadingScreen();
						}
					});

				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	public void onNavigationClick(View view) {
		drawerLayout.closeDrawer(GravityCompat.START);
		clearBackStack();
		switch (view.getId()) {
			case R.id.nav_btn_contacts:
				startFragment(activityComponent.newContactListFragment());
				break;
			case R.id.nav_btn_forums:
				startFragment(activityComponent.newForumListFragment());
				break;
			case R.id.nav_btn_settings:
				startActivity(new Intent(this, SettingsActivity.class));
				break;
			case R.id.nav_btn_signout:
				signOut();
				break;
		}
	}


	@Override
	public void onBackPressed() {
		if (getSupportFragmentManager().getBackStackEntryCount() == 0
				&& drawerLayout.isDrawerOpen(GravityCompat.START)) {
			drawerLayout.closeDrawer(GravityCompat.START);
			return;
		}
		super.onBackPressed();
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		drawerToggle.syncState();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		drawerToggle.onConfigurationChanged(newConfig);
	}

	@Override
	protected void signOut() {
		showLoadingScreen(true, R.string.progress_title_logout);
		super.signOut();
	}

	@Override
	public void showLoadingScreen(boolean isBlocking, int stringId) {
		if (isBlocking) {
			// Disable navigation drawer slide to open
			drawerLayout
					.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
			CustomAnimations.animateHeight(toolbar, false, 250);
		}
		progressTitle.setText(stringId);
		progressViewGroup.setVisibility(View.VISIBLE);
	}

	@Override
	public void hideLoadingScreen() {
		drawerLayout
				.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
		CustomAnimations.animateHeight(toolbar, true, 250);
		progressViewGroup.setVisibility(View.INVISIBLE);
	}

	private void initializeTransports(final LayoutInflater inflater) {
		transports = new ArrayList<Transport>(3);

		Transport tor = new Transport();
		tor.id = new TransportId("tor");
		Plugin torPlugin = pluginManager.getPlugin(tor.id);
		tor.enabled = torPlugin != null && torPlugin.isRunning();
		tor.iconId = R.drawable.transport_tor;
		tor.textId = R.string.transport_tor;
		transports.add(tor);

		Transport bt = new Transport();
		bt.id = new TransportId("bt");
		Plugin btPlugin = pluginManager.getPlugin(bt.id);
		bt.enabled = btPlugin != null && btPlugin.isRunning();
		bt.iconId = R.drawable.transport_bt;
		bt.textId = R.string.transport_bt;
		transports.add(bt);

		Transport lan = new Transport();
		lan.id = new TransportId("lan");
		Plugin lanPlugin = pluginManager.getPlugin(lan.id);
		lan.enabled = lanPlugin != null && lanPlugin.isRunning();
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
						transportsAdapter.notifyDataSetChanged();
						break;
					}
				}
			}
		});
	}

	private void updateTransports() {
		if (transports == null || transportsAdapter == null) return;
		for (Transport t : transports) {
			Plugin plugin = pluginManager.getPlugin(t.id);
			t.enabled = plugin != null && plugin.isRunning();
		}
		transportsAdapter.notifyDataSetChanged();
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

	private static class Transport {
		TransportId id;
		boolean enabled;
		int iconId;
		int textId;
	}
}
