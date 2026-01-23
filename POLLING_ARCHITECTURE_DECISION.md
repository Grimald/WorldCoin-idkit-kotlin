# 🏗️ Polling Architecture Decision

## 📋 Table of Contents
1. [Background](#background)
2. [Current Implementation](#current-implementation)
3. [Architecture Options](#architecture-options)
4. [Final Recommendations](#final-recommendations)

---

## 🔍 Background

### The Issue:

After the app transitions to background, Android 15+ restricts network access for apps, causing `UnknownHostException` errors during polling. This is documented behavior in Android 15's [background network access restrictions](https://developer.android.com/about/versions/15/behavior-changes-all#background-network-access).

**Current SDK behavior:**
- ✅ Continues polling on `IOException` (including DNS failures)
- ✅ Allows verification to complete even when app is in background
- ✅ User can cancel via coroutine cancellation if needed

---

## 📊 Current Implementation

### API:
```kotlin
interface Session {
    val connectUrl: URL
    fun status(): Flow<Status>
}
```

### Implementation:
```kotlin
override fun status(): Flow<Status> = flow {
    var currentStatus: Status = Status.WaitingForConnection
    emit(currentStatus)

    while (true) {
        try {
            val bridgeResponse = NetworkClient.queryRequest(requestID, bridgeURL)
            val newStatus = handleBridgeResponse(bridgeResponse)
            
            if (newStatus != currentStatus) {
                currentStatus = newStatus
                emit(newStatus)
            }
            
            // Stop polling on terminal statuses
            if (newStatus is Status.Failed || newStatus is Status.Confirmed) {
                break
            }

            delay(config.intervalMs)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            // ✅ SOLUTION: Continue polling on IOException
            logWarning(message = "Network error, will continue polling: ${ex.message}")
            delay(config.intervalMs)
            continue
        } catch (ex: Exception) {
            logWarning(message = "Something went wrong: $ex")
            val error = when (ex) {
                is AppErrorThrowable -> ex.appError
                else -> AppError.GenericError(ex.message)
            }
            emit(Status.Failed(error))
            break
        }
    }
}.flowOn(Dispatchers.IO)
```

### Behavior:

| Event | Action |
|---------|----------|
| `collect()` called | ✅ Polling starts |
| `cancel()` called | ✅ Polling stops |
| `Status.Confirmed` | ✅ Polling stops (break) |
| `Status.Failed` | ✅ Polling stops (break) |
| `IOException` | ✅ Polling continues (continue) |
| No subscribers | ✅ Polling does NOT run |

### Pros of current implementation:

- ✅ **Simple API** - standard Kotlin Flow
- ✅ **Automatic lifecycle** - polling managed via `collect()`/`cancel()`
- ✅ **Cold Flow** - polling doesn't run without subscribers (saves resources)
- ✅ **Continues polling on IOException** - gets result even with DNS issues
- ✅ **Works with `repeatOnLifecycle`** - perfect integration with Android Lifecycle

### Cons of current implementation:

- ❌ **Cannot resubscribe** - each `status()` creates a new Flow
- ❌ **Cannot get current status** without subscription
- ❌ **Cannot change polling interval** - hardcoded 3 seconds
- ❌ **No explicit control** over polling (stop/resume)

---

## 🏗️ Architecture Options

### Option 1: Current (`Flow`) - Minimal Changes

#### Changes:
```kotlin
// In IDKit.kt
fun createSession(
    appID: AppID,
    action: String,
    signal: String = "",
    verificationLevel: VerificationLevel = VerificationLevel.ORB,
    bridgeURL: BridgeURL = BridgeURL.default,
    actionDescription: String? = null,
    pollingIntervalMs: Long = 3000L  // ✅ New optional parameter
): Session
```

#### API remains the same:
```kotlin
val session = IDKit.createSession(
    appID = appID,
    action = "verify",
    pollingIntervalMs = 5000L  // ✅ Optional
)

viewLifecycleOwner.lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        session.status().collect { status ->
            // Handle status
        }
    }
}
```

#### Pros:
- ✅ **No breaking changes**
- ✅ **Simple implementation**
- ✅ **Works as it does now**
- ✅ **Adds flexibility** (configurable interval)

#### Cons:
- ❌ All cons of current implementation remain
- ❌ Cannot resubscribe
- ❌ No explicit control

#### Recommendation: ⭐⭐⭐ (if we want minimal changes)

---

### Option 2: `StateFlow` - Hot Flow with automatic start

#### Changes:
```kotlin
interface Session {
    val connectUrl: URL
    val status: StateFlow<Status>  // ✅ Hot flow instead of function
    
    fun stopPolling()
    fun resumePolling()
    fun close()  // Cleanup
}

internal class DefaultSession(...) : Session {
    
    private val sessionScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )
    
    private val _status = MutableStateFlow<Status>(Status.WaitingForConnection)
    override val status: StateFlow<Status> = _status.asStateFlow()
    
    private var pollingJob: Job? = null
    
    init {
        startPolling()  // ✅ Automatically on creation
    }
    
    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        
        pollingJob = sessionScope.launch {
            var currentStatus: Status = Status.WaitingForConnection
            _status.value = currentStatus

            while (isActive) {  // ✅ Respects cancellation
                try {
                    val bridgeResponse = NetworkClient.queryRequest(requestID, bridgeURL)
                    val newStatus = handleBridgeResponse(bridgeResponse)
                    
                    if (newStatus != currentStatus) {
                        currentStatus = newStatus
                        _status.value = newStatus
                    }
                    
                    if (newStatus is Status.Failed || newStatus is Status.Confirmed) {
                        break
                    }

                    delay(config.intervalMs)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: IOException) {
                    logWarning(message = "Network error, will continue polling: ${ex.message}")
                    delay(config.intervalMs)
                    continue
                } catch (ex: Exception) {
                    logWarning(message = "Something went wrong: $ex")
                    val error = when (ex) {
                        is AppErrorThrowable -> ex.appError
                        else -> AppError.GenericError(ex.message)
                    }
                    _status.value = Status.Failed(error)
                    break
                }
            }
        }
    }
    
    override fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }
    
    override fun resumePolling() {
        startPolling()
    }
    
    override fun close() {
        sessionScope.cancel()
    }
}
```

#### Usage:

**Simple case (automatic lifecycle):**
```kotlin
val session = IDKit.createSession(...)  // ✅ Polling already started

viewLifecycleOwner.lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        session.status.collect { status ->
            // ✅ Polling continues in background
            // ✅ UI updates only when visible
        }
    }
}
```

**Advanced case (manual control):**
```kotlin
class YourViewModel : ViewModel() {
    private var session: Session? = null
    
    fun startVerification() {
        session = IDKit.createSession(...)  // ✅ Polling started
    }
    
    fun onAppGoesToBackground() {
        session?.stopPolling()  // Save battery
    }
    
    fun onAppReturnsToForeground() {
        session?.resumePolling()  // Resume
        
        // ✅ Get current status immediately
        val currentStatus = session?.status?.value
    }
    
    override fun onCleared() {
        session?.close()  // Cleanup
        super.onCleared()
    }
}
```

**Resubscription:**
```kotlin
val session = IDKit.createSession(...)

// 1. First subscription
val job1 = lifecycleScope.launch {
    session.status.collect { status -> /* ... */ }
}

// App goes to background
job1.cancel()  // ✅ Polling continues!

// App returns to foreground
val job2 = lifecycleScope.launch {
    session.status.collect { status ->
        // ✅ Get CURRENT status and all subsequent ones!
    }
}
```

#### Pros:
- ✅ **Can resubscribe** - hot flow
- ✅ **Current status available** via `.value`
- ✅ **Explicit control** - `stopPolling()` / `resumePolling()`
- ✅ **Polling in background** - continues without subscribers
- ✅ **Idiomatic Kotlin** - standard StateFlow
- ✅ **Better for ViewModel** - can hold status in memory

#### Cons:
- ⚠️ **Breaking change** - API changes
- ⚠️ **More methods** - `stopPolling()`, `resumePolling()`, `close()`
- ⚠️ **Requires cleanup** - must explicitly call `close()`
- ⚠️ **Polling always runs** - even without subscribers (consumes resources)

#### Recommendation: ⭐⭐⭐⭐⭐ (if ready for breaking change)

---

### Option 3: Hybrid - `Flow` + `SharedFlow` for resubscription

#### Idea:
Use `SharedFlow` internally, but keep API as `Flow`:

```kotlin
internal class DefaultSession(...) : Session {
    
    private val _statusFlow = MutableSharedFlow<Status>(
        replay = 1,  // Keep last value
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    
    private val pollingJob = CoroutineScope(Dispatchers.IO).launch {
        // ... polling logic, emit to _statusFlow ...
    }
    
    override fun status(): Flow<Status> = _statusFlow.asSharedFlow()
}
```

#### Pros:
- ✅ **Can resubscribe**
- ✅ **No breaking changes** (same API)
- ✅ **Get last status** on subscription

#### Cons:
- ❌ **Polling always runs** (even without subscribers)
- ❌ **No control** (stop/resume)
- ❌ **Requires cleanup**
- ❌ **Harder to understand behavior**

#### Recommendation: ⭐⭐ (not recommended - worst of both worlds)

---

## ✅ Final Recommendations

### Option 1: Minimal Changes (no breaking changes)

#### What to do:
1. ✅ Keep `Flow` API
2. ✅ Add `pollingIntervalMs` parameter
3. ✅ Keep `continue` on `IOException`
4. ✅ Document usage with `repeatOnLifecycle`

#### Code:
```kotlin
// SDK API (minimal changes)
fun createSession(
    appID: AppID,
    action: String,
    pollingIntervalMs: Long = 3000L  // ✅ New parameter
): Session

// Usage
viewLifecycleOwner.lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        session.status().collect { status ->
            // Handle status
        }
    }
}
```

#### Pros:
- ✅ No breaking changes
- ✅ Simple implementation
- ✅ Works for most cases
- ✅ Flexibility through parameters

#### Cons:
- ❌ Cannot resubscribe
- ❌ No explicit control

#### Suitable for:
- ✅ Current SDK version (v4.x)
- ✅ Quick release
- ✅ Minimal risk

---

### Option 2: Full Refactoring (breaking change for v5.0)

#### What to do:
1. ✅ Change to `StateFlow` API
2. ✅ Add `stopPolling()` / `resumePolling()` / `close()`
3. ✅ Polling starts in `init`
4. ✅ Add `pollingIntervalMs` parameter
5. ✅ Update documentation

#### Code:
```kotlin
// SDK API (new in v5.0)
interface Session : AutoCloseable {
    val connectUrl: URL
    val status: StateFlow<Status>
    
    fun stopPolling()
    fun resumePolling()
    override fun close()  // Cleanup
}

// Usage (simple)
val session = IDKit.createSession(...)

viewLifecycleOwner.lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        session.status.collect { status ->
            // ✅ Polling continues in background
            // ✅ Can resubscribe
        }
    }
}

// Usage (advanced)
class YourViewModel : ViewModel() {
    private var session: Session? = null
    
    fun startVerification() {
        session = IDKit.createSession(...)
    }
    
    fun onAppGoesToBackground() {
        session?.stopPolling()
    }
    
    fun onAppReturnsToForeground() {
        session?.resumePolling()
        val currentStatus = session?.status?.value  // ✅
    }
    
    override fun onCleared() {
        session?.close()
        super.onCleared()
    }
}
```

#### Pros:
- ✅ Can resubscribe
- ✅ Explicit control
- ✅ Current status available
- ✅ Better for complex use cases
- ✅ Modern API

#### Cons:
- ⚠️ Breaking change
- ⚠️ Larger API surface
- ⚠️ Requires cleanup

#### Suitable for:
- ✅ Major version (v5.0)
- ✅ Long-term perspective
- ✅ Complex applications

---

## 📝 Migration Path

### Phase 1: v4.1 (minimal changes)
- ✅ Add `pollingIntervalMs` parameter
- ✅ Keep `Flow` API
- ✅ Update documentation

### Phase 2: v4.2 (deprecation)
- ⚠️ Deprecate `status(): Flow<Status>`
- ⚠️ Add `statusFlow: StateFlow<Status>` as experimental
- ⚠️ Give users time to migrate

### Phase 3: v5.0 (breaking change)
- ✅ Remove `status(): Flow<Status>`
- ✅ Make `statusFlow: StateFlow<Status>` stable
- ✅ Add control methods

---

## 🎯 Final Recommendation

### For immediate release:
**Option 1 (minimal changes)** ⭐⭐⭐

### For long-term perspective:
**Option 2 (StateFlow)** ⭐⭐⭐⭐⭐

### Plan:
1. **Now (v4.1):** Minimal changes
2. **In 3-6 months (v4.2):** Add StateFlow as experimental
3. **In a year (v5.0):** Full transition to StateFlow

---

## 📚 Additional Resources

### Lifecycle documentation:
- [Lifecycle-aware components](https://developer.android.com/topic/libraries/architecture/lifecycle)
- [repeatOnLifecycle](https://developer.android.com/topic/libraries/architecture/coroutines#lifecycle-aware)
- [Android 15 Background Network Access Restrictions](https://developer.android.com/about/versions/15/behavior-changes-all#background-network-access)

### Kotlin Flow:
- [Cold vs Hot Flows](https://kotlinlang.org/docs/flow.html#flows-are-cold)
- [StateFlow](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/)
- [SharedFlow](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-shared-flow/)

---

**Created:** 2025-01-23  
**SDK Version:** 4.0.0-SNAPSHOT  
**Status:** Decision Pending
