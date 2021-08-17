package com.rriesebos.positioningapp.api;

import com.rriesebos.positioningapp.model.BeaconInformation;
import com.rriesebos.positioningapp.model.BeaconMeasurement;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface BeaconApi {

    @GET("beacons")
    Call<List<BeaconInformation>> getBeacons();

    @GET("beacons/{beaconAddress}")
    Call<ResponseBody> getBeacon(@Path("beaconAddress") String beaconAddress);

    @GET("beacons/{beaconAddress}/rssi")
    Call<ResponseBody> getBeaconMeasurements(@Path("beaconAddress") String beaconAddress);

    @POST("beacons/{beaconAddress}/rssi")
    Call<ResponseBody> postBeaconMeasurements(
            @Path("beaconAddress") String beaconAddress,
            @Body BeaconMeasurement beaconMeasurement
    );

    @DELETE("beacons/rssi")
    Call<ResponseBody> deleteAllBeaconMeasurements();
}
