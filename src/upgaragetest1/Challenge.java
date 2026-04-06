package upgaragetest1;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.json.JSONObject;

public class Challenge {

    static final String BASE_URL = "http://challenge.z2o.cloud";
    static final String NICKNAME = "HAL_Marbun";

    static final HttpClient client = HttpClient.newHttpClient();

    static long offset = 0;

    public static void main(String[] args) throws Exception {

        // 🔥 1x sync (simple)
        syncTime();

        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/challenges?nickname=" + NICKNAME))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> postResponse =
                client.send(postRequest, HttpResponse.BodyHandlers.ofString());

        JSONObject data = new JSONObject(postResponse.body());

        int attempt = 0;

        while (true) {

            String id = data.getString("id");
            long activesAt = data.getLong("actives_at");

            long now = now();
            long waitTime = activesAt - now - 10;

            if (waitTime > 0) {
                Thread.sleep(waitTime);
            }

            while (now() < activesAt) {
                // spin wait ringan
            }

            long diff = now() - activesAt;
            System.out.println("Attempt " + (++attempt) + " diff: " + diff + " ms");

            HttpRequest putRequest = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/challenges"))
                    .header("X-Challenge-Id", id)
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> putResponse =
                    client.send(putRequest, HttpResponse.BodyHandlers.ofString());

            data = new JSONObject(putResponse.body());

            if (data.has("result")) {
                JSONObject result = data.getJSONObject("result");

                System.out.println("Total attempts: " + result.getInt("attempts"));

                if (!result.isNull("url")) {
                    System.out.println("SUCCESS: " + BASE_URL + result.getString("url"));
                } else {
                    System.out.println("FAILED");
                }

                break;
            }

            System.out.println("Total diff: " + data.getLong("total_diff") + " ms");
        }
    }

    // 🔥 simple sync (1x aja)
    static void syncTime() throws Exception {
        long before = System.currentTimeMillis();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/challenges?nickname=sync"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString());

        long after = System.currentTimeMillis();

        JSONObject data = new JSONObject(resp.body());
        long serverTime = data.getLong("called_at");

        long estimate = before + (after - before) / 2;

        offset = serverTime - estimate;

        System.out.println("Offset: " + offset + " ms");
    }

    static long now() {
        return System.currentTimeMillis() + offset;
    }
}