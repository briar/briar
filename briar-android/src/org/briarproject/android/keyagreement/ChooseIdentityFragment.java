package org.briarproject.android.keyagreement;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Spinner;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.identity.CreateIdentityActivity;
import org.briarproject.android.identity.LocalAuthorItem;
import org.briarproject.android.identity.LocalAuthorItemComparator;
import org.briarproject.android.identity.LocalAuthorSpinnerAdapter;
import org.briarproject.api.db.DbException;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;

import java.util.Collection;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.app.Activity.RESULT_OK;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.identity.LocalAuthorItem.NEW;

public class ChooseIdentityFragment extends BaseFragment
		implements OnItemSelectedListener {

	interface IdentitySelectedListener {
		void identitySelected(AuthorId localAuthorId);
	}

	public static final String TAG = "ChooseIdentityFragment";

	private static final Logger LOG =
			Logger.getLogger(ChooseIdentityFragment.class.getName());

	private static final int REQUEST_CREATE_IDENTITY = 1;

	private IdentitySelectedListener lsnr;
	private LocalAuthorSpinnerAdapter adapter;
	private Spinner spinner;
	private View button;
	private AuthorId localAuthorId;

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile IdentityManager identityManager;

	public static ChooseIdentityFragment newInstance() {
		
		Bundle args = new Bundle();
		
		ChooseIdentityFragment fragment = new ChooseIdentityFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		try {
			lsnr = (IdentitySelectedListener) context;
		} catch (ClassCastException e) {
			throw new ClassCastException(
					"Using class must implement IdentitySelectedListener");
		}
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_keyagreement_id, container,
				false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		adapter = new LocalAuthorSpinnerAdapter(getActivity(), false);
		spinner = (Spinner) view.findViewById(R.id.spinner);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(this);

		button = view.findViewById(R.id.continueButton);
		button.setEnabled(false);
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				lsnr.identitySelected(localAuthorId);
			}
		});

	}

	@Override
	public void onStart() {
		super.onStart();
		loadLocalAuthors();
	}

	private void loadLocalAuthors() {
		listener.runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					Collection<LocalAuthor> authors =
							identityManager.getLocalAuthors();
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading authors took " + duration + " ms");
					displayLocalAuthors(authors);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayLocalAuthors(final Collection<LocalAuthor> authors) {
		listener.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				adapter.clear();
				for (LocalAuthor a : authors)
					adapter.add(new LocalAuthorItem(a));
				adapter.sort(LocalAuthorItemComparator.INSTANCE);
				// If a local author has been selected, select it again
				if (localAuthorId == null) return;
				int count = adapter.getCount();
				for (int i = 0; i < count; i++) {
					LocalAuthorItem item = adapter.getItem(i);
					if (item == NEW) continue;
					if (item.getLocalAuthor().getId().equals(localAuthorId)) {
						spinner.setSelection(i);
						return;
					}
				}
			}
		});
	}

	private void setLocalAuthorId(AuthorId authorId) {
		localAuthorId = authorId;
		button.setEnabled(localAuthorId != null);
	}

	@Override
	public void onItemSelected(AdapterView<?> parent, View view, int position,
			long id) {
		LocalAuthorItem item = adapter.getItem(position);
		if (item == NEW) {
			setLocalAuthorId(null);
			Intent i = new Intent(getActivity(), CreateIdentityActivity.class);
			startActivityForResult(i, REQUEST_CREATE_IDENTITY);
		} else {
			setLocalAuthorId(item.getLocalAuthor().getId());
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> parent) {
		setLocalAuthorId(null);
	}

	@Override
	public void onActivityResult(int request, int result, Intent data) {
		if (request == REQUEST_CREATE_IDENTITY && result == RESULT_OK) {
			byte[] b = data.getByteArrayExtra("briar.LOCAL_AUTHOR_ID");
			if (b == null) throw new IllegalStateException();
			setLocalAuthorId(new AuthorId(b));
			loadLocalAuthors();
		} else
			super.onActivityResult(request, result, data);
	}
}
