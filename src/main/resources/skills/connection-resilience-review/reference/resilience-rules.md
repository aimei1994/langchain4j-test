# Connection Resilience Rules

## Key Principle
Only flag **connection establishment / client configuration** code — where the connection object or client is built.
Never flag API call methods like `.getForObject(`, `.query(`, `.get(`, `.set(`, `fetch(`, `axios.get(` — those are connection *usage*, not connection *setup*.

---

## Connection Types: HTTP, DB, REDIS, FTP

---

## PASS 1 — Source Code: Missing Retry/Reconnect at Connection Setup

### Rule: http-no-retry
- severity: critical
- Detect — connection **setup** keywords only (where HTTP client object is created/built):
  - Java/Kotlin: `new RestTemplate(`, `RestTemplate(new`, `WebClient.builder()`, `HttpClient.newBuilder()`, `new OkHttpClient(`, `OkHttpClient.Builder()`, `HttpClients.custom()`, `HttpClients.createDefault()`, `new CloseableHttpClient`, `new HttpComponentsClientHttpRequestFactory(`, `OpenAiChatModel.builder()`, `Feign.builder()`, `@FeignClient`
  - Python: `requests.Session()`, `httpx.Client(`, `httpx.AsyncClient(`, `aiohttp.ClientSession(`
  - TypeScript/JS: `axios.create(`, `new http.Agent(`, `new https.Agent(`, `got.extend(`
  - Go: `&http.Client{`, `http.Transport{`
  - C#: `new HttpClient(`, `IHttpClientFactory`
- NOT detected (skip these): `.getForObject(`, `.postForObject(`, `.exchange(`, `.execute(`, `axios.get(`, `axios.post(`, `fetch(`, `requests.get(`, `requests.post(`
- No retry guard means: none of `RetryTemplate`, `@Retryable`, `maxAttempts`, `resilience4j`, `CircuitBreaker`, `retryWhen(`, `retryIf(`, `.retry(`, `connectTimeout`, `readTimeout`, `socketTimeout`, `connectionRequestTimeout` in the enclosing class or bean factory method.
- Fix (Java): add `connectTimeout` + `readTimeout` to the client builder; add `@Retryable(retryFor = {SocketTimeoutException.class, IOException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))` on the service method that invokes this client.

### Rule: db-no-reconnect
- severity: critical
- Detect — connection **setup** keywords only (where pool or raw connection is created):
  - Java/Kotlin: `new HikariDataSource(`, `HikariConfig`, `DriverManager.getConnection(`, `DataSourceBuilder.create()`, `new BasicDataSource(`, `new ComboPooledDataSource(`, `new BoneCPDataSource(`
  - Python: `psycopg2.connect(`, `mysql.connector.connect(`, `create_engine(`, `sqlite3.connect(`
  - TypeScript/JS: `new Pool(`, `mysql.createConnection(`, `mongoose.connect(`, `new Sequelize(`, `createConnection(`
  - Go: `sql.Open(`
  - C#: `new SqlConnection(`, `new NpgsqlConnection(`
- NOT detected (skip these): `jdbcTemplate.query(`, `jdbcTemplate.update(`, `repository.save(`, `entityManager.persist(`, `session.execute(`, `@Query`
- No reconnect guard means: none of `connectionTimeout`, `maxLifetime`, `connectionTestQuery`, `keepaliveTime`, `initializationFailTimeout`, `connectionRetryInterval`, `@Retryable`, `RetryTemplate`, `TransientDataAccessException` in the enclosing class or config block.
- Fix (Java/HikariCP): set `config.setConnectionTimeout(30000)`, `config.setMaxLifetime(1800000)`, `config.setKeepaliveTime(60000)`, `config.setInitializationFailTimeout(-1)` on `HikariConfig`.

### Rule: redis-no-reconnect
- severity: major
- Detect — connection **setup** keywords only (where Redis connection factory or client is created):
  - Java/Kotlin: `new LettuceConnectionFactory(`, `new JedisConnectionFactory(`, `RedisClient.create(`, `new JedisPool(`, `new JedisCluster(`, `new Jedis(`
  - Python: `redis.Redis(`, `redis.StrictRedis(`, `aioredis.create_connection(`, `aioredis.create_redis(`
  - TypeScript/JS: `redis.createClient(`, `new Redis(`, `new IORedis(`
  - Go: `redis.NewClient(`, `redis.NewClusterClient(`
  - C#: `ConnectionMultiplexer.Connect(`
