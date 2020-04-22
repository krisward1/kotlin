// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_VARIABLE -ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE -UNUSED_VALUE -UNUSED_PARAMETER -UNUSED_EXPRESSION
// SKIP_TXT

/*
 * KOTLIN DIAGNOSTICS SPEC TEST (POSITIVE)
 *
 * SPEC VERSION: 0.1-366
 * PLACE: overload-resolution, choosing-the-most-specific-candidate-from-the-overload-candidate-set, algorithm-of-msc-selection -> paragraph 9 -> sentence 8
 * NUMBER: 1
 * DESCRIPTION: The candidate having any variable-argument parameters is less specific
 */

// TESTCASE NUMBER: 1
class Case1() {
    fun foo(y: Int, x: Int): String = TODO() // (1.1)
    fun foo(vararg x: Int): Unit = TODO() // (1.2)

    fun case(){
        <!DEBUG_INFO_CALL("fqName: Case1.foo; typeCall: function")!>foo(1, 1)<!>
        <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.String")!>foo(1, 1)<!>
    }
}
// TESTCASE NUMBER: 2
class Case2() {
    fun foo(y: Any?, x: Any?): String = TODO() // (1.1)
    fun foo(vararg x: Any?): Unit = TODO() // (1.2)

    fun case(){
        <!DEBUG_INFO_CALL("fqName: Case2.foo; typeCall: function")!>foo(1, 1)<!>
        <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.String")!>foo(1, 1)<!>
    }
}