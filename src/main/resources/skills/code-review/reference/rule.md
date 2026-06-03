# Code Review Rules

- ruleName : null pointer exception
  severity : major
  instruction : |
    Detect:
    - Java/Kotlin: unguarded `obj.method()` or `obj.field` where obj may be null; `Map.get()` result used without null check; `Optional.get()` without `isPresent()`; chained calls `a.getB().getC()` where any link may be null; `!!` on nullable Kotlin type
    - Python: calling methods or accessing attributes on values that may be `None`; `dict.get()` result used without `is None` guard
    - TypeScript/JS: property access on `T | null | undefined` without type guard or `?.`; missing `??` guard; array index access without bounds/undefined check
    - Go: pointer or interface used without nil check after a function that may return nil
    - Rust: `Option.unwrap()` or `expect()` without prior `is_some()` guard or match arm
    - C#: `.Value` on `Nullable<T>` without `.HasValue`; missing `?.` or `??`
    - PHP: method call on variable that may be `null` without null check
    Fix:
    - Java: wrap with `if (obj != null) { ... }` or use `Objects.requireNonNullElse`
    - Kotlin: replace `obj.method()` with `obj?.method()` or add `?: return` / `?: throw` guard
    - Python: add `if x is not None:` guard before the attribute/method access
    - TypeScript/JS: replace direct access with `?.` optional chaining or add `if (x != null)` guard
    - Go: add `if x != nil {` guard before the dereference
    - Rust: replace `.unwrap()` with `match` or `if let Some(v) = x { ... }`
    - C#: replace `.Value` with `?.` null-conditional or add `.HasValue` check
    - PHP: add `if ($x !== null) {` guard before the method call

- ruleName : command injection
  severity : critical
  instruction : |
    Detect:
    - Java: `Runtime.exec()` or `ProcessBuilder` constructed with user-controlled input concatenated directly; string-built shell commands e.g. `"cmd /c " + userInput` or `"sh -c " + userInput`
    - Kotlin: `Runtime.getRuntime().exec()` with string concatenation; `ProcessBuilder(listOf("sh", "-c", userInput))` with unsanitized input; string template `"sh -c $userInput"` passed to process builder
    - Python: `os.system()`, `subprocess.call/run/Popen` with `shell=True` and user input in the command string; `eval()` or `exec()` on user input
    - TypeScript/JS: `child_process.exec()` with user input concatenated into command string; `eval()` on user input
    - Go: `exec.Command("sh", "-c", userInput)` or any shell invocation with unsanitized user input
    - Rust: `Command::new("sh").arg("-c").arg(user_input)` with unsanitized input
    - C#: `Process.Start()` with user input in `FileName` or `Arguments` without sanitization; `cmd.exe /c` with concatenated user input
    - PHP: `shell_exec()`, `exec()`, `system()`, `passthru()`, `popen()` with user input; backtick operator `` `$userInput` ``
    Fix:
    - Java: use `ProcessBuilder` with argument list form (no shell), never concatenate user input into a shell string; validate/whitelist input before use
    - Kotlin: use `ProcessBuilder(listOf(binary, arg1, arg2))` with separate args, no shell invocation; never use string templates to build shell commands; validate input against a whitelist
    - Python: use `subprocess.run([cmd, arg1, arg2], shell=False)` list form; never pass `shell=True` with user input; validate input against a whitelist
    - TypeScript/JS: use `child_process.execFile()` or `spawn()` with argument array instead of `exec()`; never concatenate user input into shell string
    - Go: use `exec.Command(binary, arg1, arg2)` with separate args, not `sh -c`; validate input
    - Rust: use `Command::new(binary).arg(arg1)` with separate args, not shell invocation
    - C#: use `ProcessStartInfo` with `FileName` set to binary and `ArgumentList` for args; never build shell command strings from user input
    - PHP: use `escapeshellarg()` on every user-supplied argument; prefer parameter-based APIs over shell functions

