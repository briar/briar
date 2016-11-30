package org.briarproject.briar.android.navdrawer;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.design.widget.NavigationView.OnNavigationItemSelectedListener;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.bramble.api.plugin.BluetoothConstants;
import org.briarproject.bramble.api.plugin.LanTcpConstants;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarFragmentActivity;
import org.briarproject.briar.android.blog.FeedFragment;
import org.briarproject.briar.android.contact.ContactListFragment;
import org.briarproject.briar.android.forum.ForumListFragment;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;
import org.briarproject.briar.android.fragment.SignOutFragment;
import org.briarproject.briar.android.privategroup.list.GroupListFragment;
import org.briarproject.briar.android.settings.SettingsActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.support.v4.view.GravityCompat.START;
import static android.support.v4.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED;

public class NavDrawerActivity extends BriarFragmentActivity implements
		BaseFragmentListener, TransportStateListener,
		OnNavigationItemSelectedListener {

	public static final String INTENT_CONTACTS = "intent_contacts";
	public static final String INTENT_GROUPS = "intent_groups";
	public static final String INTENT_FORUMS = "intent_forums";
	public static final String INTENT_BLOGS = "intent_blogs";

	private static final Logger LOG =
			Logger.getLogger(NavDrawerActivity.class.getName());

	private ActionBarDrawerToggle drawerToggle;

	@Inject
	NavDrawerController controller;

	private DrawerLayout drawerLayout;
	private NavigationView navigation;

	private List<Transport> transports;
	private BaseAdapter transportsAdapter;

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		exitIfStartupFailed(intent);
		// TODO don't create new instances if they are on the stack (#606)
		if (intent.getBooleanExtra(INTENT_GROUPS, false)) {
			startFragment(GroupListFragment.newInstance());
		} else if (intent.getBooleanExtra(INTENT_FORUMS, false)) {
			startFragment(ForumListFragment.newInstance());
		} else if (intent.getBooleanExtra(INTENT_CONTACTS, false)) {
			startFragment(ContactListFragment.newInstance());
		} else if (intent.getBooleanExtra(INTENT_BLOGS, false)) {
			startFragment(FeedFragment.newInstance());
		}
		setIntent(null);
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@SuppressWarnings("ConstantConditions")
	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		exitIfStartupFailed(getIntent());
		setContentView(R.layout.activity_nav_drawer);

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		navigation = (NavigationView) findViewById(R.id.navigation);
		GridView transportsView = (GridView) findViewById(R.id.transportsView);

		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);

		drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,
				R.string.nav_drawer_open_description,
				R.string.nav_drawer_close_description);
		drawerLayout.addDrawerListener(drawerToggle);
		navigation.setNavigationItemSelectedListener(this);

		initializeTransports(getLayoutInflater());
		transportsView.setAdapter(transportsAdapter);

		if (state == null) {
			navigation.setCheckedItem(R.id.nav_btn_contacts);
			startFragment(ContactListFragment.newInstance());
		}
		if (getIntent() != null) {
			onNewIntent(getIntent());
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		updateTransports();
	}

	private void exitIfStartupFailed(Intent intent) {
		if (intent.getBooleanExtra(KEY_STARTUP_FAILED, false)) {
			finish();
			LOG.info("Exiting");
			System.exit(0);
		}
	}

	private void loadFragment(int fragmentId) {
		// TODO re-use fragments from the manager when possible (#606)
		switch (fragmentId) {
			case R.id.nav_btn_contacts:
				startFragment(ContactListFragment.newInstance());
				break;
			case R.id.nav_btn_groups:
				startFragment(GroupListFragment.newInstance());
				break;
			case R.id.nav_btn_forums:
				startFragment(ForumListFragment.newInstance());
				break;
			case R.id.nav_btn_blogs:
				startFragment(FeedFragment.newInstance());
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
	public void onFragmentCreated(String tag) {
		super.onFragmentCreated(tag);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar == null) return;

		if (tag.equals(ContactListFragment.TAG)) {
			actionBar.setTitle(R.string.contact_list_button);
		} else if (tag.equals(GroupListFragment.TAG)) {
			actionBar.setTitle(R.string.groups_button);
		} else if (tag.equals(ForumListFragment.TAG)) {
			actionBar.setTitle(R.string.forums_button);
		} else if (tag.equals(FeedFragment.TAG)) {
			actionBar.setTitle(R.string.blogs_button);
		}
	}

	@Override
	public boolean onNavigationItemSelected(MenuItem item) {
		drawerLayout.closeDrawer(START);
		clearBackStack();
		loadFragment(item.getItemId());
		//Don't display the Settings Item as checked
		if (item.getItemId() == R.id.nav_btn_settings){
			return false;
		}
		return true;
	}

	@Override
	public void onBackPressed() {
		if (getSupportFragmentManager().getBackStackEntryCount() == 0
				&& drawerLayout.isDrawerOpen(START)) {
			drawerLayout.closeDrawer(START);
			return;
		}
		// Check the Contacts item because we always return to Contacts here
		navigation.setCheckedItem(R.id.nav_btn_contacts);
		super.onBackPressed();
	}

	@Override
	public void onPostCreate(Bundle savedInstanceState) {
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
		drawerLayout.setDrawerLockMode(LOCK_MODE_LOCKED_CLOSED);
		startFragment(new SignOutFragment());
		super.signOut();
	}

	private void initializeTransports(final LayoutInflater inflater) {
		transports = new ArrayList<>(3);

		Transport tor = new Transport();
		tor.id = TorConstants.ID;
		tor.enabled = controller.isTransportRunning(tor.id);
		tor.iconId = R.drawable.transport_tor;
		tor.textId = R.string.transport_tor;
		transports.add(tor);

		Transport bt = new Transport();
		bt.id = BluetoothConstants.ID;
		bt.enabled = controller.isTransportRunning(bt.id);
		bt.iconId = R.drawable.transport_bt;
		bt.textId = R.string.transport_bt;
		transports.add(bt);

		Transport lan = new Transport();
		lan.id = LanTcpConstants.ID;
		lan.enabled = controller.isTransportRunning(lan.id);
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
				View view;
				if (convertView != null) view = convertView;
				else view =
						inflater.inflate(R.layout.list_item_transport, parent,
								false);

				Transport t = getItem(position);
				int c;
				if (t.enabled) {
					c = ContextCompat.getColor(NavDrawerActivity.this,
							R.color.briar_green_light);
				} else {
					c = ContextCompat.getColor(NavDrawerActivity.this,
							android.R.color.tertiary_text_light);
				}

				ImageView icon = (ImageView) view.findViewById(R.id.imageView);
				icon.setImageDrawable(ContextCompat
						.getDrawable(NavDrawerActivity.this, t.iconId));
				icon.setColorFilter(c);

				TextView text = (TextView) view.findViewById(R.id.textView);
				text.setText(getString(t.textId));

				return view;
			}
		};
	}

	private void setTransport(final TransportId id, final boolean enabled) {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
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
			t.enabled = controller.isTransportRunning(t.id);
		}
		transportsAdapter.notifyDataSetChanged();
	}

	@Override
	public void stateUpdate(TransportId id, boolean enabled) {
		setTransport(id, enabled);
	}

	private static class Transport {

		private TransportId id;
		private boolean enabled;
		private int iconId;
		private int textId;
	}
}
