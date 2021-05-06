package org.briarproject.briar.android.blog;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.lifecycle.ViewModelProvider;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.inputmethod.EditorInfo.IME_ACTION_DONE;
import static org.briarproject.briar.android.util.UiUtils.hideSoftKeyboard;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class RssFeedImportFragment extends BaseFragment {
	public static final String TAG = RssFeedImportFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;
	private RssFeedViewModel viewModel;

	private EditText urlInput;
	private Button importButton;
	private ProgressBar progressBar;

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);

		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(RssFeedViewModel.class);
	}

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		requireActivity().setTitle(getString(R.string.blogs_rss_feeds_import));
		View v = inflater.inflate(R.layout.fragment_rss_feed_import,
				container, false);

		urlInput = v.findViewById(R.id.urlInput);
		urlInput.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				enableOrDisableImportButton();
			}
		});
		urlInput.setOnEditorActionListener((view, actionId, event) -> {
			if (actionId == IME_ACTION_DONE && importButton.isEnabled() &&
					importButton.getVisibility() == VISIBLE) {
				publish();
				return true;
			}
			return false;
		});

		importButton = v.findViewById(R.id.importButton);
		importButton.setOnClickListener(view -> publish());

		progressBar = v.findViewById(R.id.progressBar);

		viewModel.getIsImporting().observe(getViewLifecycleOwner(),
				this::onIsImporting);

		return v;
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	private void enableOrDisableImportButton() {
		String url = urlInput.getText().toString();
		importButton.setEnabled(viewModel.validateAndNormaliseUrl(url) != null);
	}

	private void publish() {
		String url = viewModel
				.validateAndNormaliseUrl(urlInput.getText().toString());
		if (url == null) throw new AssertionError();
		viewModel.importFeed(url);
	}

	private void onIsImporting(Boolean importing) {
		if (importing) {
			// show progress bar, hide import button
			importButton.setVisibility(GONE);
			progressBar.setVisibility(VISIBLE);
			hideSoftKeyboard(urlInput);
		} else {
			// show publish button, hide progress bar
			importButton.setVisibility(VISIBLE);
			progressBar.setVisibility(GONE);
		}
	}
}