- ruleName : path traversal
  severity : critical
  instruction : |
    Detect:
    - Java: `new File(baseDir, userInput)` or `Paths.get(userInput)` without canonical path check; user input concatenated into file path without verifying resolved path stays within allowed base
    - Kotlin: `File(baseDir, userInput)` or `Path.of(userInput)` without `canonicalPath` check; string template `"$baseDir/$userInput"` used as file path without validation
    - Python: `open(user_input)` directly; `os.path.join(base, user_input)` without `os.path.realpath()` check that result stays within base; `pathlib.Path(base) / user_input` without resolved path validation
    - TypeScript/JS: `path.join(baseDir, userInput)` without verifying resolved path starts with base; `fs.readFile(userInput)` or `fs.createReadStream(userInput)` with unsanitized input
    - Go: `filepath.Join(baseDir, userInput)` without `filepath.Clean` + prefix check against base
    - Rust: `Path::new(base).join(user_input)` without `canonicalize()` and base prefix check
    - C#: `Path.Combine(baseDir, userInput)` without `Path.GetFullPath()` check that result starts with base; `File.Open(userInput)` with unsanitized input
    - PHP: `file_get_contents($userInput)`, `fopen($userInput)`, `include $userInput`, or `require $userInput` with user input; `realpath()` result not verified against allowed base
    Fix:
    - Java: call `file.getCanonicalPath()` on the resolved path and verify it starts with `baseDir.getCanonicalPath()`; reject if not within base
    - Kotlin: check `File(baseDir, userInput).canonicalPath.startsWith(File(baseDir).canonicalPath)`; reject if outside base
    - Python: use `os.path.realpath(os.path.join(base, user_input))` and assert result starts with `os.path.realpath(base)`
    - TypeScript/JS: use `path.resolve(baseDir, userInput)` and verify it starts with `path.resolve(baseDir)`; reject if outside
    - Go: use `filepath.Clean(filepath.Join(base, userInput))` then check it starts with `filepath.Clean(base) + string(os.PathSeparator)`
    - Rust: use `.canonicalize()` on the joined path and verify it starts with the canonicalized base path
    - C#: use `Path.GetFullPath(Path.Combine(baseDir, userInput))` and verify it starts with `Path.GetFullPath(baseDir)`
    - PHP: use `realpath(base . '/' . $userInput)` and verify result starts with `realpath(base)`; never allow user input in `include`/`require`

- ruleName : insecure deserialization
  severity : critical
  instruction : |
    Detect:
    - Java/Kotlin: `ObjectInputStream.readObject()` with untrusted data; `XMLDecoder` with user input; `readResolve` without class validation
    - Python: `pickle.loads(user_data)`; `yaml.load()` without `Loader=yaml.SafeLoader`; `marshal.loads()` on user input
    - TypeScript/JS: `node-serialize` or similar library deserializing user data into class instances; JSON `reviver` that instantiates objects from user input
    - Go: `encoding/gob` decode of untrusted data without type assertion check
    - C#: `BinaryFormatter.Deserialize()` with untrusted input; `NetDataContractSerializer`; `LosFormatter` on user data
    - PHP: `unserialize($userInput)` without `allowed_classes` whitelist
    Fix:
    - Java/Kotlin: use JSON/XML with schema validation; if object deserialization required, apply `ObjectInputFilter` to whitelist allowed classes
    - Python: replace `pickle` with `json`; use `yaml.safe_load()` instead of `yaml.load()`
    - TypeScript/JS: avoid deserializing to class instances; use plain `JSON.parse()` with schema validation (e.g. zod/ajv)
    - Go: validate type assertions after decode; use JSON with schema validation
    - C#: replace `BinaryFormatter` with `JsonSerializer` or `XmlSerializer` with explicit known types
    - PHP: use `json_decode()` instead; if `unserialize()` required, pass `['allowed_classes' => false]` or explicit class whitelist

