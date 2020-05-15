// Auto-generated by GenerateSteppedRangesCodegenTestData. Do not edit!
// IGNORE_BACKEND: JVM_IR
// IGNORE_BACKEND_FIR: JVM_IR
// KJS_WITH_FULL_RUNTIME
// WITH_RUNTIME
import kotlin.test.*

fun box(): String {
    val uintList = mutableListOf<UInt>()
    val uintProgression = 7u downTo 1u
    for (i in uintProgression step 7) {
        uintList += i
    }
    assertEquals(listOf(7u), uintList)

    val ulongList = mutableListOf<ULong>()
    val ulongProgression = 7uL downTo 1uL
    for (i in ulongProgression step 7L) {
        ulongList += i
    }
    assertEquals(listOf(7uL), ulongList)

    return "OK"
}