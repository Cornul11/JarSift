package nl.tudelft.cornul11.thesis.api;

import okhttp3.*;

import java.io.IOException;

public class ApiClient {
    private final OkHttpClient client;
//    private final String apiKey;
    private final String baseUrl = "https://services.nvd.nist.gov/rest/json/cpes/2.0";
    private final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public ApiClient(/*String apiKey*/) {
        this.client = new OkHttpClient();
//        this.apiKey = apiKey;
    }

    public String makePostRequest(String url, String jsonData) throws IOException {
        RequestBody body = RequestBody.create(jsonData, JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
//                .addHeader("apiKey", apiKey)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            return response.body().string();
        }
    }
}
