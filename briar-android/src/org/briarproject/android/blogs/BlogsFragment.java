package org.briarproject.android.blogs;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.fragment.BaseFragment;

import static android.view.View.GONE;

public class BlogsFragment extends BaseFragment {

	public final static String TAG = BlogsFragment.class.getName();

	private static final String SELECTED_TAB = "selectedTab";
	private TabLayout tabLayout;

	public static BlogsFragment newInstance() {
		
		Bundle args = new Bundle();
		
		BlogsFragment fragment = new BlogsFragment();
		fragment.setArguments(args);
		return fragment;
	}
	
	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		View v = inflater.inflate(R.layout.fragment_blogs, container,
				false);

		tabLayout = (TabLayout) v.findViewById(R.id.tabLayout);
		ViewPager viewPager = (ViewPager) v.findViewById(R.id.pager);

		String[] titles = {
				getString(R.string.blogs_feed),
				getString(R.string.blogs_my_blogs),
				getString(R.string.blogs_blog_list),
				getString(R.string.blogs_available_blogs),
				getString(R.string.blogs_drafts)
		};
		TabAdapter tabAdapter =
				new TabAdapter(getChildFragmentManager(), titles);
		viewPager.setAdapter(tabAdapter);
		tabLayout.setupWithViewPager(viewPager);

		tabLayout.setVisibility(GONE);

		if (savedInstanceState != null) {
			int position = savedInstanceState.getInt(SELECTED_TAB, 0);
			viewPager.setCurrentItem(position);
		}
		return v;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(SELECTED_TAB, tabLayout.getSelectedTabPosition());
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}


	private class TabAdapter extends FragmentStatePagerAdapter {
		private String[] titles;

		TabAdapter(FragmentManager fm, String[] titles) {
			super(fm);
			this.titles = titles;
		}

		@Override
		public int getCount() {
			return 1;
//			return titles.length;
		}

		@Override
		public Fragment getItem(int position) {
			return FeedFragment.newInstance();
//			switch (position) {
//				case 0:
//					return FeedFragment.newInstance();
//				case 1:
//					return new MyBlogsFragment();
//				default:
//					return BlogListFragment.newInstance(position);
//			}
		}

		@Override
		public CharSequence getPageTitle(int position) {
			return titles[position];
		}
	}

}
