package io.github.kartoffelsup.json

import arrow.core.Tuple2
import arrow.test.generators.char
import arrow.test.generators.greaterEqual
import io.kotlintest.properties.Gen
import io.kotlintest.properties.assertAll
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class CharPredicateWrapper(val predicate: (Char) -> Boolean, val description: String) {
    override fun toString(): String = description
}

object CharPredicateGen : Gen<CharPredicateWrapper> {
    override fun constants(): Iterable<CharPredicateWrapper> {
        return listOf(
            CharPredicateWrapper(Char::isDigit, "Char::isDigit"),
            CharPredicateWrapper(Char::isWhitespace, "Char::isWhitespace"),
            CharPredicateWrapper(Char::isLetter, "Char::isLetter"),
            CharPredicateWrapper(Char::isLetterOrDigit, "Char::isLetterOrDigit"),
            CharPredicateWrapper(Char::isUpperCase, "Char::isUpperCase"),
            CharPredicateWrapper(Char::isLowerCase, "Char::isLowerCase"),
            CharPredicateWrapper({ a: Char -> a == 'a' }, "{ a: Char -> a == 'a' }"),
            CharPredicateWrapper({ a: Char -> a == 'b' }, "{ a: Char -> a == 'b' }"),
            CharPredicateWrapper({ a: Char -> a == 'c' }, "{ a: Char -> a == 'c'}")
        )
    }

    override fun random(): Sequence<CharPredicateWrapper> {
        return generateSequence {
            val char = Gen.char().random().first()
            CharPredicateWrapper({ c: Char -> c == char }, "c: Char -> c == 'rnd $char'")
        }
    }
}

internal class StringViewTest : StringSpec({
    "span non empty" {
        val input = "      b   "
        val p = Char::isWhitespace
        val actual: Tuple2<StringView, StringView> = StringView.from(input).span(p)
        val expected = input.span(p)
        actual.a.value shouldBe expected.a
        actual.b.value shouldBe expected.b
    }

    "Gen span non empty" {
        assertAll(Gen.string(), CharPredicateGen) { a: String, p: CharPredicateWrapper ->
            val actual: Tuple2<StringView, StringView> = StringView.from(a).span(p.predicate)
            val expected: Tuple2<String, String> = a.span(p.predicate)
            actual.a.value shouldBe expected.a
            actual.b.value shouldBe expected.b
        }
    }

    "Gen nested span non empty" {
        assertAll(
            Gen.string(),
            CharPredicateGen,
            CharPredicateGen
        ) { input: String, first: CharPredicateWrapper, second: CharPredicateWrapper ->
            val actual: Tuple2<StringView, StringView> =
                StringView.from(input).span(first.predicate).flatMap { it.span(second.predicate) }
            val expected: Tuple2<String, String> = input.span(first.predicate).flatMap { it.span(second.predicate) }
            actual.a.value shouldBe expected.a
            actual.b.value shouldBe expected.b
        }
    }

    "span empty" {
        val result: Tuple2<StringView, StringView> = StringView.from("").span(Char::isWhitespace)
        result.a.value shouldBe ""
        result.b.value shouldBe ""
    }

    "isNotEmpty" {
        StringView.from("a").isEmpty() shouldBe false
        assertAll { a: String ->
            StringView.from(a).isNotEmpty() shouldBe a.isNotEmpty()
        }
    }

    "isEmpty" {
        StringView.from("").isEmpty() shouldBe true
        assertAll { a: String ->
            StringView.from(a).isEmpty() shouldBe a.isEmpty()
        }
    }

    "length" {
        assertAll { a: String ->
            StringView.from(a).length() shouldBe a.length
        }
    }

    "drop" {
        assertAll(Gen.string(), Gen.greaterEqual(0)) { a: String, b: Int ->
            StringView.from(a).drop(b).value shouldBe a.drop(b)
            StringView.from(a).drop(b).drop(b).value shouldBe a.drop(b).drop(b)
        }
    }

    "get" {
        assertAll { a: String, b: Int ->
            val maxIndex = a.length - 1
            val index = if (b > 0) maxIndex % b else maxIndex
            if (index != -1) {
                StringView.from(a)[index] shouldBe a[index]
            }
        }
    }
})