- NOT detected (skip these): `redisTemplate.opsForValue(`, `redisTemplate.execute(`, `jedis.get(`, `jedis.set(`, `stringRedisTemplate.`, `client.get(`, `client.set(`
- No reconnect guard means: none of `autoReconnect`, `reconnectDelay`, `reconnectBackoff`, `SocketOptions`, `ClientOptions`, `retryAttempts`, `retryInterval`, `commandTimeout`, `connectTimeout`, `topologyRefreshOptions` in the enclosing class or config block.
- Fix (Java/Lettuce): build `SocketOptions` with `connectTimeout` + `ClientOptions` with `autoReconnect(true)` and pass to `LettuceConnectionFactory`.

### Rule: ftp-no-reconnect
- severity: critical
- Detect — FTP/SFTP connection **establishment** keywords:
  - Java: `ftpClient.connect(`, `new FTPClient()`, `new FTPSClient()`, `new SFTPClient(`, `new JSch()`, `jsch.getSession(`
  - Python: `ftplib.FTP(`, `ftplib.FTP_TLS(`, `paramiko.SSHClient(`, `paramiko.SFTPClient`
  - TypeScript/JS: `ftp.connect(`, `new FTPClient(`, `sftp.connect(`
  - Go: `ftp.Dial(`, `sftp.NewClient(`
  - C#: `new FtpClient(`, `ftpClient.Connect(`
- No reconnect guard means: none of `retry`, `reconnect`, `maxAttempts`, `connectTimeout`, `dataTimeout`, `defaultTimeout`, `soTimeout`, `setDefaultTimeout` in the enclosing method or class.
- Fix (Java/Apache Commons Net): call `ftpClient.setDefaultTimeout(5000)`, `ftpClient.setConnectTimeout(5000)`, `ftpClient.setDataTimeout(Duration.ofSeconds(10))` before `ftpClient.connect()`; wrap `connect()` in a retry loop with max attempts and exponential backoff.

---

## PASS 2 — Test Code: Missing Retry/Reconnect Test Coverage

### Rule: http-retry-untested
- severity: critical
- Detect: test class that references an HTTP-client component (matched by PASS 1 http-no-retry) but contains NONE of:
  `SocketTimeoutException`, `ConnectException`, `ConnectTimeoutException`, `IOException`, `times(`, `verify(`, `willThrow`, `thenThrow`, `doThrow`, `assertThrows`, `RetryTemplate`, `maxAttempts`
- Fix (Java): add test mocking the HTTP client builder/factory to throw `SocketTimeoutException` on first call; assert retry count with `verify(mock, times(N))`.

### Rule: db-reconnect-untested
- severity: critical
- Detect: test class that references a DB-connected component (matched by PASS 1 db-no-reconnect) but contains NONE of:
  `TransientDataAccessException`, `DataAccessException`, `CannotAcquireLockException`, `thenThrow`, `willThrow`, `doThrow`, `times(`, `verify(`, `maxAttempts`, `connectionTimeout`
- Fix (Java): add test mocking `DataSource.getConnection()` to throw `SQLException` once then succeed; verify retry occurred.

### Rule: redis-reconnect-untested
- severity: major
- Detect: test class that references a Redis-connected component (matched by PASS 1 redis-no-reconnect) but contains NONE of:
  `RedisConnectionFailureException`, `JedisConnectionException`, `LettuceConnectionException`, `thenThrow`, `willThrow`, `doThrow`, `times(`, `verify(`, `retryAttempts`, `autoReconnect`, `commandTimeout`
- Fix (Java): add test mocking `LettuceConnectionFactory` to throw `RedisConnectionFailureException` once; verify autoReconnect or retry occurred.

### Rule: ftp-reconnect-untested
- severity: critical
- Detect: test class that references an FTP-connected component (matched by PASS 1 ftp-no-reconnect) but contains NONE of:
  `IOException`, `SocketException`, `FTPConnectionClosedException`, `thenThrow`, `willThrow`, `doThrow`, `times(`, `verify(`, `reconnect`, `retry`, `maxAttempts`, `connectTimeout`
- Fix (Java): add test mocking `FTPClient.connect()` to throw `IOException` on first call then succeed; assert retry logic called `connect()` exactly twice.

