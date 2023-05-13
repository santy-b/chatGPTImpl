package com.grpc;

import com.google.protobuf.ByteString;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ApiServiceImpl extends ApiService {
    private final ConnectionPool connectionPool = new ConnectionPool(10, 30, TimeUnit.SECONDS);
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectionPool(connectionPool)
            .build();

    public void sendRequest(RpcController controller, ApiRequest request, RpcCallback<ApiResponse> done) {
        Request httpRequest = new Request.Builder()
                .url(request.getUrl())
                .header("Authorization", "Bearer " + request.getApiKey())
                .post(RequestBody.create(request.getRequestBody(), MediaType.parse("application/json")))
                .build();

        Call httpCall = client.newCall(httpRequest);
        httpCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                controller.setFailed(e.getMessage());
                done.run(null);
            }

            @Override
            public void onResponse(Call call, Response httpResponse) throws IOException {
                try (Response response = httpResponse) {
                    byte[] responseData = response.body().bytes();
                    Map<String, String> responseHeaders = response.headers().toMultimap().entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, entry -> String.join(",", entry.getValue())));

                    String result = new JSONObject(new String(responseData)).toString();

                    ApiResponse.Builder responseBuilder = ApiResponse.newBuilder()
                            .setData(ByteString.copyFrom(result.getBytes()))
                            .putAllResponseHeaders(responseHeaders);

                    done.run(responseBuilder.build());

                } catch (JSONException e) {
                    controller.setFailed("Error parsing JSON response");
                    done.run(null);
                } catch (IOException e) {
                    controller.setFailed(e.getMessage());
                    done.run(null);
                }
            }
        });
    }
}
