package utils

import kotlinx.coroutines.*
import kotlin.time.Duration


fun CoroutineScope.Timer(
    duration: Duration,
    dispatcher: CoroutineDispatcher,
    run: () -> Unit,
): Job {
    return this.launch(dispatcher) {
        delay(duration)
        withContext(dispatcher) { run() }
    }
}
