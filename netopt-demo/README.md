# netopt-demo — Android 网络优化全链路实战

把陆业聪「Android 网络优化系列」5 篇文章里的每一项策略落成可跑、可量化、可截图的 demo。12 个对照实验、前后端 + Caddy + Grafana 一条龙，emulator 上端到端跑通。

> 文章原文（按系列顺序）
> 1. [Android 网络全链路拆解：一次 HTTP 请求背后的性能陷阱](https://mp.weixin.qq.com/s/WH_NmNmxD_VUyK7As21xiw)
> 2. [DNS 优化实战：从运营商 DNS 到 HttpDNS 的进化之路](https://mp.weixin.qq.com/s/MHxyn1CY65tHjNFWbNmFPg)
> 3. [连接优化与复用：让每一次握手都物超所值](https://mp.weixin.qq.com/s/MdWqWPVwtYZVvazYqhcqjQ)
> 4. [数据压缩与缓存策略：把带宽用到极致](https://mp.weixin.qq.com/s/Rh0F_WB4XWN4BJhlFfswcw)
> 5. [网络监控与容灾：让网络问题无处遁形](https://mp.weixin.qq.com/s/VoygN5ciQfxPyB3ptbNV-Q)

---

## 1 · 拓扑

```
[Android Emulator / 真机]
        │ https://*.demo.local:4443 (h2)
        │ https://*.demo.local:4444 (h1)
        ▼
   Caddy 2.11 (Mac 原生)                  ⇦ TLS 终止 + 协议切换
        │ mkcert wildcard cert
        │ reverse_proxy 127.0.0.1:8080
        ▼
   Spring Boot 4 (Java 21 + 虚拟线程)      ⇦ 业务 + chaos 注入
        │ Micrometer ──┐
        │              ▼
        │      /actuator/prometheus
        ▼              ▼
   MySQL 8 / Redis 7   Prometheus → Grafana   ⇦ 既有 docker-compose
```

| 进程 | 在哪 | 端口 | 角色 |
|---|---|---|---|
| Spring Boot | Mac 原生 (mvnw) | 8080 | 业务 + chaos |
| Caddy | Mac 原生 (brew) | 4443 (H2), 4444 (H1) | TLS 终止 + 协议切换 |
| MySQL/Redis/Kafka/Prometheus/Grafana | docker compose | 3306/6379/9092/9090/3000 | 既有栈 |

---

## 2 · 工程结构

```
iwatchme-android/netopt-demo/                 # Android 子 module (:netopt-demo)
├── build.gradle.kts                          # 用 iwatchme.android.application.compose
├── src/main/proto/feed.proto                 # ⭐ 共享 proto schema (springshop 也读这里)
├── src/main/res/
│   ├── raw/dev_root_ca.pem                   # mkcert root CA — APK 内置 → 自动信任开发证书
│   └── xml/network_security_config.xml       # debug build 信任 dev CA
├── src/main/assets/fallback_config.json      # E12 三级容灾的 L3 兜底
└── src/main/kotlin/com/iwatchme/netopt/
    ├── MainActivity.kt + NetoptApp.kt
    ├── net/
    │   ├── ApiHost.kt                        # emulator vs 真机 vs frp 自动切换
    │   ├── EncodingType.kt                   # 4 编码枚举
    │   ├── ClientFactory.kt                  # 6 个工厂 (baseline/forEncoding/cached/withDns/withDispatcherCap/naive/resilient)
    │   ├── monitor/
    │   │   ├── NetMonitorListener.kt         # OkHttp EventListener 全埋点
    │   │   └── TimingRecord.kt
    │   ├── dns/
    │   │   ├── SlowSystemDns.kt              # 模拟运营商 DNS 慢 + 长尾
    │   │   ├── HttpDnsResolver.kt            # HttpDNS + SWR + 三层兜底
    │   │   └── MapHostDns.kt                 # 多 host 共指同 IP
    │   └── interceptor/
    │       ├── EncodingInterceptor.kt        # 显式 Accept-Encoding 关掉 OkHttp 自动 gzip
    │       ├── RetryInterceptor.kt           # 指数退避 + 抖动
    │       └── EndpointFailover.kt           # path 级降级 + body drain
    ├── data/
    │   ├── FeedDecoder.kt                    # JSON/Gzip/Brotli + 手写 protobuf reader
    │   └── NoteStore.kt                      # 离线优先 (SharedPref 版 Room)
    └── ui/
        ├── ExperimentsScreen.kt              # 12 实验入口
        ├── ExperimentDetailScreen.kt         # E1 用
        ├── ExperimentE2Screen.kt … E12Screen.kt
        └── component/
            ├── WaterfallChart.kt             # 单请求 5 段瀑布
            ├── BatchTimelineChart.kt         # 并发批次 timeline (排队可视化)
            └── EncodingBarChart.kt           # 4 柱对比

iwatchme-springboot/                         # Spring Boot 服务端 (主项目内 httpopt 子包)
├── pom.xml                                  # +4 dep: protobuf-java / brotli4j / protoc-jar / build-helper
├── prometheus.yml                            # Prometheus 配置
├── docker-compose.yml                        # mysql/redis/kafka/prometheus/grafana
└── src/main/java/com/iwatchme/springshop/httpopt/
    ├── config/HttpOptWebMvcConfig.java       # 注册 chaos interceptor
    ├── interceptor/ChaosInterceptor.java     # 全局 ?rtt= 注入
    ├── controller/
    │   ├── PingController.java               # GET /api/opt/ping
    │   ├── FeedController.java               # 4 编码 endpoint
    │   ├── FeedIncrementalController.java    # ?since=
    │   ├── ConfigController.java             # 3 cache mode
    │   ├── HttpDnsController.java            # mock HttpDNS
    │   ├── ChaosController.java              # /flaky/primary + /flaky/backup
    │   ├── ImageController.java              # 4 图片变体
    │   ├── NotesController.java              # E11 笔记同步
    │   └── MetricsIngestController.java      # 客户端 metric 收口 → Micrometer
    └── dto/FeedItemDto.java
└── tools/
    ├── caddy/Caddyfile                       # 4443 (h2) + 4444 (h1) + mkcert
    ├── frp/                                  # Scene C 外网 / 4G 真机测试
    │   ├── frpc.toml / frps.toml
    │   └── start-client.sh + README.md
    └── grafana/
        ├── netopt-dashboard.json             # 5 panel dashboard JSON
        └── README.md
```

---

## 3 · 快速运行

```bash
# 0) 一次性: brew install mkcert caddy + mkcert -install
mkdir -p /Users/iwatchme/Desktop/Spring/spring/iwatchme-springboot/tools/caddy
cd /Users/iwatchme/Desktop/Spring/spring/iwatchme-springboot/tools/caddy
mkcert -cert-file demo.local.crt -key-file demo.local.key \
    localhost 10.0.2.2 127.0.0.1 \
    h1.demo.local h2.demo.local api.demo.local cdn.demo.local img.demo.local

# 1) 启依赖
cd /Users/iwatchme/Desktop/Spring/spring/iwatchme-springboot
docker compose up -d   # mysql/redis/prometheus/grafana

# 2) 启 Spring (Java 21 via mise)
mise exec java@21 -- ./mvnw spring-boot:run

# 3) 启 Caddy (新窗口)
cd tools/caddy && caddy run --config Caddyfile

# 4) 装 APK
cd /Users/iwatchme/android/iwatchme-android
./gradlew :netopt-demo:installDebug
adb shell am start -n com.iwatchme.netopt/.MainActivity
```

---

## 4 · 12 个实验 × 优化策略 × 真实代码

每节按相同结构：**原理 → 文章引用 → 真实代码位置 → 实测数据**。

---

### Series 1 · 全链路拆解

#### E1 — 用 EventListener 拆开一次 HTTP 请求

**原理**
> 一次 HTTP 请求 = DNS (0-2000ms) → TCP 三次握手 (1 RTT) → TLS 握手 (1-2 RTT) → 请求发送 → 服务端处理 → 响应接收 → 数据解析。**握手三件套占总耗时 70%+，服务端处理只占 7%。**

文章 1 给的 P50 分布：
```
DNS:120ms  TCP:150ms  TLS:200ms  请求:20ms  服务端:45ms  响应:130ms  ─ 总 665ms
```

**代码** — `net/monitor/NetMonitorListener.kt`

```kotlin
class NetMonitorListener(
    private val onComplete: (TimingRecord) -> Unit,
) : EventListener() {
    override fun callStart(call: Call) { callStartMs = System.currentTimeMillis(); … }
    override fun dnsStart(call: Call, domainName: String) { dnsStartMs = … }
    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) { dnsEndMs = … }
    override fun connectStart(…) { hadConnectStart = true; connectStartMs = … }
    override fun secureConnectStart(…) { tlsStartMs = … }
    override fun secureConnectEnd(…) { tlsEndMs = … }
    override fun connectEnd(…, protocol: Protocol?) { connectEndMs = …; this.protocol = … }
    override fun connectionAcquired(call: Call, connection: Connection) {
        reused = !hadConnectStart       // 没触发 connectStart = 复用
        if (protocol == null) protocol = connection.protocol().toString()
    }
    override fun requestHeadersStart(call: Call) { requestStartMs = … }
    override fun responseHeadersStart(call: Call) { responseStartMs = … }
    override fun responseBodyEnd(call: Call, byteCount: Long) { responseEndMs = …; respBytes = byteCount }
    override fun callEnd(call: Call) { success = true; finish() }
    override fun callFailed(call: Call, ioe: IOException) { errorType = classifyError(ioe); finish() }
}
```

**可视化** — `ui/component/WaterfallChart.kt` 把 5 段（DNS/TCP/TLS/Wait/Recv）画成水平时间条，命中复用时显示 `REUSED`。

**实测**
| 请求 | TCP | TLS | TTFB | total | 状态 |
|---|---|---|---|---|---|
| 新建 | 12ms | 31ms | 6ms | **54ms** | NEW |
| 复用 | 0ms | 0ms | 5ms | **7ms** | **REUSED** |

复用节省 87%。这正是文章 3 一开始那个"为什么同一接口第一次和第二次差 300ms" 的答案。

---

### Series 2 · DNS 优化

#### E2 — HttpDNS + Stale-While-Revalidate + 三层兜底

**原理**
> 运营商 LocalDNS 四大坑: 劫持 / 调度不准 / 缓存乱 / 长尾 (P99 2000ms+)。HttpDNS 走 HTTPS GET 拿 IP 绕开 LocalDNS。**关键不在 P50,在 P99 长尾。**
> Stale-while-revalidate: cache 过新鲜期但未过陈旧期时返回旧值 + 后台异步刷新,把"过期后阻塞重新解析"变成"瞬时拿旧值"。

**服务端** — `httpopt/controller/HttpDnsController.java`
```java
@GetMapping
public Map<String, Object> resolve(@RequestParam String host, @RequestParam(defaultValue = "0") int delay) {
    if (delay > 0) LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(delay));
    return Map.of("host", host, "ips", List.of("10.0.2.2"), "ttl", 60, "source", "mock-httpdns");
}
```

**客户端 — Slow DNS baseline** `net/dns/SlowSystemDns.kt`
```kotlin
class SlowSystemDns(
    private val avgMs: Long = 500, private val tailMs: Long = 1500, private val tailRate: Float = 0.25f,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val delay = if (Random.nextFloat() < tailRate) tailMs + Random.nextLong(-200, 200)
                    else avgMs + Random.nextLong(-100, 100)
        Thread.sleep(delay.coerceAtLeast(0))
        return listOf(InetAddress.getByName("10.0.2.2"))
    }
}
```

**客户端 — HttpDNS 三层兜底** `net/dns/HttpDnsResolver.kt`
```kotlin
class HttpDnsResolver(
    private val httpDnsBaseUrl: String,
    private val fallback: Dns = SlowSystemDns(),
    private val freshMs: Long = 60_000L,
    private val staleMs: Long = 5 * 60_000L,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val now = System.currentTimeMillis()
        cache[hostname]?.let { e ->
            return when {
                now < e.expireAt -> e.addresses                          // L1 cache 命中 (~0ms)
                now < e.staleAt  -> { scope.launch { resolveAndCache(hostname) }; e.addresses }  // SWR
                else             -> resolveAndCache(hostname) ?: fallback.lookup(hostname)
            }
        }
        return resolveAndCache(hostname) ?: fallback.lookup(hostname)    // L2 HttpDNS → L3 SystemDNS
    }
}
```

**实测**
| Lane | avg DNS | max DNS (P100) |
|---|---|---|
| Slow System DNS | 926ms | **1554ms** |
| HttpDNS + SWR | **3ms** | **16ms** |

P100 改善 **-99%**。文章 2 给的 P99 1500ms→180ms 在 demo 里被放大到 1554→16ms。

---

### Series 3 · 连接优化与复用

#### E3 — HTTP/1.1 vs HTTP/2 多路复用（变量分离）

**原理**
> H1.1 一连接一请求（队头阻塞）。H2 一条 TCP 上多 stream 并发。
> **但要小心**:浏览器对 H1 限 6 conn/host(`maxRequestsPerHost`),对 H2 不限。这个 dispatcher cap 差异本身就贡献了大部分速度差,不全是多路复用本身。

我们把 cap 和 multiplex 解耦成 4 个 lane:

**服务端协议切换** — `tools/caddy/Caddyfile`
```caddy
{
    servers :4444 { protocols h1 }   # 强制 HTTP/1.1
}
:4443 { tls demo.local.crt demo.local.key
        reverse_proxy 127.0.0.1:8080 }
:4444 { tls demo.local.crt demo.local.key
        reverse_proxy 127.0.0.1:8080 }
```

**客户端 — dispatcher cap 工厂** `net/ClientFactory.kt`
```kotlin
fun withDispatcherCap(maxPerHost: Int, onTiming: (TimingRecord) -> Unit): OkHttpClient {
    val dispatcher = Dispatcher().apply {
        maxRequests = 256
        maxRequestsPerHost = maxPerHost   // 关键:enqueue() 才会被它限流,execute() 不会
    }
    return OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .eventListenerFactory(EventListener.Factory { NetMonitorListener(onTiming) })
        .build()
}
```

**客户端 — 4 lane 配置** `ui/ExperimentE3Screen.kt`
```kotlin
H1_REAL("H1 cap=6 (real)",  url = h1 :4444, cap = 6)   // 浏览器经典限制
H1_FAIR("H1 cap=12 (fair)", url = h1 :4444, cap = 12)  // 解开 cap 看纯协议
H2_FAIR("H2 cap=12 (fair)", url = h2 :4443, cap = 12)
H2_REAL("H2 cap=64 (real)", url = h2 :4443, cap = 64)  // H2 不限
```

**排队可视化** — `ui/component/BatchTimelineChart.kt` 每行画一条 `(wait, exec)`，wait = `wallStartMs - batchStartMs`（OkHttp `requestHeadersStart` 时刻减去 batch 起点），exec = `totalMs - requestStartOffset`。

**实测**
```
H1 cap=6 (real):  664ms  ← 6 跑 + 6 等
H1 cap=12 (fair): 370ms  ← 12 个 TCP+TLS 并发
H2 cap=12 (fair): 326ms
H2 cap=64 (real): 352ms

Δ cap     (H1 6→12)       = -44.3%   ← 浏览器现实差异
Δ multiplex (H1→H2 cap=12) = -11.9%   ← 协议层纯收益 (localhost RTT≈0,真实弱网下放大)
```

#### E9 — Connection Coalescing（域名收敛）

**原理**
> 多个不同 hostname 如果(1)解析到同一 IP (2) TLS 证书 SAN 同时覆盖 (3) 同端口,OkHttp 自动复用一条 H2 连接的多 stream。文章 3 里"6 子域名收敛 + 通配符证书"复用率 62%→78%。

**客户端 — Dns 映射** `net/dns/MapHostDns.kt`
```kotlin
class MapHostDns(private val mappings: Map<String, String>, private val fallback: Dns = Dns.SYSTEM) : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        mappings[hostname]?.let { listOf(InetAddress.getByName(it)) } ?: fallback.lookup(hostname)
}
```

**实验** `ui/ExperimentE9Screen.kt`
```kotlin
val hostMap = listOf("api.demo.local", "cdn.demo.local", "img.demo.local", "h2.demo.local")
                .associateWith { "10.0.2.2" }

// Disjoint:  每个 host 独立 client = 独立 connection pool → 各自握手
val disjointClients = DEMO_HOSTS.associateWith { ClientFactory.withDns(MapHostDns(hostMap)) { … } }
// Coalesced: 一个 client 共享 pool → OkHttp 自动 coalesce
val coalescedClient = ClientFactory.withDns(MapHostDns(hostMap)) { … }
```

**实测（4 个 host 各请求一次）**
| Lane | reused | new conns | 累计 TLS |
|---|---|---|---|
| Disjoint | 0/4 | 4 | **44ms** |
| **Coalesced** | **3/4** | **1** | **7ms** |

第 1 个 api.demo.local 握手；后 3 个 cdn/img/h2 看到 SAN 命中 + 同 IP → **直接走第 1 条连接的不同 stream**，0 ms TLS。

#### Quick Win: 共享 OkHttpClient 实例

文章 1 Quick Win 1 强调"哪怕 host 不同也要共享 client/pool"。我们的 `ClientFactory` 工厂方法每个实验返回独立 client 仅为隔离 lane，**生产环境必须复用单例**——这正是 `coalescedClient` lane 复用率高的本质。

---

### Series 4 · 数据压缩与缓存

#### E5 — JSON / Gzip / Brotli / Protobuf 4 编码端到端

**原理**
> Gzip 文章 4 L107-124: OkHttp 默认 `Accept-Encoding: gzip` 自动加 + 自动解压,**你不需要做任何配置**。但如果你显式 set 这个头,自动行为就被关掉。
> Brotli 文章 4 L137: 同等 CPU 比 Gzip 多压 15-25%。Caddy/Spring 内置都没,要自己加 brotli4j。
> Protobuf 文章 4 L168: 从根上"少生成数据"。字段名变 tag,无引号无逗号。

**服务端 — 启动时预压 4 份** `httpopt/controller/FeedController.java`
```java
@PostConstruct
void warmEncodings() throws IOException {
    jsonBytes  = objectMapper.writeValueAsBytes(sample);
    gzipBytes  = gzip(jsonBytes);
    Brotli4jLoader.ensureAvailability();
    brBytes    = Encoder.compress(jsonBytes);
    // protobuf: 用 protoc-jar 生成的 Java 类
    FeedItemList.Builder list = FeedItemList.newBuilder();
    for (FeedItemDto d : sample) list.addItems(FeedItem.newBuilder().setId(d.id())…build());
    protoBytes = list.build().toByteArray();
}

@GetMapping
public ResponseEntity<byte[]> list(@RequestParam(defaultValue="50") int limit,
                                   @RequestHeader(value="Accept", defaultValue="application/json") String accept,
                                   @RequestHeader(value="Accept-Encoding", defaultValue="identity") String acceptEncoding) {
    if (accept.contains("application/x-protobuf")) return ok().contentType(PROTOBUF).body(protoBytes);
    if (acceptEncoding.contains("br"))             return ok().header("Content-Encoding","br").body(brBytes);
    if (acceptEncoding.contains("gzip"))           return ok().header("Content-Encoding","gzip").body(gzipBytes);
    return ok().contentType(MediaType.APPLICATION_JSON).body(jsonBytes);
}
```

**proto schema 单一源** — `iwatchme-android/netopt-demo/src/main/proto/feed.proto` 同时被 springshop pom.xml 通过 `protoc-jar-maven-plugin` 消费。

**客户端 — 显式 Accept-Encoding 关闭 OkHttp 自动 gzip** `net/interceptor/EncodingInterceptor.kt`
```kotlin
class EncodingInterceptor(private val type: EncodingType) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request().newBuilder()
            .header("Accept", type.accept)
            .header("Accept-Encoding", type.acceptEncoding)
            .build()
        return chain.proceed(req)
    }
}
```
> 反直觉点: OkHttp 一旦看到客户端**主动**设了 `Accept-Encoding`，就不再自动解压。这样 `responseBodyEnd(byteCount)` 报告的是**真实 wire bytes**而不是解压后大小——demo 才能展示真实压缩节省。文章 4 L107-124 警告"手动设 Accept-Encoding 会关闭自动解压"，这里我们**故意利用**它做对照。

**客户端 — 手写 protobuf reader** `data/FeedDecoder.kt`
```kotlin
object FeedDecoder {
    fun decode(type: EncodingType, raw: ByteArray): List<FeedItemView> = when (type) {
        EncodingType.JSON     -> decodeJson(raw)
        EncodingType.GZIP     -> decodeJson(GZIPInputStream(ByteArrayInputStream(raw)).readBytes())
        EncodingType.BROTLI   -> decodeJson(BrotliInputStream(ByteArrayInputStream(raw)).readBytes())
        EncodingType.PROTOBUF -> ProtoFeedReader.decodeList(raw)
    }
}
// ProtoFeedReader: 70 行手写 wire format 解析器,只支持 varint/length-delimited
// (Wire 4.9.9 和 5.0.0 都还在用 Gradle 8.13 移除的内部 API,等上游修复)
```

**实测（100 条 Lorem ipsum）**
| 编码 | wire bytes | vs JSON | 解码后 items |
|---|---|---|---|
| JSON     | 26169 | 100.0% | 100 ✓ |
| **Gzip** | **1662** | **6.4%** | 100 ✓ |
| **Brotli** | **861** | **3.3%** | 100 ✓ |
| Protobuf | 20784 | 79.4% | 100 ✓ |

Brotli **-96.7%** —— Lorem ipsum 模板内容重复度高，真实 API 一般 -10~25%。Protobuf 在 string-heavy 数据上只省 20%（字段名 + 数字 tag 节省被长 string 内容稀释），所以文章 4 强调 **Protobuf + Gzip 才是组合拳**。

#### E6 — HTTP 缓存（200 / 304 / 强缓存）

**原理**
> 强缓存（`Cache-Control: max-age=N`）→ 客户端 N 秒内**不发请求**，磁盘读取 <5ms。
> 协商缓存（ETag）→ 客户端发 `If-None-Match`，server 比对返 304 + 空 body，省一次 body 传输。
> 文章 4 L228 说 "OkHttp 自动处理这套语义，你只要 `.cache(Cache(dir, size))`"。

**服务端** — `httpopt/controller/ConfigController.java`
```java
@GetMapping
public ResponseEntity<byte[]> get(@RequestParam(defaultValue="etag") String cache,
                                  @RequestHeader(value="If-None-Match", required=false) String inm) {
    return switch (cache) {
        case "none"   -> ok().header("Cache-Control","no-store").body(payloadBytes);
        case "strong" -> ok().header("Cache-Control","max-age=60, public").body(payloadBytes);
        case "etag"   ->
            if (etag.equals(inm)) status(NOT_MODIFIED).header("Cache-Control","no-cache").header("ETag",etag).build();
            else                  ok().header("Cache-Control","no-cache").header("ETag",etag).body(payloadBytes);
    };
}
```

**客户端 — 加 disk cache** `net/ClientFactory.kt`
```kotlin
fun cached(cacheDir: File, sizeBytes: Long = 10L * 1024 * 1024, onTiming: …): OkHttpClient =
    OkHttpClient.Builder()
        .cache(Cache(File(cacheDir, "http_cache_e6"), sizeBytes))
        .eventListenerFactory(EventListener.Factory { NetMonitorListener(onTiming) })
        .build()
```

**判定命中层级** `ui/ExperimentE6Screen.kt`
```kotlin
private fun classify(resp: Response): CacheHit {
    val net = resp.networkResponse
    val cache = resp.cacheResponse
    return when {
        net == null && cache != null -> CacheHit.LOCAL_CACHE      // 强缓存命中,完全没碰网络
        net != null && cache != null -> CacheHit.NEGOTIATED_304   // ETag revalidated
        else                          -> CacheHit.FRESH_NET
    }
}
```

**实测（每 mode 3 次）**
| Mode | #1 | #2 | #3 |
|---|---|---|---|
| No-Store | FRESH 200 | FRESH 200 | FRESH 200 |
| **ETag** | FRESH 200 | **304 · 0B body · 7ms** | **304 · 0B body · 10ms** |
| **Strong** | FRESH 200 | **LOCAL · 2ms** | **LOCAL · 2ms** |

#### E7 — 增量同步

**原理**
> 文章 4 L338 给的场景: 100 条消息每次刷新只新增 2 条,**98% 数据被重复传输**。改成 `?since=v` 后服务端只返 version > v 的条目。

**服务端** — `httpopt/controller/FeedIncrementalController.java`
```java
@GetMapping
public ResponseEntity<byte[]> incremental(@RequestParam(defaultValue="0") long since) {
    List<FeedItemDto> filtered = sample.stream().filter(it -> it.version() > since).toList();
    return ok().header("X-Items-Returned", String.valueOf(filtered.size())).body(json(filtered));
}
```

**实测**
| 模式 | bytes | items |
|---|---|---|
| Full (since=0) | 26169 | 100 |
| **Incremental (since=98)** | **529** | **2** |
| 节省 | **-98%** | — |

#### E8 — 图片格式 + BlurHash 占位

**原理**
> 文章 4 L536 说图片占 App 流量 60-80%。AVIF 比 JPEG 小 57% (Android 12+),WebP 35% (Android 4.0+ 覆盖近 100%)。但更关键的是**渐进式加载**: BlurHash 25 字节占位让用户首屏 -40% 感知时间。

**服务端 — 启动时生成 4 变体** `httpopt/controller/ImageController.java`
```java
@PostConstruct
void warm() throws IOException {
    BufferedImage src = renderSample(1080, 720);
    jpegHi  = encodeJpeg(src, 0.92f);
    jpegMd  = encodeJpeg(src, 0.60f);
    jpegLo  = encodeJpeg(src, 0.30f);
    blurPng = encodePng(downsample(src, 16, 11));  // BlurHash 等效:暴力降采样
}
```

**客户端用 Coil 加载** `ui/ExperimentE8Screen.kt`
```kotlin
AsyncImage(
    model = ImageRequest.Builder(context)
        .data("${ApiHost.baseUrl}/api/opt/img?format=${v.query}")
        .build(),
    contentScale = ContentScale.Fit,
)
```

**实测**
| 变体 | bytes | vs HI |
|---|---|---|
| JPEG q=92 | 46793 | 100.0% |
| JPEG q=60 | 22076 | 47.2% |
| JPEG q=30 | 16560 | 35.4% |
| **Blur 16×11** | **247** | **0.5%** |

Blur 占位**247 字节** —— 这就是 BlurHash 在生产里的本质:几乎免费的"先有个东西看",真图在后台慢慢加载。

#### E11 — 离线优先

**原理**
> 文章 4 L443: 核心功能(笔记/聊天/待办)**断网也要正常使用**。本地数据库是 single source of truth,网络只是同步通道。UI 操作 → 立刻写本地 → UI 立刻反映 → 后台 WorkManager 静默同步。

**简化版本地存储 (文章用 Room+WorkManager,demo 用 SharedPref 简化)** `data/NoteStore.kt`
```kotlin
fun add(text: String): LocalNote {
    val note = LocalNote(
        localId = UUID.randomUUID().toString(),
        text = text,
        status = "PENDING",                          // 关键字段
        updatedAt = System.currentTimeMillis(),
    )
    write(all() + note)
    return note   // <5ms — UI 立即拿到
}
fun markSynced(localId: String, serverId: Long) {
    write(all().map { if (it.localId == localId) it.copy(status="SYNCED", serverId=serverId) else it })
}
```

**Sync 操作** `ui/ExperimentE11Screen.kt`
```kotlin
suspend fun pushOne(note: LocalNote): Long? {
    if (simulateOffline) return null
    return withContext(Dispatchers.IO) {
        val req = Request.Builder().url("${ApiHost.baseUrl}/api/opt/notes").post(JSONObject(…).toRequestBody()).build()
        client.newCall(req).execute().use { resp -> JSONObject(resp.body!!.string()).optLong("serverId") }
    }
}
fun syncPending() {
    val pending = store.all().filter { it.status == "PENDING" }
    pending.forEach { note -> pushOne(note)?.let { store.markSynced(note.localId, it) } }
}
```

UI 用户路径:
1. 输入文字 → Save (Local) → 列表立刻多一行 PENDING (~3ms)
2. 切 Offline toggle → 继续 Save 仍 work
3. 切回 Online → Sync PENDING → PENDING 变 SYNCED 带 server#N

---

### Series 5 · 网络监控与容灾

#### E10 — 弱网 / 重试 / Endpoint Failover

**原理**
> 文章 5 L547-579: 指数退避 + 随机抖动(`baseMs · 2^attempt + 0~50% jitter`),避免故障恢复时百万客户端同时重试形成脉冲。
> 文章 5 L397-447: 主域名挂了切备用,**多域名容灾解决 80% 可用性问题**。

**服务端 chaos** — `httpopt/controller/ChaosController.java`
```java
@GetMapping("/primary")
public ResponseEntity<Map<String,Object>> primary(@RequestParam(defaultValue="0.5") double fail, ...) {
    if (ThreadLocalRandom.current().nextDouble() < fail) {
        return status(SERVICE_UNAVAILABLE).header("Connection","close").body(Map.of("ok",false));
    }
    return ok(Map.of("ok",true));
}
@GetMapping("/backup")
public ResponseEntity<Map<String,Object>> backup(@RequestParam(defaultValue="0.0") double fail, ...) { … }
```

**客户端 — 指数退避** `net/interceptor/RetryInterceptor.kt`
```kotlin
class RetryInterceptor(maxRetries:Int=3, baseMs:Long=200, maxMs:Long=4000,
                      retryableCodes:Set<Int> = setOf(502,503,504)) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        for (attempt in 0..maxRetries) {
            try {
                val resp = chain.proceed(request)
                if (resp.code in retryableCodes && attempt < maxRetries) {
                    resp.close(); Thread.sleep(delay(attempt)); continue
                }
                if (resp.code in retryableCodes) {
                    // 最后一次仍 5xx → drain body,让上游 EndpointFailover 能继续 chain
                    val drained = (resp.body?.bytes() ?: ByteArray(0)).toResponseBody(resp.body?.contentType())
                    resp.close()
                    return resp.newBuilder().body(drained).build()
                }
                return resp
            } catch (e: IOException) {
                lastErr = e
                if (attempt < maxRetries) Thread.sleep(delay(attempt))
            }
        }
        throw lastErr ?: IOException("Retry budget exhausted")
    }
    private fun delay(attempt: Int): Long {
        val exp = minOf(baseMs * (1L shl attempt), maxMs)
        return exp + (exp * Random.nextFloat() * 0.5f).toLong()   // 0..50% jitter
    }
}
```

**客户端 — Path failover** `net/interceptor/EndpointFailover.kt`
```kotlin
class EndpointFailover(private val candidates: List<String>) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var lastResp: Response? = null
        for (path in candidates) {
            val attempt = original.newBuilder().url(original.url.newBuilder().encodedPath(path).build()).build()
            val resp = chain.proceed(attempt)
            if (resp.code < 500) { lastResp?.close(); return resp }
            // 关键: drain body + 重建,否则下次 chain.proceed 抛 "previous response still open"
            val drained = (resp.body?.bytes() ?: ByteArray(0)).toResponseBody(resp.body?.contentType())
            resp.close()
            lastResp?.close()
            lastResp = resp.newBuilder().body(drained).build()
        }
        return lastResp ?: throw IOException("All endpoints failed")
    }
}
```

**实测（20 次请求 × 50% primary 失败率）**
| Lane | 成功率 | avg | 备注 |
|---|---|---|---|
| Naive | 12/20 = **60%** | 21ms | primary 失败直接抛给用户 |
| **Retry + Failover** | **20/20 = 100%** | 282ms | 重试 + 切 backup |

文章 5 给的"成功率 +12pp"在 demo 极端 50% 失败率下放大到 +40pp。

#### E12 — 三级容灾 (网络 → 缓存 → Asset)

**原理**
> 文章 5 L456-484 `ResilientRepo`: 网络失败时降级缓存,缓存也没就降级 APK 内预埋的兜底包。**断网不白屏**。

**客户端实现** `ui/ExperimentE12Screen.kt`
```kotlin
suspend fun resolveFresh(): ResolveResult {
    readNetwork(URL_CONFIG)?.let     { return ResolveResult.Fresh(it) }   // L1
    readCacheOnly(URL_CONFIG)?.let   { return ResolveResult.Stale(it) }   // L2
    readAsset()?.let                 { return ResolveResult.Fallback(it) } // L3
    return ResolveResult.Empty("nothing left")
}

private suspend fun readCacheOnly(path: String): String? = withContext(Dispatchers.IO) {
    val req = Request.Builder().url("${ApiHost.baseUrl}$path")
        .header("Cache-Control", "only-if-cached, max-stale=604800")   // 关键:让 OkHttp 只查 cache 不发网络
        .build()
    cachedClient.newCall(req).execute().use { resp ->
        if (resp.code in 200..299) resp.body?.string() else null
    }
}

private fun readAsset(): String? =
    context.assets.open("fallback_config.json").bufferedReader().use { it.readText() }
```

**APK 内置兜底** — `src/main/assets/fallback_config.json`
```json
{
  "source": "asset-fallback",
  "version": "0.0.0",
  "featureFlags": { "newFeedUi": false, "darkMode": true }
}
```

**实测**
| 场景 | 命中层级 |
|---|---|
| Healthy | **L1 network · FRESH** |
| Offline (cache warm) | **L2 OkHttp cache · STALE** |
| Wipe + Offline | **L3 APK asset · FALLBACK** |

#### 监控接口 + Grafana

**服务端** — `httpopt/controller/MetricsIngestController.java`
```java
@PostMapping
public Map<String, Object> ingest(@RequestBody IngestPayload payload) {
    for (Item it : payload.items()) {
        Tags tags = Tags.of("lane", lane, "host", host, "protocol", protocol, "outcome", ok ? "ok" : "fail");
        ttfbTimers.computeIfAbsent(key, k -> Timer.builder("netopt_ttfb_ms")
                .publishPercentiles(0.5, 0.95, 0.99).tags(tags).register(registry))
                .record(it.ttfbMs(), MILLISECONDS);
        registry.counter("netopt_bytes_total", tags).increment(it.respBytes());
    }
    …
}
```

**Grafana dashboard** — `iwatchme-springboot/tools/grafana/netopt-dashboard.json`,5 panel:
- TTFB p50/p95/p99 by lane
- Bytes/sec by protocol
- Total call ms p95 by lane
- Success rate
- Request rate by lane

导入:`Grafana :3000` → Dashboards → Import → 上传 JSON。

---

## 5 · 工程踩坑笔记

按时间顺序记录,留给下一个看 demo 的人。

### 工具链
- **Wire 4.9.9 / 5.0.0 都引用了 Gradle 8.13 移除的 `FileOrUriNotationConverter`** → 用手写 70 行 protobuf reader (`ProtoFeedReader`) 替代。等 Wire 发新版可一键切回 codegen
- **Caddy Homebrew build 不带 brotli 模块** → 干脆让 Spring 端做 Brotli pre-compression,Caddy 只做 TLS + reverse proxy
- **`protocols h1` 是 Caddy v2 全局 `servers :PORT { ... }` 块的语法**,不是 site block
- **mvnw 默认走当前 shell JDK** → 必须 `mise exec java@21 -- ./mvnw …`,否则报"不支持发行版本 21"

### OkHttp 行为
- **`Accept-Encoding` 客户端不主动设,OkHttp 自动加 gzip 并透明解压**;**主动设**就关掉自动行为 → 我们 E5 故意利用这点测真实 wire bytes
- **`EventListener.callStart` 在 enqueue 那一刻就触发,不等 dispatcher promote** → 要看排队请用 `requestHeadersStart` 触发的时刻 (E3 `wallStartMs` 用的)
- **`Dispatcher.maxRequestsPerHost` 只限 `enqueue()` 异步调用**,`execute()` 同步调用不限 → E3 必须用 enqueue+Callback 才能看到排队
- **OkHttp 不允许在同一 client 上持有未 close 的 Response 同时发新请求** → 抛 `IllegalStateException: previous response is still open`。Retry/Failover 保留 5xx response 必须先 `body?.bytes()` drain + `close()` + `newBuilder().body(drained).build()` 重建
- **H2 cold-start 6 个并发请求会各自建 1 条 conn**,因为 connection pool 还空。要看 multiplexing 必须先 warmup 一次

### Spring Boot 4
- **starter-webmvc 不包含 Jackson auto-config** → ObjectMapper 不能 `@Autowired`,用 `new ObjectMapper()`
- **虚拟线程友好的 sleep** 用 `LockSupport.parkNanos`,不用 `Thread.sleep`

### Android Compose
- **`async` 默认 dispatcher 在 Compose runtime 上线程池小**,12 并发只能 2-4 个真并发 → 必须显式 `Dispatchers.IO`
- **`runCatching` 吞异常** → debug 时换成 `try/catch + 记 e.message`,否则失败原因看不到

### mkcert / TLS
- **mkcert 一次签 7 个 SAN**,后面再加域名要重签
- **Android 系统 trust store 不含 mkcert root CA** → 把 `rootCA.pem` 复制进 `res/raw/` + `network_security_config.xml` 加 trust-anchor,debug build 自动信任,**不需要在模拟器手动装证书**

---

## 6 · 不同场景的部署

### Scene A 本机 emulator (开发主力)
- `ApiHost.kt` 检测 emulator → 自动用 `https://10.0.2.2:4443`
- mkcert 证书已包含 `10.0.2.2`
- ✅ 端到端协议层可控

### Scene B 真机 + 同 Wi-Fi
- 改 `ApiHost.kt` 真机分支为 Mac 的 LAN IP (如 `https://192.168.1.42:4443`)
- 客户端 `MapHostDns` 把 `*.demo.local` 解析到这个 IP,TLS SNI 还走域名

### Scene C 远程 / 4G 真机
- 看 `iwatchme-springboot/tools/frp/README.md`
- **关键**: 用 `frp type="tcp"` 透传,不要用 ngrok/cloudflared (它们终止 TLS 把协议层实验全废)

---

## 7 · 12 实验索引

| # | 屏 | 文章对应 | 核心数据点 |
|---|---|---|---|
| E1 | ExperimentDetailScreen | 1 全链路 | 复用 -87% |
| E2 | ExperimentE2Screen | 2 HttpDNS+SWR | P100 -99% |
| E3 | ExperimentE3Screen | 3 H1 vs H2 | cap -44% / mux -12% 拆分 |
| E4 | (文档化) | 3 TLS 1.3 | Conscrypt 集成步骤 plan §六 |
| E5 | ExperimentE5Screen | 4 编码 | Brotli -96.7%, 解码全 round-trip |
| E6 | ExperimentE6Screen | 4 缓存 | 强缓存 2ms, 304 0B |
| E7 | ExperimentE7Screen | 4 增量 | -98% |
| E8 | ExperimentE8Screen | 4 图片 | Blur 247B = -99.5% |
| E9 | ExperimentE9Screen | 3 连接合并 | 累计 TLS 44→7ms |
| E10 | ExperimentE10Screen | 5 弱网/容灾 | 60→100% |
| E11 | ExperimentE11Screen | 4 离线优先 | 本地写 <5ms |
| E12 | ExperimentE12Screen | 5 三级降级 | L1/L2/L3 全跑通 |

---

## 8 · License 与免责

本工程仅为学习与演示。所有"-90% 流量""-1500ms 长尾"的数据是受控环境下的实测,**真实生产场景受 RTT/丢包率/CPU/服务端实现影响差异巨大**,请以你自己环境的 EventListener metric 为准——这也是文章 1 反复强调的:**网络优化的第一步不是优化,是度量**。
