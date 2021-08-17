package com.rriesebos.positioningapp.api;

import com.rriesebos.positioningapp.model.Coordinates;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface PositioningApi {

    @GET("positioning")
    Call<List<Coordinates>> getPredictedCoordinates();

    @FormUrlEncoded
    @POST("positioning")
    Call<ResponseBody> postPredictedCoordinates(
            @Field("timestamp") long timestamp,
            @Field("x") double x,
            @Field("y") double y,
            @Field("confidence") double confidence
    );

    @FormUrlEncoded
    @POST("positioning/checkpoints")
    Call<ResponseBody> postCheckpointTimestamp(
            @Field("timestamp") long timestamp,
            @Field("checkpoint") int checkpoint
    );

    @DELETE("positioning")
    Call<ResponseBody> deleteAllPredictedCoordinates();

    @DELETE("positioning/checkpoints")
    Call<ResponseBody> deleteAllCheckpointTimestamps();
}
