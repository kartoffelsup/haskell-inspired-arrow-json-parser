package io.github.kartoffelsup.json

import arrow.core.None
import arrow.core.Some
import arrow.core.Tuple2
import arrow.core.toT
import arrow.test.generators.nonEmptyString
import io.kotlintest.properties.Gen
import io.kotlintest.properties.assertAll
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class ParserTest : StringSpec({
    "charParser" {
        assertAll(Gen.nonEmptyString()) { str: String ->
            val first: Char = str.first()
            val parsedChar: Tuple2<StringView, Char>? = charParser(first).runParser(StringView.from(str))
            parsedChar?.b shouldBe first
        }
    }

    "maybe" {
        assertAll(Gen.nonEmptyString()) { str: String ->
            val first: Char = str.first()
            val second: Char? = str.drop(1).firstOrNull()
            val stringView = StringView.from(str)
            val someMaybe: Tuple2<StringView, Char?>? = maybe(first).runParser(stringView)
            someMaybe?.b shouldBe first
            if (second != null && first != second) {
                val noneMaybe = maybe(second).runParser(stringView)
                noneMaybe?.b shouldBe null
            }
        }
    }

    "spanParser" {
        assertAll(Gen.string(), CharPredicateGen) { str: String, predWrapper: CharPredicateWrapper ->
            val parsedString: Tuple2<StringView, StringView>? =
                spanParser(predWrapper.predicate).runParser(StringView.from(str))
            val expected: Tuple2<String, String> = str.span(predWrapper.predicate).reverse()
            parsedString?.let { it.a.value toT it.b.value } shouldBe expected
        }
    }

    "stringParser" {
        assertAll { str: String ->
            val parsedString: Tuple2<StringView, String>? = stringParser(str).runParser(StringView.from(str))
            parsedString?.b shouldBe str
        }
    }

    "jsonNull" {
        val jsonNull = jsonNull.runParser(StringView.from("null"))
        jsonNull?.b shouldBe JsonNull
    }

    "whitespace" {
        val jsonNull = whiteSpace.runParser(StringView.from(" \n \r\n \r \t"))
        jsonNull?.let { it.a.value toT it.b.value } shouldBe Tuple2("", " \n \r\n \r \t")
    }

    "jsonBool" {
        val boolTrue = jsonBool.runParser(StringView.from("true"))
        val boolFalse = jsonBool.runParser(StringView.from("false"))
        val notABool = jsonBool.runParser(StringView.from("notABool"))
        boolTrue?.b shouldBe JsonBool(true)
        boolFalse?.b shouldBe JsonBool(false)
        notABool?.b shouldBe null
    }

    "notEmpty" {
        assertAll { input: String ->
            val inputView = StringView.from(input)
            val notEmpty: Tuple2<StringView, StringView>? = notEmpty(spanParser { true }).runParser(inputView)
            val actual: StringView? = notEmpty?.b
            val expected = if (input.isEmpty()) null else inputView
            actual shouldBe expected
        }
    }

    "jsonNumber" {
        assertAll { number: Int ->
            val jsonValue: Tuple2<StringView, JsonValue>? = jsonNumber.runParser(StringView.from(number.toString()))
            jsonValue?.b shouldBe JsonNumber(number)
        }
    }
})
