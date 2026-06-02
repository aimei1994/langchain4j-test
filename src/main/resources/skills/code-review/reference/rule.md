# Code Review Rules

- ruleName : null pointer exception
  severity : low
  instruction : |
    Detect all dereferences of potentially null references where no null-check guards the operation.
    Apply using the null concept of the detected language:
    - Java/Kotlin: unguarded `obj.method()` or `obj.field` where obj may be null; `Map.get()` result used without null check; `Optional.get()` without `isPresent()`; chained calls `a.getB().getC()` where any link may be null; `!!` on nullable Kotlin type
    - Python: calling methods or accessing attributes on values that may be `None`; `dict.get()` result used without `is None` guard
    - TypeScript/JS: property access on `T | null | undefined` without type guard or `?.`; missing `??` guard; array index access without bounds/undefined check
    - Go: pointer or interface used without nil check after a function that may return nil
    - Rust: `Option.unwrap()` or `expect()` without prior `is_some()` guard or match arm
    - C#: `.Value` on `Nullable<T>` without `.HasValue`; missing `?.` or `??`
    - PHP: method call on variable that may be `null` without null check

- ruleName : empty if else block
  severity : major
  instruction : |
    Detect if/else-if/else blocks whose body contains no statements. Apply using the block syntax of the detected language:
    - Java/Kotlin/TypeScript/JS/Go/Rust/C#/PHP: `if (cond) {}`, `else {}`, `else if (cond) {}` with nothing between braces
    - Python: `if cond:` / `elif cond:` / `else:` block containing only `pass` or a bare comment with no real logic
    Whitespace and comments do not count as statements.
