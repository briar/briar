package org.briarproject.android.blogs;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.fragment.BaseFragment;

public class BlogListFragment extends BaseFragment {

	public final static String TAG = BlogListFragment.class.getName();

	static BlogListFragment newInstance(int num) {
		BlogListFragment f = new BlogListFragment();

		Bundle args = new Bundle();
		args.putInt("num", num);
		f.setArguments(args);

		return f;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		View v = inflater.inflate(R.layout.fragment_blogs_list, container,
				false);

		TextView numView = (TextView) v.findViewById(R.id.num);
		String num = String.valueOf(getArguments().getInt("num"));
		numView.setText(num);

		return v;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

}