- ruleName : hard-coded credentials
  severity : critical
  instruction : |
    Detect:
    - All languages: non-empty string literals assigned to variables named `password`, `passwd`, `secret`, `apiKey`, `api_key`, `token`, `authToken`, `credential`, `privateKey`, `accessKey`; base64-encoded strings assigned to auth variables; PEM private key literals in source
    - Only flag when the value is a quoted string literal. Do not flag when value comes from a method call, constructor, function, or another variable.
    - Java/Kotlin: `String password = "abc123"`, `val apiKey = "sk-..."` — literal string value only
    - Python: `PASSWORD = "abc123"`, `API_KEY = "sk-..."` at module or class level — literal string value only
    - TypeScript/JS: `const password = "abc123"`, `const apiKey = "sk-..."` — literal string value only
    - Go: `password := "abc123"`, `const apiKey = "sk-..."` — literal string value only
    - C#: `string password = "abc123"`, `string apiKey = "sk-..."` — literal string value only
    - PHP: `$password = "abc123"`, `$apiKey = "sk-..."` — literal string value only
    Fix:
    - Move credential to environment variable and read at runtime: Java `System.getenv("API_KEY")`, Kotlin `System.getenv("API_KEY")`, Python `os.environ["API_KEY"]`, TypeScript/JS `process.env.API_KEY`, Go `os.Getenv("API_KEY")`, C# `Environment.GetEnvironmentVariable("API_KEY")`, PHP `getenv("API_KEY")`
    - Never commit secrets to source control; use a secrets manager (Vault, AWS Secrets Manager, etc.) for production

- ruleName : SQL injection
  severity : critical
  instruction : |
    Detect:
    - Java/Kotlin: `Statement.execute()` or `createStatement()` with string concatenation of user input; `"SELECT ... WHERE id = " + userId` or `"SELECT ... WHERE name = '" + name + "'"`
    - Python: `cursor.execute("SELECT ... WHERE x = " + userInput)` or f-string `f"SELECT ... {userInput}"` passed to execute
    - TypeScript/JS: template literal `` `SELECT * FROM users WHERE id = ${userId}` `` or string concatenation passed to raw query function
    - Go: `db.Query("SELECT ... WHERE id = " + userInput)` without placeholder
    - Rust: raw query string with user input concatenated
    - C#: `SqlCommand` constructed with `"SELECT ... WHERE id = " + userId` string concatenation
    - PHP: `mysqli_query($conn, "SELECT ... WHERE id = '$userInput'")` without prepared statement
    Fix:
    - Java/Kotlin: use `PreparedStatement` with `?` placeholders: `conn.prepareStatement("SELECT ... WHERE id = ?")` then `stmt.setInt(1, id)`
    - Python: use parameterized query: `cursor.execute("SELECT ... WHERE x = %s", (value,))`
    - TypeScript/JS: use parameterized queries with `$1`/`?` placeholders via ORM or query builder; never raw string interpolation
    - Go: use placeholder: `db.Query("SELECT ... WHERE id = ?", userInput)`
    - Rust: use query builder with bound parameters
    - C#: use `SqlParameter`: `cmd.Parameters.AddWithValue("@id", userId)`
    - PHP: use PDO prepared statements: `$stmt = $pdo->prepare("SELECT ... WHERE id = ?"); $stmt->execute([$id])`

- ruleName : cross-site scripting (XSS)
  severity : critical
  instruction : |
    Detect:
    - TypeScript/JS: `element.innerHTML = userInput`; `document.write(userInput)`; `insertAdjacentHTML('...', userInput)`; React `dangerouslySetInnerHTML={{ __html: userInput }}` with unsanitized value; `eval(userInput)`
    - Java/Kotlin: servlet/JSP writing user input directly to response: `response.getWriter().write(userInput)`; Thymeleaf `th:utext` with unsanitized user data
    - Python: Flask/Django template `{{ user_input | safe }}` or `Markup(user_input)` without prior sanitization
    - Go: `fmt.Fprintf(w, userInput)` or `w.Write([]byte(userInput))` in HTTP handler without HTML escaping; using `template/text` instead of `html/template` with user data
    - C#: `Response.Write(userInput)` without encoding; Razor `@Html.Raw(userInput)` with unsanitized data
    - PHP: `echo $userInput` or `print $userInput` without `htmlspecialchars()`
    Fix:
    - TypeScript/JS: use `element.textContent = userInput` instead of `innerHTML`; sanitize with DOMPurify before any HTML insertion; remove `dangerouslySetInnerHTML` or sanitize value first
    - Java/Kotlin: encode with `HtmlUtils.htmlEscape()` or OWASP Java Encoder `Encode.forHtml(userInput)` before writing to response
    - Python: remove `| safe` filter; rely on template auto-escaping; use `markupsafe.escape(user_input)` explicitly if needed
    - Go: use `html/template` package instead of `text/template`; use `html.EscapeString(userInput)` before writing to response
    - C#: replace `Response.Write` with `HttpUtility.HtmlEncode(userInput)`; replace `Html.Raw()` with `@Html.Encode()`
    - PHP: wrap all output: `echo htmlspecialchars($userInput, ENT_QUOTES, 'UTF-8')`

