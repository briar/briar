package org.briarproject.briar.android.socialbackup;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.contactselection.ContactSelectorListener;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.magmacollective.darkcrystal.secretsharingwrapper.SecretSharingWrapper;

import static java.util.Objects.requireNonNull;

public class ThresholdSelectorFragment extends BaseFragment {

    public static final String TAG = ThresholdSelectorFragment.class.getName();
    private static final String NUMBER_CUSTODIANS = "numberCustodians";

    protected ThresholdDefinedListener listener;

    // TODO this should be the actual number of custodians
    private int numberOfCustodians;
    private SeekBar seekBar;
    private TextView thresholdRepresentation;
    private TextView message;

    public static ThresholdSelectorFragment newInstance(int numberCustodians) {
        Bundle bundle = new Bundle();
        bundle.putInt(NUMBER_CUSTODIANS, numberCustodians);
        ThresholdSelectorFragment fragment = new ThresholdSelectorFragment();
        fragment.setArguments(bundle);
//        fragment.setNumberCustodians(numberCustodians);
        return fragment;
    }

    private void setNumberCustodians(int numberCustodians) {
        numberOfCustodians = numberCustodians;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requireActivity().setTitle(R.string.title_define_threshold);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_select_threshold,
                container, false);
//        if (savedInstanceState != null) {
//            numberOfCustodians = savedInstanceState.getInt("numberCustodians");
//        }
	    Bundle args = requireArguments();
	    numberOfCustodians = args.getInt(NUMBER_CUSTODIANS);

        seekBar = view.findViewById(R.id.seekBar);
        thresholdRepresentation = view.findViewById(R.id.textViewThresholdRepresentation);
        message = view.findViewById(R.id.textViewMessage);
        int max = numberOfCustodians - 3;
        seekBar.setMax(max);
        seekBar.setOnSeekBarChangeListener(new SeekBarListener());
        int defaultThreshold = SecretSharingWrapper.defaultThreshold(numberOfCustodians);
        seekBar.setProgress(defaultThreshold - 2);

        thresholdRepresentation.setText(buildThresholdRepresentationString(defaultThreshold));
        return view;
//        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        listener = (ThresholdDefinedListener) context;
    }


    @Override
    public String getUniqueTag() {
        return TAG;
    }

    @Override
    public void injectFragment(ActivityComponent component) {
        component.inject(this);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.define_threshold_actions, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_threshold_defined:
                listener.thresholdDefined();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private String buildThresholdRepresentationString (int threshold) {
        String thresholdRepresentationText = "";
        for (int i = 0; i < threshold; i++) {
            thresholdRepresentationText += getString(R.string.filled_bullet);
//            thresholdRepresentationText += "1 ";
        }
        for (int i = 0; i < (numberOfCustodians - threshold); i++) {
            thresholdRepresentationText += getString(R.string.linear_bullet);
//            thresholdRepresentationText += "0 ";
        }
        return thresholdRepresentationText;
    }

    private class SeekBarListener implements SeekBar.OnSeekBarChangeListener {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress,
                boolean fromUser) {
	        int threshold = progress + 2;

            thresholdRepresentation.setText(
                    buildThresholdRepresentationString(threshold)
            );

            int sanityLevel = SecretSharingWrapper.thresholdSanity(threshold, numberOfCustodians);
            int text = R.string.threshold_secure;
            // TODO use 1 / -1 instead of 0
            if (sanityLevel < 0) text = R.string.threshold_low_insecure;
            if (sanityLevel > 0) text = R.string.threshold_high_insecure;
            message.setText(text);
            // TODO change colour of thresholdRepresentation to green/red based on sanityLevel
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // do nothing
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // do nothing
        }

    }

}
