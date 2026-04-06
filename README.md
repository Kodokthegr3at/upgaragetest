# 回答説明

> **作成者:** HAL_Marbun  
> **使用言語:** Java  
> **対象エンドポイント:** http://challenge.z2o.cloud/

---

## 目次

1. [このチャレンジとは？](#1-このチャレンジとは)
2. [解くべき問題](#2-解くべき問題)
3. [処理の流れ](#3-処理の流れ)
4. [APIの説明](#4-apiの説明)
5. [コードの説明](#5-コードの説明)
6. [技術的な工夫](#6-技術的な工夫)
7. [実行方法](#7-実行方法)
8. [まとめ](#8-まとめ)

---

## 1. このチャレンジとは？

これは **タイミングゲーム** です。

サーバーが「この時刻に叩いてね」と指定した時間に、正確にHTTPリクエストを送り続けるプログラムを作ります。  
遅れが積み重なって **合計500msを超えると終了**（失敗）。規定回数クリアできると **成功URL** がもらえます。

> 💡 **わかりやすく言うと...**  
> サーバーが「せーの！」と言った瞬間にボタンを押し続けるゲームです。  
> ズレが積み重なりすぎるとゲームオーバー。

---

## 2. 解くべき問題

| 問題 | 内容 |
|------|------|
| ⏱️ 時刻のズレ | 自分のパソコンの時計とサーバーの時計は微妙に違う |
| 😴 `Thread.sleep`の不精度 | OSのスリープはミリ秒単位で正確ではない |
| 🌐 通信の遅延 | HTTPリクエストの往復時間がある |
| 🔁 繰り返しの正確性 | 何百回も正確なタイミングを維持し続ける必要がある |

---

## 3. 処理の流れ

```
① POST /challenges?nickname=HAL_Marbun
        ↓
   サーバーから id と actives_at を受け取る
        ↓
② actives_at の時刻になるまで待つ
        ↓
③ PUT /challenges  (ヘッダに X-Challenge-Id: [id] を付ける)
        ↓
   サーバーから次の actives_at を受け取る
        ↓
④ ②〜③を繰り返す
        ↓
⑤ total_diff > 500ms になったら終了
   → result.url があれば成功！
```

---

## 4. APIの説明

### エンドポイント

**ベースURL:** `http://challenge.z2o.cloud`

---

### `POST /challenges` — チャレンジ開始

| 種別 | キー | 内容 |
|------|------|------|
| クエリ | `nickname` | ランキング用の名前（1〜16文字） |

```bash
curl -X POST "http://challenge.z2o.cloud/challenges?nickname=HAL_Marbun"
```

---

### `PUT /challenges` — 叩く（タイミング送信）

| 種別 | キー | 内容 |
|------|------|------|
| ヘッダ | `X-Challenge-Id` | POSTで受け取ったチャレンジID |

```bash
curl -X PUT -H "X-Challenge-Id: xxxxxxxxxx" "http://challenge.z2o.cloud/challenges"
```

---

### レスポンスの形式（JSON）

**チャレンジ継続中:**

```json
{
  "id": "xxxxxxxxxx",
  "actives_at": 1579938264219,
  "called_at":  1579938263720,
  "total_diff": 0
}
```

| フィールド | 意味 |
|-----------|------|
| `id` | チャレンジID（PUTリクエストで必要） |
| `actives_at` | 次に叩くべき時刻（UNIXミリ秒） |
| `called_at` | サーバーがリクエストを受け取った時刻 |
| `total_diff` | これまでの遅延の合計（ミリ秒） |

**チャレンジ終了時（成功）:**

```json
{
  "result": {
    "attempts": 100,
    "url": "/xxxxxxxx"
  }
}
```

**チャレンジ終了時（失敗）:**

```json
{
  "result": {
    "attempts": 3,
    "url": null
  }
}
```

---

## 5. コードの説明

### 5.1 設定

```java
static final String BASE_URL = "http://challenge.z2o.cloud";
static final String NICKNAME = "HAL_Marbun";
static final HttpClient client = HttpClient.newHttpClient();
static long offset = 0; // サーバーとのズレ（ミリ秒）
```

- `BASE_URL` — サーバーのアドレス
- `NICKNAME` — ランキングに表示される名前
- `offset` — 自分の時計とサーバーの時計のズレを補正する値

---

### 5.2 時刻同期 `syncTime()`

**なぜ必要か？**  
自分のパソコンの時計とサーバーの時計が数ミリ秒ずれていると、いつも遅れてしまいます。

**やり方（NTPと同じ考え方）:**

```
リクエスト送信前の時刻 → [before]
        ↓ HTTP通信
サーバーが受け取った時刻 → [serverTime]
        ↓
レスポンス受け取り後の時刻 → [after]

往復時間 (RTT) = after - before
サーバー時刻の推定 = before + RTT / 2
offset = serverTime - 推定値
```

```java
static void syncTime() throws Exception {
    long before = System.currentTimeMillis();
    // ... POST リクエスト送信 ...
    long after = System.currentTimeMillis();

    long serverTime = data.getLong("called_at");
    long estimate   = before + (after - before) / 2;
    offset = serverTime - estimate;
}
```

> `offset` を使って補正した時刻を取得するメソッド:
> ```java
> static long now() {
>     return System.currentTimeMillis() + offset;
> }
> ```

---

### 5.3 メインループ

```java
while (true) {
    String id       = data.getString("id");
    long activesAt  = data.getLong("actives_at");

    // ① 大まかにスリープ（CPU節約）
    long waitTime = activesAt - now() - 10;
    if (waitTime > 0) Thread.sleep(waitTime);

    // ② スピンウェイトで精密に待機
    while (now() < activesAt) { /* 何もしない */ }

    // ③ PUTリクエスト送信
    HttpRequest putRequest = HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + "/challenges"))
        .header("X-Challenge-Id", id)
        .PUT(HttpRequest.BodyPublishers.noBody())
        .build();

    data = new JSONObject(client.send(putRequest, ...).body());

    // ④ チャレンジ終了の確認
    if (data.has("result")) break;
}
```

---

## 6. 技術的な工夫

### スピンウェイト（精密タイミング）

```java
while (now() < activesAt) {
    // 空ループ — 何もしない
}
```

| 方法 | 精度 | CPU使用 |
|------|------|---------|
| `Thread.sleep(ms)` | 低い（OSに依存） | 低い |
| スピンウェイト | 高い（ミリ秒以下） | 高い |
| **組み合わせ（本実装）** | **高い** | **適度** |

本実装では `Thread.sleep` で大まかに待ち、残り数ミリ秒をスピンウェイトで精密に合わせます。

---

### 時刻補正の効果

```
補正なし → 毎回 ±5〜20ms ズレが蓄積 → すぐ500ms超過で終了
補正あり → ±1〜3ms 程度に抑制    → 長時間継続可能 
```

---

## 7. 実行方法

### 必要なもの

- Java JDK 11 以上
- `org.json` ライブラリ（`json.jar`）

### 手順

```bash
# 1. ニックネームを変更（Challenge.java の中）
static final String NICKNAME = "あなたの名前";

# 2. コンパイル
javac -cp .:json.jar upgaragetest1/Challenge.java

# 3. 実行
java -cp .:json.jar upgaragetest1.Challenge
```

### 実行時の出力例

```
Offset: -5 ms
Attempt 1 diff: 2 ms
Total diff: 2 ms
Attempt 2 diff: 1 ms
Total diff: 3 ms
Attempt 3 diff: 3 ms
Total diff: 6 ms
...
Total attempts: 847
SUCCESS: http://challenge.z2o.cloud/xxxxxxxx
```

---

## 8. まとめ

```
┌─────────────────────────────────────────────────────┐
│  🔵  サーバーと「叩き合い」をするプログラムを作る       │
│  🟡  サーバーが次に叩く時刻を教えてくれる             │
│  🟢  ‘間に合い’ に叩かないと合計500msで終了          │
│  🔴  ポイント① → syncTime() でズレを補正する          │
│  🔴  ポイント② → スピンウェイトで精密に待つ            │
│  🏆  多く叩けるほどランキング上位へ！                  │
└─────────────────────────────────────────────────────┘
```

---