- ruleName : authentication pass by get
  severity : critical
  instruction : |
    Detect:
    - All languages/frameworks: authentication credentials, tokens, passwords, or session IDs transmitted as URL query parameters (GET params); only flag when the endpoint name, route path, or surrounding code clearly indicates authentication context (login, signin, auth, authenticate, verify-session, refresh-token). Do not flag generic query params like email-verification tokens or password-reset links in isolation.
    - Java/Kotlin: `@GetMapping` on an auth-related endpoint with `@RequestParam("password")`, `@RequestParam("sessionId")`, or `@RequestParam("token")` where the handler performs login or session validation
    - Python: `request.args.get('password')` or `request.GET.get('sessionId')` inside a view function named login, authenticate, or similar
    - TypeScript/JS: `req.query.password`, `req.query.sessionId`, `req.query.apiKey` read inside an auth/login route handler
    - Go: `r.URL.Query().Get("password")`, `r.URL.Query().Get("sessionId")` inside a handler function with auth context
    - C#: `[FromQuery] string password`, `[FromQuery] string sessionId` on a controller action with auth context
    - PHP: `$_GET['password']`, `$_GET['session_id']` used inside an authentication or session-validation block
    Fix:
    - Pass credentials via POST request body, `Authorization` header (e.g. `Bearer <token>`), or cookies with `Secure` and `HttpOnly` flags
    - Java/Kotlin: use `@RequestHeader("Authorization")` or `@RequestBody` instead of `@RequestParam`
    - Python: read from `request.headers.get('Authorization')` or `request.POST`
    - TypeScript/JS: read from `req.headers.authorization` or `req.body`
    - Go: read from `r.Header.Get("Authorization")` or parsed POST body
    - C#: use `[FromHeader] string authorization` or `[FromBody]`
    - PHP: use `$_SERVER['HTTP_AUTHORIZATION']` or `$_POST`

