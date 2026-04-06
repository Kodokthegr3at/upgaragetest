package upgaragetest1;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.json.JSONObject;

public class Challenge {

    // ベースURL（APIエンドポイント）
    static final String BASE_URL = "http://challenge.z2o.cloud";

    // ニックネーム（チャレンジ識別用）
    static final String NICKNAME = "HAL_Marbun";

    // HTTPクライアント（Java標準）
    static final HttpClient client = HttpClient.newHttpClient();

    // サーバー時刻との差分（オフセット）
    static long offset = 0;

    public static void main(String[] args) throws Exception {

        // 初回のみ時刻同期を実施
        syncTime();

        // チャレンジ開始リクエスト（POST）
        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/challenges?nickname=" + NICKNAME))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> postResponse =
                client.send(postRequest, HttpResponse.BodyHandlers.ofString());

        // レスポンスJSONを解析
        JSONObject data = new JSONObject(postResponse.body());

        int attempt = 0;

        // メインループ（成功するまで繰り返し）
        while (true) {

            // チャレンジID取得
            String id = data.getString("id");

            // 有効開始時刻（サーバー基準）
            long activesAt = data.getLong("actives_at");

            long now = now();

            // 実行タイミングまでの待機時間を計算（少し早めに調整）
            long waitTime = activesAt - now - 10;

            // スリープで大まかに待機（CPU負荷軽減）
            if (waitTime > 0) {
                Thread.sleep(waitTime);
            }

            // ミリ秒単位での精密待機（スピンウェイト）
            while (now() < activesAt) {
                // 軽いループでタイミング調整
            }

            // 実際のズレを計測
            long diff = now() - activesAt;
            System.out.println("Attempt " + (++attempt) + " diff: " + diff + " ms");

            // PUTリクエスト送信（タイミング勝負）
            HttpRequest putRequest = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/challenges"))
                    .header("X-Challenge-Id", id)
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> putResponse =
                    client.send(putRequest, HttpResponse.BodyHandlers.ofString());

            // レスポンス更新
            data = new JSONObject(putResponse.body());

            // 結果が含まれる場合（終了条件）
            if (data.has("result")) {
                JSONObject result = data.getJSONObject("result");

                System.out.println("Total attempts: " + result.getInt("attempts"));

                // 成功時URL表示
                if (!result.isNull("url")) {
                    System.out.println("SUCCESS: " + BASE_URL + result.getString("url"));
                } else {
                    System.out.println("FAILED");
                }

                break;
            }

            // 現在までの累積誤差
            System.out.println("Total diff: " + data.getLong("total_diff") + " ms");
        }
    }

    // サーバー時刻との同期処理（1回のみ）
    static void syncTime() throws Exception {

        // リクエスト送信前のローカル時刻
        long before = System.currentTimeMillis();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/challenges?nickname=sync"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString());

        // レスポンス受信後のローカル時刻
        long after = System.currentTimeMillis();

        JSONObject data = new JSONObject(resp.body());

        // サーバー側の呼び出し時刻
        long serverTime = data.getLong("called_at");

        // RTT（往復時間）を考慮した推定時刻
        long estimate = before + (after - before) / 2;

        // オフセット計算（サーバーとの差分）
        offset = serverTime - estimate;

        System.out.println("Offset: " + offset + " ms");
    }

    // 補正済み現在時刻を取得
    static long now() {
        return System.currentTimeMillis() + offset;
    }
}
