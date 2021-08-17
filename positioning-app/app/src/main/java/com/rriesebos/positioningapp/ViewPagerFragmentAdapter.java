package com.rriesebos.positioningapp;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.rriesebos.positioningapp.ui.positioning.PositioningFragment;
import com.rriesebos.positioningapp.ui.beaconlist.BeaconListFragment;

public class ViewPagerFragmentAdapter extends FragmentStateAdapter {

    private static final int itemCount = 2;

    public ViewPagerFragmentAdapter(FragmentManager fragmentManager, Lifecycle lifecycle) {
        super(fragmentManager, lifecycle);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return BeaconListFragment.newInstance();
            case 1:
                return PositioningFragment.newInstance();
            default:
                return null;
        }
    }

    @Override
    public int getItemCount() {
        return itemCount;
    }
}
