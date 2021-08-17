package com.rriesebos.positioningapp.ui.positioning;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.transition.Slide;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.rriesebos.positioningapp.api.PositioningApi;
import com.rriesebos.positioningapp.api.RetrofitClient;
import com.rriesebos.positioningapp.positioning.PositionProvider;
import com.rriesebos.positioningapp.positioning.PositioningException;
import com.rriesebos.positioningapp.positioning.PositioningMethod;
import com.rriesebos.positioningapp.ui.BeaconsViewModel;
import com.rriesebos.positioningapp.R;
import com.rriesebos.positioningapp.model.Coordinates;
import com.rriesebos.positioningapp.ui.RecordingViewModel;

import org.altbeacon.beacon.Beacon;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link PositioningFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class PositioningFragment extends Fragment {

    private static final String LOG_TAG = PositioningFragment.class.getSimpleName();

    private ViewGroup positioningRootView;

    private TextView positioningErrorTextView;
    private LinearLayout trilaterationWrapper;
    private LinearLayout weightedCentroidsWrapper;
    private LinearLayout probabilityWrapper;

    private TextView positionTextViewTrilateration;
    private TextView positionTextViewWeightedCentroids;
    private TextView positionTextViewProbability;

    private LinearLayout checkpointWrapper;
    private MaterialButton checkpointButton;
    private TextView checkpointCountTextView;

    private BeaconsViewModel mBeaconsViewModel;
    private RecordingViewModel mRecordingViewModel;
    private PositionProvider mPositionProvider;

    private final PositioningApi mPositioningApi = RetrofitClient.getPositioningApi();

    public PositioningFragment() {
        mPositionProvider = PositionProvider.getInstance(getContext());
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment PositionFragment.
     */
    public static PositioningFragment newInstance() {
        return new PositioningFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_positioning, container, false);

        positioningRootView = view.findViewById(R.id.positioning_root_view);

        positioningErrorTextView = view.findViewById(R.id.positioning_error_text);
        trilaterationWrapper = view.findViewById(R.id.trilateration_wrapper);
        weightedCentroidsWrapper = view.findViewById(R.id.weighted_centroids_wrapper);
        probabilityWrapper = view.findViewById(R.id.probability_wrapper);

        positionTextViewTrilateration = view.findViewById(R.id.position_text_trilateration);
        positionTextViewWeightedCentroids = view.findViewById(R.id.position_text_weighted_centroids);
        positionTextViewProbability = view.findViewById(R.id.position_text_probability);

        checkpointWrapper = view.findViewById(R.id.checkpoint_wrapper);
        checkpointButton = view.findViewById(R.id.checkpoint_button);
        checkpointCountTextView = view.findViewById(R.id.checkpoint_count);

        checkpointButton.setOnClickListener(buttonView -> {
            int checkpoint = mRecordingViewModel.incrementCheckpoint();
            sendCheckpointTimestamp(checkpoint);
        });

        return view;
    }

    private void sendCheckpointTimestamp(int checkpoint) {
        Log.d(LOG_TAG, "Sending timestamp for checkpoint " + checkpoint + " to server.");

        // Send checkpoint timestamp to backend
        long timestamp = System.currentTimeMillis();
        final Call<ResponseBody> call = mPositioningApi.postCheckpointTimestamp(
                timestamp,
                checkpoint
        );

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                // Successfully send measurement to server
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(LOG_TAG, "Failed to POST checkpoint timestamp: " + t.getLocalizedMessage());
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mBeaconsViewModel = new ViewModelProvider(requireActivity()).get(BeaconsViewModel.class);
        mRecordingViewModel = new ViewModelProvider(requireActivity()).get(RecordingViewModel.class);

        mBeaconsViewModel.getBeaconList().observe(getViewLifecycleOwner(), this::updatePosition);

        mRecordingViewModel.isRecording().observe(getViewLifecycleOwner(), isRecording -> {
            Transition transition = new Slide(Gravity.BOTTOM);
            transition.setDuration(getResources().getInteger(android.R.integer.config_shortAnimTime));
            transition.addTarget(R.id.checkpoint_wrapper);

            TransitionManager.beginDelayedTransition(positioningRootView, transition);
            checkpointWrapper.setVisibility(isRecording ? View.VISIBLE : View.GONE);

            if (isRecording) {
                // Send starting checkpoint to server
                sendCheckpointTimestamp(0);
            }
        });

        mRecordingViewModel.getCheckpoint().observe(getViewLifecycleOwner(), checkpoint -> {
            checkpointCountTextView.setText(String.valueOf(checkpoint));
        });
    }

    private void updatePosition(List<Beacon> beaconList) {
        try {
            positioningErrorTextView.setVisibility(View.GONE);

            Coordinates coordinatesTrilateration = mPositionProvider.getPosition(beaconList, PositioningMethod.TRILATERATION);
            Coordinates coordinatesWeightedCentroid = mPositionProvider.getPosition(beaconList, PositioningMethod.WEIGHTED_CENTROID);
            Coordinates coordinatesProbability = mPositionProvider.getPosition(beaconList, PositioningMethod.PROBABILITY);

            if (coordinatesTrilateration != null) {
                trilaterationWrapper.setVisibility(View.VISIBLE);
                positionTextViewTrilateration.setText(getString(R.string.coordinates, coordinatesTrilateration.getX(), coordinatesTrilateration.getY()));
            }

            if (coordinatesWeightedCentroid != null) {
                weightedCentroidsWrapper.setVisibility(View.VISIBLE);
                positionTextViewWeightedCentroids.setText(getString(R.string.coordinates, coordinatesWeightedCentroid.getX(), coordinatesWeightedCentroid.getY()));
            }

            if (coordinatesProbability != null) {
                probabilityWrapper.setVisibility(View.VISIBLE);
                positionTextViewProbability.setText(getString(R.string.coordinates, coordinatesProbability.getX(), coordinatesProbability.getY()));
            }
        } catch (PositioningException positioningException) {
            Log.e(LOG_TAG, "PositioningException: " + positioningException.getLocalizedMessage());

            trilaterationWrapper.setVisibility(View.GONE);
            weightedCentroidsWrapper.setVisibility(View.GONE);
            probabilityWrapper.setVisibility(View.GONE);

            positioningErrorTextView.setVisibility(View.VISIBLE);
            positioningErrorTextView.setText(positioningException.getLocalizedMessage());
        }
    }
}