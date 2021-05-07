package org.briarproject.briar.android.hotspot;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;

import javax.inject.Inject;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import static android.widget.Toast.LENGTH_SHORT;
import static org.briarproject.briar.android.AppModule.getAndroidComponent;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public abstract class AbstractTabsFragment extends Fragment {

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	protected HotspotViewModel viewModel;

	protected Button stopButton;
	protected Button connectedButton;

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		getAndroidComponent(requireContext()).inject(this);
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(HotspotViewModel.class);
	}

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater
				.inflate(R.layout.fragment_hotspot_tabs, container, false);
	}

	@Override
	@CallSuper
	public void onViewCreated(@NonNull View view,
			@Nullable Bundle savedInstanceState) {
		TabAdapter tabAdapter = new TabAdapter(this);
		ViewPager2 viewPager = view.findViewById(R.id.pager);
		viewPager.setAdapter(tabAdapter);
		TabLayout tabLayout = view.findViewById(R.id.tabLayout);
		new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
			// tabs are set in XML, but are just dummies that don't get added
			if (position == 0) {
				tab.setText(R.string.hotspot_tab_manual);
				tab.setIcon(R.drawable.forum_item_create_white);
			} else if (position == 1) {
				tab.setText(R.string.qr_code);
				tab.setIcon(R.drawable.ic_qr_code);
			} else throw new AssertionError();
		}).attach();

		stopButton = view.findViewById(R.id.stopButton);
		stopButton.setOnClickListener(v -> {
			Toast.makeText(requireContext(), "Not yet implemented",
					LENGTH_SHORT).show();
		});
		connectedButton = view.findViewById(R.id.connectedButton);
	}

	protected abstract Fragment getFirstFragment();

	protected abstract Fragment getSecondFragment();

	private class TabAdapter extends FragmentStateAdapter {
		private TabAdapter(Fragment fragment) {
			super(fragment);
		}

		@NonNull
		@Override
		public Fragment createFragment(int position) {
			if (position == 0) return getFirstFragment();
			if (position == 1) return getSecondFragment();
			throw new AssertionError();
		}

		@Override
		public int getItemCount() {
			return 2;
		}
	}

}
