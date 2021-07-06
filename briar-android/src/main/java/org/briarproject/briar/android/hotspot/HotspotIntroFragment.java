package org.briarproject.briar.android.hotspot;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;

import javax.inject.Inject;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import static android.content.pm.ApplicationInfo.FLAG_TEST_ONLY;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static androidx.transition.TransitionManager.beginDelayedTransition;
import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG;
import static org.briarproject.briar.android.AppModule.getAndroidComponent;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class HotspotIntroFragment extends Fragment {

	public final static String TAG = HotspotIntroFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private HotspotViewModel viewModel;
	private ConditionManager conditionManager;

	private Button startButton;
	private ProgressBar progressBar;
	private TextView progressTextView;

	private final ActivityResultLauncher<String> locationRequest =
			registerForActivityResult(new RequestPermission(), granted -> {
				conditionManager.onRequestPermissionResult(granted);
				startHotspot();
			});
	private final ActivityResultLauncher<Intent> wifiRequest =
			registerForActivityResult(new StartActivityForResult(), result -> {
				conditionManager.onRequestWifiEnabledResult();
				startHotspot();
			});

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		FragmentActivity activity = requireActivity();
		getAndroidComponent(activity).inject(this);
		viewModel = new ViewModelProvider(activity, viewModelFactory)
				.get(HotspotViewModel.class);
		conditionManager =
				new ConditionManager(activity, locationRequest, wifiRequest);
	}

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View v = inflater
				.inflate(R.layout.fragment_hotspot_intro, container, false);

		startButton = v.findViewById(R.id.startButton);
		progressBar = v.findViewById(R.id.progressBar);
		progressTextView = v.findViewById(R.id.progressTextView);

		startButton.setOnClickListener(button -> {
			startButton.setEnabled(false);
			conditionManager.startConditionChecks();
		});

		return v;
	}

	@Override
	public void onStart() {
		super.onStart();
		conditionManager.resetPermissions();
	}

	private void startHotspot() {
		startButton.setEnabled(true);
		if (conditionManager.checkAndRequestConditions()) {
			showInstallWarningIfNeeded();
			beginDelayedTransition((ViewGroup) requireView());
			startButton.setVisibility(INVISIBLE);
			progressBar.setVisibility(VISIBLE);
			progressTextView.setVisibility(VISIBLE);
			viewModel.startHotspot();
		}
	}

	private void showInstallWarningIfNeeded() {
		Context ctx = requireContext();
		ApplicationInfo applicationInfo;
		try {
			applicationInfo = ctx.getPackageManager()
					.getApplicationInfo(ctx.getPackageName(), 0);
		} catch (PackageManager.NameNotFoundException e) {
			throw new AssertionError(e);
		}
		// test only apps can not be installed
		if ((applicationInfo.flags & FLAG_TEST_ONLY) == FLAG_TEST_ONLY) {
			int color = getResources().getColor(R.color.briar_red_500);
			Snackbar.make(requireView(), R.string.hotspot_flag_test,
					LENGTH_LONG).setBackgroundTint(color).show();
		}
	}

}
