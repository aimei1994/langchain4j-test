# External Connection Review Rules

## HTTP Client Patterns

### Detection Keywords
- Java/Kotlin: `RestTemplate`, `WebClient`, `OkHttpClient`, `HttpClient`, `HttpURLConnection`, `CloseableHttpClient`, `Feign`, `@FeignClient`, `openConnection`, `HttpGet`, `HttpPost`
- Python: `requests.get`, `requests.post`, `httpx.Client`, `aiohttp.ClientSession`, `urllib.request`
- TypeScript/JS: `fetch(`, `axios.get`, `axios.post`, `http.request`, `https.request`, `got(`, `superagent`
- Go: `http.Get(`, `http.Post(`, `http.NewRequest`, `http.DefaultClient`
- C#: `HttpClient`, `WebClient`, `HttpWebRequest`

### Retry/Reconnect Test Keywords (HTTP)
- `retry`, `retryOn`, `RetryTemplate`, `Retry`, `backoff`, `Backoff`, `timeout`, `Timeout`
- `resilience4j`, `Resilience4j`, `CircuitBreaker`, `circuitBreaker`
- `maxAttempts`, `maxRetries`, `retryCount`, `RetryPolicy`
- `SocketTimeoutException`, `ConnectTimeoutException`, `IOException`, `SocketException`
- `@Retryable`, `@Retry`, `retryWhen`, `retryIf`

---

## Database Connection Patterns

### Detection Keywords
- Java/Kotlin: `DataSource`, `Connection`, `DriverManager`, `JdbcTemplate`, `EntityManager`, `SessionFactory`, `HikariDataSource`, `@Repository`, `CrudRepository`, `JpaRepository`, `@Transactional`, `@Query`
- Python: `psycopg2.connect`, `mysql.connector.connect`, `sqlite3.connect`, `create_engine`, `Session`, `sessionmaker`
- TypeScript/JS: `pg.Client`, `mysql.createConnection`, `mongoose.connect`, `Sequelize`, `TypeORM`, `createConnection`, `prisma`
- Go: `sql.Open(`, `db.Query(`, `db.Exec(`, `gorm.Open(`
- C#: `SqlConnection`, `NpgsqlConnection`, `DbContext`, `EFCore`

### Retry/Reconnect Test Keywords (DB)
- `reconnect`, `Reconnect`, `connectionRetry`, `retryConnection`
- `DataAccessException`, `TransientDataAccessException`, `CannotAcquireLockException`
- `HikariPool`, `connectionTimeout`, `maxLifetime`, `idleTimeout`
- `@Transactional(retry`, `retryOnFailure`, `deadlock`, `Deadlock`
- `connection pool`, `connectionPool`, `pool exhausted`, `PoolExhausted`
- `DataSourceTransactionManager`, `PlatformTransactionManager`

---

## Redis Connection Patterns

### Detection Keywords
- Java/Kotlin: `RedisTemplate`, `StringRedisTemplate`, `Jedis`, `LettuceClient`, `RedisConnectionFactory`, `@RedisHash`, `RedisRepository`, `ReactiveRedisTemplate`
- Python: `redis.Redis(`, `redis.StrictRedis(`, `aioredis.create_connection`, `redis.asyncio`
- TypeScript/JS: `redis.createClient(`, `ioredis`, `new Redis(`, `createClient(`
- Go: `redis.NewClient(`, `go-redis`, `redigo`
- C#: `StackExchange.Redis`, `ConnectionMultiplexer.Connect(`

### Retry/Reconnect Test Keywords (Redis)
- `RedisConnectionFailureException`, `JedisConnectionException`, `LettuceConnectionException`
- `reconnectDelay`, `reconnectBackoff`, `autoReconnect`
- `retryAttempts`, `retryInterval`, `commandTimeout`
- `ClusterTopologyRefresh`, `topologyRefresh`, `sentinel`
- `RedisCluster`, `RedisSentinel`, `failover`, `Failover`

---

## Test Coverage Severity Levels

| Level | Condition |
|-------|-----------|
| `critical` | External connection found, zero test files exist for that component |
| `major` | Test files exist but no retry/reconnect keywords found anywhere in tests |
| `minor` | Retry tested but missing timeout or backoff scenarios |
| `info` | Fully covered — retry + timeout + backoff all present |
