package de.connect2x.trixnity.utils

data class AtomicUpdateAndRunResult<R, T>(val result: R, val update: T)

/**
 * Executes a transformation on a value atomically using a Compare-And-Swap (CAS) loop.
 *
 * @param getValue A function to retrieve the current value.
 * @param updateValue A function to perform the atomic update.
 * @param update A suspend function that takes the current value and returns an [AtomicUpdateAndRunResult] containing
 *   the new value and the intended return value.
 * @return The result of type [R] produced by the [update] function.
 */
inline fun <R, T> atomicUpdateAndRun(
    getValue: () -> T,
    updateValue: ((T) -> T) -> Unit,
    update: (T) -> AtomicUpdateAndRunResult<R, T>,
): R {
    while (true) {
        val prevValue = getValue()
        val nextValue = update(prevValue)
        var retry = false
        updateValue { currentValue ->
            if (currentValue == prevValue) nextValue.update
            else {
                retry = true
                currentValue
            }
        }
        if (!retry) {
            return nextValue.result
        }
    }
}

/**
 * Performs an atomic update on a value and returns the result of the transformation.
 *
 * This is a convenience wrapper around [atomicUpdateAndRun] where the result is [Unit].
 */
inline fun <T> atomicUpdate(getValue: () -> T, updateValue: ((T) -> T) -> Unit, update: (T) -> T) =
    atomicUpdateAndRun(getValue = getValue, updateValue = updateValue) { AtomicUpdateAndRunResult(Unit, update(it)) }
