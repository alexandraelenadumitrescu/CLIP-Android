package com.photomatch.api;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface ApiService {

    @GET("health")
    Call<Map<String, Object>> health();

    @Multipart
    @POST("process")
    Call<ProcessResponse> process(@Part MultipartBody.Part image);

    @Multipart
    @POST("style/upload")
    Call<StyleUploadResponse> styleUpload(@Part List<MultipartBody.Part> images);

    @Multipart
    @POST("style/process")
    Call<ProcessResponse> styleProcess(
        @Part MultipartBody.Part image,
        @Part("session_id") RequestBody sessionId
    );

    @Multipart
    @POST("batch/process")
    Call<BatchResponse> batchProcess(@Part List<MultipartBody.Part> files);

    @POST("cluster")
    Call<ClusterResponse> cluster(@Body ClusterRequest request);
}
