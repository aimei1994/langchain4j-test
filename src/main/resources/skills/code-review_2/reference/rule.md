# Code Review Rules

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
