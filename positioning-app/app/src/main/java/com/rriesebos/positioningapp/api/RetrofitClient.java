package com.rriesebos.positioningapp.api;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static Retrofit retrofitInstance;
    private static final String BASE_URL = "http://192.168.178.22:3000/api/";

    public static Retrofit getInstance() {
        if (retrofitInstance == null) {
            retrofitInstance = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }

        return retrofitInstance;
    }

    public static BeaconApi getBeaconApi() {
        return getInstance().create(BeaconApi.class);
    }

    public static PositioningApi getPositioningApi() {
        return getInstance().create(PositioningApi.class);
    }
}
