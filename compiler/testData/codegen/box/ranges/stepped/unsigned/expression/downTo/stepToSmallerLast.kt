// Auto-generated by GenerateSteppedRangesCodegenTestData. Do not edit!
// IGNORE_BACKEND: JVM_IR
// IGNORE_BACKEND_FIR: JVM_IR
// KJS_WITH_FULL_RUNTIME
// WITH_RUNTIME
import kotlin.test.*

fun box(): String {
    val uintList = mutableListOf<UInt>()
    val uintProgression = 8u downTo 1u
    for (i in uintProgression step 2) {
        uintList += i
    }
    assertEquals(listOf(8u, 6u, 4u, 2u), uintList)

    val ulongList = mutableListOf<ULong>()
    val ulongProgression = 8uL downTo 1uL
    for (i in ulongProgression step 2L) {
        ulongList += i
    }
    assertEquals(listOf(8uL, 6uL, 4uL, 2uL), ulongList)

    return "OK"
}