- ruleName : loop logic error
  severity : medium
  instruction : |
    Detect:
    - Off-by-one: `for (int i = 0; i <= array.length; i++)` accessing `array[i]` (should be `<`); `for i in range(len(arr)+1)` accessing `arr[i]`; `for (let i = 0; i <= arr.length; i++)` accessing `arr[i]`; C#: `for (int i = 0; i <= array.Length; i++)` accessing `array[i]`; PHP: `for ($i = 0; $i <= count($arr); $i++)` accessing `$arr[$i]`
    - Infinite loop: `while (true)` or `while (condition)` with no `break`, `return`, or `throw` reachable inside the loop body and loop variable not modified; do NOT flag `while (true)` if a `break`, `return`, or `throw` exists inside the body
    - Collection modified during iteration: Java/Kotlin modifying list inside enhanced for-each loop without iterator; Python modifying list while iterating over it; C#: modifying collection inside `foreach` loop
    - Go closure bug: goroutine closure capturing loop variable by reference instead of by value
    Fix:
    - Off-by-one: change `<=` to `<` for length/size-based bounds
    - Infinite loop: ensure loop variable is updated each iteration or add a valid `break`/`return`/exit condition
    - Collection modification: use `Iterator.remove()` (Java/Kotlin), iterate over a copy `for item in list(original):` (Python), `for` loop with index decrement (C#/PHP), or collect items to remove then process after loop
    - Go closure: capture loop variable by value: `v := item; go func() { use(v) }()`

- ruleName : resource leakage
  severity : medium
  instruction : |
    Detect:
    - Java/Kotlin: `InputStream`, `OutputStream`, `Connection`, `PreparedStatement`, `ResultSet`, `Socket`, `FileReader`, `FileWriter` opened but not closed in `finally` block or try-with-resources; `Closeable` opened without `.use {}` (Kotlin)
    - Python: `open()` used without `with` statement; database connection/cursor not closed after use
    - TypeScript/JS: file handles, DB connections, or streams not closed/destroyed; missing `.destroy()` or `.end()` on Node.js streams; missing `client.release()` on DB pool connections
    - Go: `os.Open()`, `http.Get()`, or DB query result not followed by `defer resource.Close()`; `http.Response.Body` not closed after read
    - Rust: `File::open()` or network resource wrapped in `ManuallyDrop` or passed to `mem::forget()` preventing drop; raw FFI resource handles not closed
    - C#: `IDisposable` resources (`SqlConnection`, `FileStream`, `HttpClient`, `StreamReader`) not wrapped in `using` statement
    - PHP: `fopen()` handle not closed with `fclose()`; database connections not explicitly closed
    Fix:
    - Java: wrap in try-with-resources: `try (InputStream is = new FileInputStream(path)) { ... }`
    - Kotlin: use `.use { }` extension: `FileInputStream(path).use { stream -> ... }`
    - Python: use context manager: `with open(path) as f:` or `with conn.cursor() as cursor:`
    - TypeScript/JS: close in `finally` block or use stream pipeline; call `client.release()` after DB pool usage
    - Go: add `defer resource.Close()` immediately after successful open: `f, err := os.Open(path); if err != nil { return err }; defer f.Close()`
    - Rust: remove `ManuallyDrop` or `mem::forget()` and let ownership drop normally; implement `Drop` trait for FFI handles
    - C#: wrap in `using`: `using (var conn = new SqlConnection(connStr)) { ... }` or `using var conn = new SqlConnection(connStr);`
    - PHP: add `fclose($handle)` in `finally` block after `fopen()`

- ruleName : empty else block with only logging
  severity : medium
  instruction : |
    Detect:
    - Java/Kotlin: `else` block whose body contains only logging calls (`log.info()`, `log.debug()`, `log.warn()`, `logger.info()`, `System.out.println()`) and no real logic
    - Python: `else:` block containing only `print()`, `logging.info()`, `logging.debug()`, `logging.warning()` with no other statements
    - TypeScript/JS: `else` block containing only `console.log()`, `console.info()`, `console.warn()`, `console.debug()` and no real logic
    - Go: `else` block containing only `log.Println()`, `log.Printf()`, `fmt.Println()` and no real logic
    - C#: `else` block containing only `Console.WriteLine()`, `_logger.LogInformation()`, `_logger.LogDebug()` and no real logic
    - PHP: `else` block containing only `error_log()`, `var_dump()`, `print_r()` and no real logic
    Fix:
    - If the log-only else is truly a no-op fallback, remove the else block entirely
    - If the log indicates an unhandled case, replace with proper handling logic (return, throw, assign) and keep the log as context inside the real handler
    - Never leave an else block that only logs without taking any action on the outcome

- ruleName : empty catch block
  severity : medium
  instruction : |
    Detect:
    - Java/Kotlin: `catch` block with empty body `catch (Exception e) {}` or containing only a comment; `catch` block with only `e.printStackTrace()` and no recovery or rethrow
    - Python: `except` clause with only `pass` or a bare comment; `except Exception: pass`
    - TypeScript/JS: `catch (e) {}` with nothing inside; `catch (e) { /* ignored */ }` with no action
    - Go: error return ignored with `_`: `result, _ := someFunc()` where the error indicates a failure condition
    - C#: `catch (Exception ex) { }` with empty body or comment only; swallowing exception without logging or rethrowing
    - PHP: `catch (Exception $e) { }` with empty body or comment only
    Fix:
    - Java/Kotlin: at minimum log the exception: `log.error("Context message", e)`; rethrow as unchecked if unrecoverable: `throw new RuntimeException(e)`; or handle with recovery logic
    - Python: at minimum log: `logging.exception("Context message")`; or re-raise: `raise`; never use bare `except: pass`
    - TypeScript/JS: at minimum log: `console.error("Context", e)`; rethrow if unrecoverable: `throw e`
    - Go: always check and handle errors; replace `_` with named variable and add `if err != nil { return err }` or log + return
    - C#: at minimum log: `_logger.LogError(ex, "Context message")`; rethrow if unrecoverable: `throw`
    - PHP: at minimum log: `error_log($e->getMessage())`; rethrow if unrecoverable: `throw $e`
