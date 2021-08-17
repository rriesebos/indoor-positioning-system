package com.rriesebos.positioningapp.ui.beaconlist;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.rriesebos.positioningapp.R;
import com.rriesebos.positioningapp.positioning.DistanceProvider;

import org.altbeacon.beacon.Beacon;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public class BeaconListAdapter extends RecyclerView.Adapter<BeaconListAdapter.ViewHolder> {

    private static final String LOG_TAG = BeaconListAdapter.class.getSimpleName();

    private static final int VIEW_TYPE_EMPTY = 0;
    private static final int VIEW_TYPE_NOT_EMPTY = 1;

    // Maximum number of consecutive scans that do not find the beacon before marking it as out of range
    private static final int GRACE_PERIOD = 2;
    // Maximum number of consecutive scans that do not find the beacon before removing it
    private static final int REMOVE_GRACE_PERIOD = 6;
    private static final boolean SORT_LIST_BY_ADDRESS = true;

    private RecyclerView mRecyclerView;
    private final Context mContext;

    private final List<Beacon> mBeaconList = new ArrayList<>();
    private final Map<String, Integer> mGraceCounterMap = new HashMap<>();

    private DistanceProvider mDistanceProvider;

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final View mView;

        private final TextView bluetoothNameTextView;
        private final TextView bluetoothAddressTextView;
        private final TextView uuidTextView;
        private final TextView majorTextView;
        private final TextView minorTextView;
        private final TextView rssiTextView;
        private final TextView txPowerTextView;
        private final TextView distanceTextView;
        private final TextView distanceCustomTextView;

        private int mTextColor;

        public ViewHolder(View view) {
            super(view);

            mView = view;

            bluetoothNameTextView = view.findViewById(R.id.bluetooth_name);
            bluetoothAddressTextView = view.findViewById(R.id.bluetooth_address);
            uuidTextView = view.findViewById(R.id.uuid);
            majorTextView = view.findViewById(R.id.major);
            minorTextView = view.findViewById(R.id.minor);
            rssiTextView = view.findViewById(R.id.rssi);
            txPowerTextView = view.findViewById(R.id.tx_power);
            distanceTextView = view.findViewById(R.id.distance);
            distanceCustomTextView = view.findViewById(R.id.distance_custom);
        }

        public TextView getBluetoothNameTextView() {
            return bluetoothNameTextView;
        }

        public TextView getBluetoothAddressTextView() {
            return bluetoothAddressTextView;
        }

        public TextView getUuidTextView() {
            return uuidTextView;
        }

        public TextView getMajorTextView() {
            return majorTextView;
        }

        public TextView getMinorTextView() {
            return minorTextView;
        }

        public TextView getRssiTextView() {
            return rssiTextView;
        }

        public TextView getTxPowerTextView() {
            return txPowerTextView;
        }

        public TextView getDistanceTextView() {
            return distanceTextView;
        }

        public TextView getDistanceCustomTextView() {
            return distanceCustomTextView;
        }

        private void setTextColor(int color) {
            mTextColor = color;
            setTextColorUtil(color, (ViewGroup) mView);
        }

        private int getTextColor() {
            return mTextColor;
        }

        private void setTextColorUtil(int color, ViewGroup viewGroup) {
            // Loop over all the children and their children recursively
            // If a TextView is encountered, set the text color to the passed color
            for (int i = 0; i < viewGroup.getChildCount(); i++){
                View view = viewGroup.getChildAt(i);

                if (view instanceof TextView) {
                    ((TextView) view).setTextColor(color);
                } else if (view instanceof LinearLayout) {
                    setTextColorUtil(color, (ViewGroup) view);
                }
            }
        }
    }

    public BeaconListAdapter(Context context) {
        mContext = context;
        mDistanceProvider = DistanceProvider.getInstance(context);
    }

    @Override
    public int getItemViewType(int position) {
        if (mBeaconList.isEmpty()) {
            return VIEW_TYPE_EMPTY;
        }

        return VIEW_TYPE_NOT_EMPTY;
    }

    @Override
    public void onAttachedToRecyclerView(@NotNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);

        mRecyclerView = recyclerView;
    }

    @NotNull
    @Override
    public ViewHolder onCreateViewHolder(@NotNull ViewGroup viewGroup, int viewType) {
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.beacon_list_item, viewGroup, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NotNull ViewHolder viewHolder, final int position) {
        if (getItemViewType(position) == VIEW_TYPE_EMPTY) {
            return;
        }

        Beacon beacon = mBeaconList.get(position);

        TextView bluetoothNameTextView = viewHolder.getBluetoothNameTextView();
        TextView bluetoothAddressTextView = viewHolder.getBluetoothAddressTextView();
        TextView uuidTextView = viewHolder.getUuidTextView();
        TextView majorTextView = viewHolder.getMajorTextView();
        TextView minorTextView = viewHolder.getMinorTextView();
        TextView rssiTextView = viewHolder.getRssiTextView();
        TextView txPowerTextView = viewHolder.getTxPowerTextView();
        TextView distanceTextView = viewHolder.getDistanceTextView();
        TextView distanceCustomTextView = viewHolder.getDistanceCustomTextView();

        bluetoothNameTextView.setText(beacon.getBluetoothName());
        bluetoothAddressTextView.setText(beacon.getBluetoothAddress());
        uuidTextView.setText(beacon.getId1().toString());
        majorTextView.setText(beacon.getId2().toString());
        minorTextView.setText(beacon.getId3().toString());
        rssiTextView.setText(String.valueOf(beacon.getRssi()));
        txPowerTextView.setText(String.valueOf(beacon.getTxPower()));
        distanceTextView.setText(String.valueOf(beacon.getDistance()));
        distanceCustomTextView.setText(String.valueOf(mDistanceProvider.getDistance(beacon)));

        if (mGraceCounterMap.containsKey(beacon.getBluetoothAddress())
                && mGraceCounterMap.get(beacon.getBluetoothAddress()) >= GRACE_PERIOD) {
            viewHolder.setTextColor(Color.RED);
        } else if (viewHolder.getTextColor() == Color.RED) {
            viewHolder.setTextColor(Color.BLACK);
        }
    }

    @Override
    public int getItemCount() {
        return mBeaconList.size();
    }

    public boolean isEmpty() {
        return mBeaconList.isEmpty();
    }

    public void clear() {
        if (isEmpty()) {
            return;
        }

        mBeaconList.clear();
        notifyDataSetChanged();
    }

    public void updateBeaconList(List<Beacon> beaconList) {
        if (SORT_LIST_BY_ADDRESS) {
            // Sort the list by bluetooth (MAC) address
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mBeaconList.sort(Comparator.comparing(Beacon::getBluetoothAddress));
            } else {
                Collections.sort(mBeaconList, (beacon, beacon2) ->
                        beacon.getBluetoothAddress().compareTo(beacon2.getBluetoothAddress()));
            }

            for (Beacon newBeacon : beaconList) {
                insertIntoSorted(newBeacon);
            }
        } else {
            for (Beacon newBeacon : beaconList) {
                insert(newBeacon);
            }
        }

        ListIterator<Beacon> it = mBeaconList.listIterator();
        while (it.hasNext()) {
            int i = it.nextIndex();
            Beacon beacon = it.next();

            // If the beacon is not detected in the last scan, increment the grace counter
            if (beaconList.isEmpty() || !beaconList.contains(beacon)) {
                int newGraceCounter = 0;
                if (mGraceCounterMap.containsKey(beacon.getBluetoothAddress())) {
                    newGraceCounter = mGraceCounterMap.get(beacon.getBluetoothAddress()) + 1;
                }
                mGraceCounterMap.put(beacon.getBluetoothAddress(), newGraceCounter);

                // Remove the beacon from the list if it is not seen for longer than the grace period
                if (newGraceCounter >= REMOVE_GRACE_PERIOD) {
                    mGraceCounterMap.remove(beacon.getBluetoothAddress());
                    it.remove();
                    notifyItemRemoved(i);
                } else {
                    notifyItemChanged(i);
                }
            }
        }
    }

    private void insert(Beacon newBeacon) {
        // Reset the grace counter to zero for all detected beacons
        mGraceCounterMap.put(newBeacon.getBluetoothAddress(), 0);

        int index = mBeaconList.indexOf(newBeacon);
        if (index == -1) {
            // Add newly detected beacons
            mBeaconList.add(0, newBeacon);
            notifyItemInserted(0);
        } else {
            // Update beacons already in the list
            mBeaconList.set(index, newBeacon);
            notifyItemChanged(index);
        }
    }

    private void insertIntoSorted(Beacon newBeacon) {
        // Reset the grace counter to zero for all detected beacons
        mGraceCounterMap.put(newBeacon.getBluetoothAddress(), 0);

        if (mBeaconList.isEmpty()) {
            mBeaconList.add(newBeacon);
            notifyItemInserted(0);
            return;
        }

        for (int i = 0; i < mBeaconList.size(); i++) {
            Beacon beacon = mBeaconList.get(i);

            String beaconAddress = beacon.getBluetoothAddress();
            String newBeaconAddress = newBeacon.getBluetoothAddress();

            int comparison = beaconAddress.compareTo(newBeaconAddress);
            if (comparison == 0) {
                // beaconAddress == newBeaconAddress
                // Update beacons already in the list
                mBeaconList.set(i, newBeacon);
                notifyItemChanged(i);
                break;
            }

            if (comparison > 0) {
                // beaconAddress > newBeaconAddress
                // Add newly detected beacons
                mBeaconList.add(i, newBeacon);
                notifyItemInserted(i);
                break;
            }

            if (i == mBeaconList.size() - 1) {
                // Add newly detected beacons to end of list
                mBeaconList.add(newBeacon);
                notifyItemInserted(i + 1);
                break;
            }
        }
    }
}
