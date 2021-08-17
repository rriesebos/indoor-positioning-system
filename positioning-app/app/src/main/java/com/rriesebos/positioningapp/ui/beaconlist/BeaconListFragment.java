package com.rriesebos.positioningapp.ui.beaconlist;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.rriesebos.positioningapp.ui.BeaconsViewModel;
import com.rriesebos.positioningapp.R;
import com.rriesebos.positioningapp.ui.SensorStatusViewModel;

import java.util.Objects;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link BeaconListFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class BeaconListFragment extends Fragment {

    private static final String LOG_TAG = BeaconListFragment.class.getSimpleName();

    private RecyclerView mRecyclerView;
    private TextView emptyListTextView;

    private boolean mShowWarning = false;

    private BeaconListAdapter mBeaconListAdapter;
    private SensorStatusViewModel mSensorStatusViewModel;
    private BeaconsViewModel mBeaconsViewModel;

    public BeaconListFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment BeaconListFragment.
     */
    public static BeaconListFragment newInstance() {
        return new BeaconListFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_beacon_list, container, false);

        mRecyclerView = view.findViewById(R.id.beacon_list);
        emptyListTextView = view.findViewById(R.id.empty_list_text);

        mRecyclerView.addItemDecoration(new DividerItemDecoration(mRecyclerView.getContext(), DividerItemDecoration.VERTICAL));

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getActivity());
        mRecyclerView.setLayoutManager(layoutManager);

        // Disable item change animations
        ((SimpleItemAnimator) Objects.requireNonNull(mRecyclerView.getItemAnimator())).setSupportsChangeAnimations(false);

        mBeaconListAdapter = new BeaconListAdapter(requireContext());
        mRecyclerView.setAdapter(mBeaconListAdapter);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mSensorStatusViewModel = new ViewModelProvider(requireActivity()).get(SensorStatusViewModel.class);
        mBeaconsViewModel = new ViewModelProvider(requireActivity()).get(BeaconsViewModel.class);

        mSensorStatusViewModel.isBluetoothEnabled().observe(getViewLifecycleOwner(), isBluetoothEnabled -> {
            updateWarningText();
        });
        mSensorStatusViewModel.isLocationEnabled().observe(getViewLifecycleOwner(), isLocationEnabled -> {
            updateWarningText();
        });

        mBeaconsViewModel.getBeaconList().observe(getViewLifecycleOwner(), beaconList -> {
            if (mShowWarning) {
                return;
            }

            mBeaconListAdapter.updateBeaconList(beaconList);

            if (mBeaconListAdapter.isEmpty()) {
                mBeaconListAdapter.clear();
                emptyListTextView.setVisibility(View.VISIBLE);
            } else {
                emptyListTextView.setVisibility(View.GONE);
            }
        });
    }

    private void updateWarningText() {
        Boolean isBluetoothEnabled = mSensorStatusViewModel.isBluetoothEnabled().getValue();
        Boolean isLocationEnabled = mSensorStatusViewModel.isLocationEnabled().getValue();

        if (isBluetoothEnabled == null) {
            isBluetoothEnabled = false;
        }

        if (isLocationEnabled == null) {
            isLocationEnabled = false;
        }

        mShowWarning = !(isBluetoothEnabled && isLocationEnabled);

        // Bluetooth and location are enabled
        if (isBluetoothEnabled && isLocationEnabled) {
            emptyListTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.secondary_text_material_light));
            emptyListTextView.setText(R.string.empty_beacon_list);
            return;
        }

        // Clear list and show warning text
        mBeaconListAdapter.clear();
        emptyListTextView.setVisibility(View.VISIBLE);

        // Set warning text color to red
        emptyListTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.material_red_500));

        // Bluetooth is disabled, location is enabled
        if (!isBluetoothEnabled && isLocationEnabled) {
            emptyListTextView.setText(R.string.message_bluetooth_required);
            return;
        }

        // Bluetooth is enabled, location is disabled
        if (isBluetoothEnabled) {
            emptyListTextView.setText(R.string.message_location_required);
            return;
        }

        // Bluetooth and location are disabled
        emptyListTextView.setText(R.string.message_bluetooth_location_required);
    }
}