### Rule: no-test-file
- severity: critical
- Detect: source component matched by any PASS 1 rule for which NO test file references the component class name anywhere under the test source root.
- Fix: create `<ComponentName>Test` covering: (1) successful connection build, (2) first connection attempt fails then retries and succeeds, (3) all retry attempts exhausted throws final exception.

---

## Suggestion Templates

### Java — HTTP client with timeout (fix for http-no-retry)
```java
// In @Bean factory or constructor:
HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
factory.setConnectTimeout(3000);
factory.setConnectionRequestTimeout(3000);
RestTemplate restTemplate = new RestTemplate(factory);

// On the service method calling the HTTP endpoint:
@Retryable(
    retryFor = { SocketTimeoutException.class, IOException.class },
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2)
)
public String callRemote(String url) {
    return restTemplate.getForObject(url, String.class);
}

@Recover
public String recover(IOException e, String url) {
    log.error("All retries exhausted for {}", url, e);
    throw new ServiceUnavailableException("Remote call failed after retries", e);
}
```

### Java — HikariCP with reconnect settings (fix for db-no-reconnect)
```java
HikariConfig config = new HikariConfig();
config.setJdbcUrl(url);
config.setConnectionTimeout(30_000);
config.setMaxLifetime(1_800_000);
config.setKeepaliveTime(60_000);
config.setInitializationFailTimeout(-1); // retry indefinitely on startup
config.setConnectionTestQuery("SELECT 1");
return new HikariDataSource(config);
```

### Java — Lettuce with reconnect settings (fix for redis-no-reconnect)
```java
SocketOptions socketOptions = SocketOptions.builder()
    .connectTimeout(Duration.ofSeconds(2))
    .build();
ClientOptions clientOptions = ClientOptions.builder()
    .autoReconnect(true)
    .socketOptions(socketOptions)
    .build();
LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
    .clientOptions(clientOptions)
    .commandTimeout(Duration.ofSeconds(5))
    .build();
return new LettuceConnectionFactory(redisStandaloneConfiguration, clientConfig);
```

### Java — FTPClient with timeout and retry (fix for ftp-no-reconnect)

```java
FTPClient ftpClient = new FTPClient();
ftpClient.setDefaultTimeout(5_000);
ftpClient.setConnectTimeout(5_000);
ftpClient.setDataTimeout(Duration.ofSeconds(10));

int maxAttempts = 3;
for (int attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
        ftpClient.connect(host, port);
        break;
    } catch (IOException e) {
        if (attempt == maxAttempts) throw e;
        log.warn("FTP connect attempt {} failed, retrying...", attempt);
        Thread.sleep(1000L * attempt);
    }
}
```

### Java — HTTP retry test
```java
@Test
void shouldRetryOnSocketTimeoutThenSucceed() throws Exception {
    when(mockHttpClient.execute(any()))
        .thenThrow(new SocketTimeoutException("timed out"))
        .thenReturn(successResponse);

    service.callRemote(url);

    verify(mockHttpClient, times(2)).execute(any());
}

@Test
void shouldThrowAfterAllHttpRetriesExhausted() {
    when(mockHttpClient.execute(any()))
        .thenThrow(new SocketTimeoutException("timed out"));

    assertThrows(ServiceUnavailableException.class, () -> service.callRemote(url));
    verify(mockHttpClient, times(3)).execute(any());
}
```

### Java — DB reconnect test
```java
@Test
void shouldRetryOnTransientDataAccessExceptionThenSucceed() throws Exception {
    when(dataSource.getConnection())
        .thenThrow(new CannotAcquireLockException("deadlock"))
        .thenReturn(mockConnection);

    service.save(entity);

    verify(dataSource, times(2)).getConnection();
}
```

### Java — Redis reconnect test
```java
@Test
void shouldRetryOnRedisConnectionFailureThenSucceed() {
    when(connectionFactory.getConnection())
        .thenThrow(new RedisConnectionFailureException("refused"))
        .thenReturn(mockConnection);

    service.getCachedValue("key");

    verify(connectionFactory, times(2)).getConnection();
}
```

### Java — FTP reconnect test
```java
@Test
void shouldRetryFtpConnectOnIOExceptionThenSucceed() throws Exception {
    doThrow(new IOException("connection refused"))
        .doNothing()
        .when(ftpClient).connect(anyString(), anyInt());

    service.connectFtp();

    verify(ftpClient, times(2)).connect(anyString(), anyInt());
}
```
