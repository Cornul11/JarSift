package nl.tudelft.cornul11.thesis.corpus.api;

import okhttp3.*;

import java.io.IOException;

public class PostRequestClient {
    private final OkHttpClient client;
    private final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public PostRequestClient() {
        this.client = new OkHttpClient();
    }

    public String makePostRequest(String url, String jsonData) throws IOException {
        RequestBody body = RequestBody.create(jsonData, JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            return response.body().string();
        }
    }
}
