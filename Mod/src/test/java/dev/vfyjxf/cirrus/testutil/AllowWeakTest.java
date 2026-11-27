package dev.vfyjxf.cirrus.testutil;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares, visibly and in English, why one test cannot use a regular assertion.
 *
 * <p>It exempts the annotated method or class from the named rules of
 * config/pmd/cloud-test-ruleset.xml, but only when the reason is at least twenty whitespace-separated
 * English words written in printable ASCII and the rules list names the exemptions. A declaration
 * that misses that bar is itself reported by TestAllowWeakTestMisuse, and AllowWeakTestAuditTest
 * refuses an empty rules list, an unknown rule id, and a use count above its budget.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface AllowWeakTest {

    /** Why this test must read source text, assert on rendered text, or otherwise stay weak. */
    String reason();

    /** The rule ids this exemption covers; name them, an empty list exempts everything. */
    String[] rules() default {};
}
