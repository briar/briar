package org.briarproject.briar.android.hotspot;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;

import java.util.List;

import javax.inject.Inject;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.EXTRA_STREAM;
import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static android.os.Build.VERSION.SDK_INT;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static androidx.transition.TransitionManager.beginDelayedTransition;
import static org.briarproject.briar.android.AppModule.getAndroidComponent;
import static org.briarproject.briar.android.hotspot.HotspotViewModel.getApkFileName;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class HotspotHelpFragment extends Fragment {

	public final static String TAG = HotspotHelpFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private HotspotViewModel viewModel;
	private final ActivityResultLauncher<String> launcher =
			registerForActivityResult(new CreateDocument(), uri ->
					viewModel.exportApk(uri)
			);
	private Button button;
	private ProgressBar progressBar;

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		FragmentActivity activity = requireActivity();
		getAndroidComponent(activity).inject(this);
		viewModel = new ViewModelProvider(activity, viewModelFactory)
				.get(HotspotViewModel.class);
	}

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater
				.inflate(R.layout.fragment_hotspot_help, container, false);
	}

	@Override
	public void onViewCreated(View v, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(v, savedInstanceState);
		button = v.findViewById(R.id.fallbackButton);
		progressBar = v.findViewById(R.id.progressBar);
		button.setOnClickListener(view -> {
			beginDelayedTransition((ViewGroup) requireView());
			button.setVisibility(INVISIBLE);
			progressBar.setVisibility(VISIBLE);

			if (SDK_INT >= 19) launcher.launch(getApkFileName());
			else viewModel.exportApk();
		});
		viewModel.getSavedApkToUri().observeEvent(this, this::shareUri);
	}

	private void shareUri(Uri uri) {
		beginDelayedTransition((ViewGroup) requireView());
		button.setVisibility(VISIBLE);
		progressBar.setVisibility(INVISIBLE);

		Intent i = new Intent(ACTION_SEND);
		i.putExtra(EXTRA_STREAM, uri);
		i.setType("application/zip");
		i.addFlags(FLAG_GRANT_READ_URI_PERMISSION);
		Context ctx = requireContext();
		if (SDK_INT <= 19) {
			// Workaround for Android bug:
			// ctx.grantUriPermission also needed for Android 4
			List<ResolveInfo> resInfoList = ctx.getPackageManager()
					.queryIntentActivities(i, MATCH_DEFAULT_ONLY);
			for (ResolveInfo resolveInfo : resInfoList) {
				String packageName = resolveInfo.activityInfo.packageName;
				ctx.grantUriPermission(packageName, uri,
						FLAG_GRANT_READ_URI_PERMISSION);
			}
		}
		startActivity(Intent.createChooser(i, null));
	}

}
