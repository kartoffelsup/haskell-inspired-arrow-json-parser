package io.github.kartoffelsup.json

import arrow.core.None
import arrow.core.Option
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
            val parsedChar: Option<Tuple2<StringView, Char>> = charParser(first).runParser(StringView.from(str))
            parsedChar.map { it.b } shouldBe Some(first)
        }
    }

    "maybe" {
        assertAll(Gen.nonEmptyString()) { str: String ->
            val first: Char = str.first()
            val second: Char? = str.drop(1).firstOrNull()
            val stringView = StringView.from(str)
            val someMaybe: Option<Tuple2<StringView, Option<Char>>> = maybe(first).runParser(stringView)
            someMaybe.map { it.b } shouldBe Some(Some(first))
            if (second != null && first != second) {
                val noneMaybe = maybe(second).runParser(stringView)
                noneMaybe.map { it.b } shouldBe Some(None)
            }
        }
    }

    "spanParser" {
        assertAll(Gen.string(), CharPredicateGen) { str: String, predWrapper: CharPredicateWrapper ->
            val parsedString: Option<Tuple2<StringView, StringView>> =
                spanParser(predWrapper.predicate).runParser(StringView.from(str))
            val expected: Tuple2<String, String> = str.span(predWrapper.predicate).reverse()
            parsedString.map { it.a.value toT it.b.value } shouldBe Some(expected)
        }
    }

    "stringParser" {
        assertAll { str: String ->
            val parsedString: Option<Tuple2<StringView, String>> = stringParser(str).runParser(StringView.from(str))
            parsedString.map { it.b } shouldBe Some(str)
        }
    }

    "jsonNull" {
        val jsonNull = jsonNull().runParser(StringView.from("null"))
        jsonNull.map { it.b } shouldBe Some(JsonNull)
    }

    "whitespace" {
        val jsonNull = whiteSpace().runParser(StringView.from(" \n \r\n \r \t"))
        jsonNull.map { it.a.value toT it.b.value } shouldBe Some(Tuple2("", " \n \r\n \r \t"))
    }

    "jsonBool" {
        val boolTrue = jsonBool().runParser(StringView.from("true"))
        val boolFalse = jsonBool().runParser(StringView.from("false"))
        val notABool = jsonBool().runParser(StringView.from("notABool"))
        boolTrue.map { it.b } shouldBe Some(JsonBool(true))
        boolFalse.map { it.b } shouldBe Some(JsonBool(false))
        notABool.map { it.b } shouldBe None
    }

    "notEmpty" {
        assertAll { input: String ->
            val inputView = StringView.from(input)
            val notEmpty: Option<Tuple2<StringView, StringView>> = notEmpty(spanParser { true }).runParser(inputView)
            val actual: Option<StringView> = notEmpty.map { it.b }
            val expected = if (input.isEmpty()) None else Some(inputView)
            actual shouldBe expected
        }
    }

    "jsonNumber" {
        assertAll { number: Int ->
            val jsonValue: Option<Tuple2<StringView, JsonValue>> =
                jsonNumber().runParser(StringView.from(number.toString()))
            jsonValue.map { it.b } shouldBe Some(JsonNumber(number))
        }
    }
})
