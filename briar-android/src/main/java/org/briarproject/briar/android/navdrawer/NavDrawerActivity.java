package org.briarproject.briar.android.navdrawer;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.navigation.NavigationView.OnNavigationItemSelectedListener;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.BluetoothConstants;
import org.briarproject.bramble.api.plugin.LanTcpConstants;
import org.briarproject.bramble.api.plugin.Plugin.State;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.blog.FeedFragment;
import org.briarproject.briar.android.contact.ContactListFragment;
import org.briarproject.briar.android.forum.ForumListFragment;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;
import org.briarproject.briar.android.logout.SignOutFragment;
import org.briarproject.briar.android.privategroup.list.GroupListFragment;
import org.briarproject.briar.android.settings.SettingsActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static androidx.core.view.GravityCompat.START;
import static androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED;
import static androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.RUNNING;
import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;
import static org.briarproject.bramble.api.plugin.Plugin.State.ENABLING;
import static org.briarproject.bramble.api.plugin.Plugin.State.STARTING_STOPPING;
import static org.briarproject.briar.android.BriarService.EXTRA_STARTUP_FAILED;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_PASSWORD;
import static org.briarproject.briar.android.navdrawer.IntentRouter.handleExternalIntent;
import static org.briarproject.briar.android.util.UiUtils.getDaysUntilExpiry;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class NavDrawerActivity extends BriarActivity implements
		BaseFragmentListener, OnNavigationItemSelectedListener {

	private static final Logger LOG =
			getLogger(NavDrawerActivity.class.getName());

	public static Uri CONTACT_URI =
			Uri.parse("briar-content://org.briarproject.briar/contact");
	public static Uri GROUP_URI =
			Uri.parse("briar-content://org.briarproject.briar/group");
	public static Uri FORUM_URI =
			Uri.parse("briar-content://org.briarproject.briar/forum");
	public static Uri BLOG_URI =
			Uri.parse("briar-content://org.briarproject.briar/blog");
	public static Uri CONTACT_ADDED_URI =
			Uri.parse("briar-content://org.briarproject.briar/contact/added");
	public static Uri SIGN_OUT_URI =
			Uri.parse("briar-content://org.briarproject.briar/sign-out");

	private NavDrawerViewModel navDrawerViewModel;
	private PluginViewModel pluginViewModel;
	private ActionBarDrawerToggle drawerToggle;

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	@Inject
	LifecycleManager lifecycleManager;

	private DrawerLayout drawerLayout;
	private NavigationView navigation;

	private List<Transport> transports;
	private BaseAdapter transportsAdapter;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);
		exitIfStartupFailed(getIntent());
		setContentView(R.layout.activity_nav_drawer);

		ViewModelProvider provider =
				ViewModelProviders.of(this, viewModelFactory);
		navDrawerViewModel = provider.get(NavDrawerViewModel.class);
		pluginViewModel = provider.get(PluginViewModel.class);

		navDrawerViewModel.showExpiryWarning()
				.observe(this, this::showExpiryWarning);
		navDrawerViewModel.shouldAskForDozeWhitelisting().observe(this, ask -> {
			if (ask) showDozeDialog(getString(R.string.setup_doze_intro));
		});

		Toolbar toolbar = findViewById(R.id.toolbar);
		drawerLayout = findViewById(R.id.drawer_layout);
		navigation = findViewById(R.id.navigation);
		GridView transportsView = findViewById(R.id.transportsView);

		setSupportActionBar(toolbar);
		ActionBar actionBar = requireNonNull(getSupportActionBar());
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeButtonEnabled(true);

		drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,
				R.string.nav_drawer_open_description,
				R.string.nav_drawer_close_description);
		drawerLayout.addDrawerListener(drawerToggle);
		navigation.setNavigationItemSelectedListener(this);

		initializeTransports();
		transportsView.setAdapter(transportsAdapter);

		lockManager.isLockable().observe(this, this::setLockVisible);

		if (lifecycleManager.getLifecycleState().isAfter(RUNNING)) {
			showSignOutFragment();
		} else if (state == null) {
			startFragment(ContactListFragment.newInstance(),
					R.id.nav_btn_contacts);
		}
		if (state == null) {
			// do not call this again when there's existing state
			onNewIntent(getIntent());
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		lockManager.checkIfLockable();
		navDrawerViewModel.checkExpiryWarning();
	}

	@Override
	protected void onActivityResult(int request, int result,
			@Nullable Intent data) {
		super.onActivityResult(request, result, data);
		if (request == REQUEST_PASSWORD && result == RESULT_OK) {
			navDrawerViewModel.checkDozeWhitelisting();
		}
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		// will call System.exit()
		exitIfStartupFailed(intent);

		if ("briar-content".equals(intent.getScheme())) {
			handleContentIntent(intent);
		} else {
			handleExternalIntent(this, intent);
		}
	}

	private void handleContentIntent(Intent intent) {
		Uri uri = intent.getData();
		// TODO don't create new instances if they are on the stack (#606)
		if (CONTACT_URI.equals(uri) || CONTACT_ADDED_URI.equals(uri)) {
			startFragment(ContactListFragment.newInstance(),
					R.id.nav_btn_contacts);
		} else if (GROUP_URI.equals(uri)) {
			startFragment(GroupListFragment.newInstance(), R.id.nav_btn_groups);
		} else if (FORUM_URI.equals(uri)) {
			startFragment(ForumListFragment.newInstance(), R.id.nav_btn_forums);
		} else if (BLOG_URI.equals(uri)) {
			startFragment(FeedFragment.newInstance(), R.id.nav_btn_blogs);
		} else if (SIGN_OUT_URI.equals(uri)) {
			signOut(false, false);
		}
	}

	private void exitIfStartupFailed(Intent intent) {
		if (intent.getBooleanExtra(EXTRA_STARTUP_FAILED, false)) {
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
	public boolean onNavigationItemSelected(@NonNull MenuItem item) {
		drawerLayout.closeDrawer(START);
		clearBackStack();
		if (item.getItemId() == R.id.nav_btn_lock) {
			lockManager.setLocked(true);
			ActivityCompat.finishAfterTransition(this);
			return false;
		} else {
			loadFragment(item.getItemId());
			// Don't display the Settings item as checked
			return item.getItemId() != R.id.nav_btn_settings;
		}
	}

	@Override
	public void onBackPressed() {
		if (drawerLayout.isDrawerOpen(START)) {
			drawerLayout.closeDrawer(START);
		} else {
			FragmentManager fm = getSupportFragmentManager();
			if (fm.findFragmentByTag(SignOutFragment.TAG) != null) {
				finish();
			} else if (fm.getBackStackEntryCount() == 0
					&& fm.findFragmentByTag(ContactListFragment.TAG) == null) {
				/*
				 * This makes sure that the first fragment (ContactListFragment) the
				 * user sees is the same as the last fragment the user sees before
				 * exiting. This models the typical Google navigation behaviour such
				 * as in Gmail/Inbox.
				 */
				startFragment(ContactListFragment.newInstance(),
						R.id.nav_btn_contacts);
			} else {
				super.onBackPressed();
			}
		}
	}

	@Override
	public void onPostCreate(@Nullable Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		drawerToggle.syncState();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		drawerToggle.onConfigurationChanged(newConfig);
	}

	private void showSignOutFragment() {
		drawerLayout.setDrawerLockMode(LOCK_MODE_LOCKED_CLOSED);
		startFragment(new SignOutFragment());
	}

	private void signOut() {
		drawerLayout.setDrawerLockMode(LOCK_MODE_LOCKED_CLOSED);
		signOut(false, false);
		finish();
	}

	private void startFragment(BaseFragment fragment, int itemId) {
		navigation.setCheckedItem(itemId);
		startFragment(fragment);
	}

	private void startFragment(BaseFragment fragment) {
		if (getSupportFragmentManager().getBackStackEntryCount() == 0)
			startFragment(fragment, false);
		else startFragment(fragment, true);
	}

	private void startFragment(BaseFragment fragment,
			boolean isAddedToBackStack) {
		FragmentTransaction trans =
				getSupportFragmentManager().beginTransaction()
						.setCustomAnimations(R.anim.fade_in,
								R.anim.fade_out, R.anim.fade_in,
								R.anim.fade_out)
						.replace(R.id.fragmentContainer, fragment,
								fragment.getUniqueTag());
		if (isAddedToBackStack) {
			trans.addToBackStack(fragment.getUniqueTag());
		}
		trans.commit();
	}

	private void clearBackStack() {
		getSupportFragmentManager().popBackStackImmediate(null,
				POP_BACK_STACK_INCLUSIVE);
	}

	@Override
	public void handleDbException(DbException e) {
		// Do nothing for now
	}

	private void setLockVisible(boolean visible) {
		MenuItem item = navigation.getMenu().findItem(R.id.nav_btn_lock);
		if (item != null) item.setVisible(visible);
	}

	private void showExpiryWarning(boolean show) {
		int daysUntilExpiry = getDaysUntilExpiry();
		if (daysUntilExpiry < 0) {
			signOut();
			return;
		}

		ViewGroup expiryWarning = findViewById(R.id.expiryWarning);
		if (show) {
			// show expiry warning text
			TextView expiryWarningText =
					expiryWarning.findViewById(R.id.expiryWarningText);
			String text = getResources().getQuantityString(
					R.plurals.expiry_warning, daysUntilExpiry, daysUntilExpiry);
			expiryWarningText.setText(text);
			// make close button functional
			ImageView expiryWarningClose =
					expiryWarning.findViewById(R.id.expiryWarningClose);
			expiryWarningClose.setOnClickListener(v ->
					navDrawerViewModel.expiryWarningDismissed());
			expiryWarning.setVisibility(VISIBLE);
		} else {
			expiryWarning.setVisibility(GONE);
		}
	}

	private void initializeTransports() {
		transports = new ArrayList<>(3);

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
				if (convertView != null) {
					view = convertView;
				} else {
					LayoutInflater inflater = getLayoutInflater();
					view = inflater.inflate(R.layout.list_item_transport,
							parent, false);
				}

				Transport t = getItem(position);

				ImageView icon = view.findViewById(R.id.imageView);
				icon.setImageDrawable(ContextCompat
						.getDrawable(NavDrawerActivity.this, t.iconId));
				icon.setColorFilter(getIconColour(t.state));

				TextView text = view.findViewById(R.id.textView);
				text.setText(getString(t.textId));

				return view;
			}
		};

		transports.add(createTransport(TorConstants.ID,
				R.drawable.transport_tor, R.string.transport_tor));
		transports.add(createTransport(LanTcpConstants.ID,
				R.drawable.transport_lan, R.string.transport_lan));
		transports.add(createTransport(BluetoothConstants.ID,
				R.drawable.transport_bt, R.string.transport_bt));
	}

	private int getIconColour(State state) {
		int colorRes;
		if (state == ACTIVE) {
			colorRes = R.color.briar_green_light;
		} else if (state == ENABLING) {
			colorRes = R.color.briar_yellow;
		} else {
			colorRes = android.R.color.tertiary_text_light;
		}
		return ContextCompat.getColor(this, colorRes);
	}

	private Transport createTransport(TransportId id, @DrawableRes int iconId,
			@StringRes int textId) {
		Transport transport = new Transport(iconId, textId);
		pluginViewModel.getPluginState(id).observe(this, state -> {
			transport.state = state;
			transportsAdapter.notifyDataSetChanged();
		});
		return transport;
	}

	private static class Transport {

		@DrawableRes
		private final int iconId;
		@StringRes
		private final int textId;

		private State state = STARTING_STOPPING;

		private Transport(@DrawableRes int iconId, @StringRes int textId) {
			this.iconId = iconId;
			this.textId = textId;
		}
	}
}
