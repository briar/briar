package org.briarproject.android.blogs;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.fragment.BaseFragment;

import javax.inject.Inject;

import static android.support.v4.app.ActivityOptionsCompat.makeCustomAnimation;

public class MyBlogsFragment extends BaseFragment {

	public final static String TAG = MyBlogsFragment.class.getName();

	@Inject
	public MyBlogsFragment() {
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		setHasOptionsMenu(true);
		View v = inflater.inflate(R.layout.fragment_blogs_my, container,
				false);

		TextView numView = (TextView) v.findViewById(R.id.num);
		numView.setText("My Blogs");

		return v;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		listener.getActivityComponent().inject(this);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.blogs_my_actions, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
			case R.id.action_create_blog:
				Intent intent =
						new Intent(getContext(), CreateBlogActivity.class);
				ActivityOptionsCompat options =
						makeCustomAnimation(getActivity(),
								android.R.anim.slide_in_left,
								android.R.anim.slide_out_right);
				ActivityCompat.startActivity(getActivity(), intent,
						options.toBundle());
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

}
