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

import static java.util.Objects.requireNonNull;

public class ThresholdSelectorFragment extends BaseFragment {

    public static final String TAG = ThresholdSelectorFragment.class.getName();

    protected ThresholdDefinedListener listener;

    private SeekBar seekBar;
    private ImageView image;
    private TextView message;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requireNonNull(getActivity()).setTitle(R.string.title_define_threshold);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_select_threshold,
                container, false);

        seekBar = view.findViewById(R.id.seekBar);
        image = view.findViewById(R.id.imageView);
        message = view.findViewById(R.id.textViewMessage);

        seekBar.setMax(2);
        seekBar.setOnSeekBarChangeListener(new SeekBarListener());
        seekBar.setProgress(1);

        return view;
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

    private class SeekBarListener implements SeekBar.OnSeekBarChangeListener {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress,
                boolean fromUser) {
            // progress can be 0, 1, 2
            int drawable = R.drawable.ic_pie_2_of_5;
            switch (progress) {
                case 1:
                    drawable = R.drawable.ic_pie_3_of_5;
                    break;
                case 2:
                    drawable = R.drawable.ic_pie_4_of_5;
                    break;
            }
            int text = progress < 1 ? R.string.threshold_insecure : R.string.threshold_secure;
            image.setImageDrawable(getContext().getResources().getDrawable(drawable));
            message.setText(text);
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
