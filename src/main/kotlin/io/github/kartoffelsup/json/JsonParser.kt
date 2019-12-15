package io.github.kartoffelsup.json

import arrow.Kind
import arrow.core.ListK
import arrow.core.NonEmptyList
import arrow.core.None
import arrow.core.Option
import arrow.core.SequenceK
import arrow.core.Some
import arrow.core.Tuple2
import arrow.core.extensions.fx
import arrow.core.k

// typealias Parser<T> = (String) -> Option<Tuple2<String, T>>

interface Parser<out A> : ParserOf<A> {
    val runParser: (String) -> Option<Tuple2<String, A>>

    fun <B> map(f: (A) -> B): Parser<B> =
        Parser { x: String ->
            runParser(x).map { t ->
                t.map(f)
            }
        }

    fun <B> ap(ff: ParserOf<(A) -> B>): Parser<B> = Parser { input ->
        Option.fx {
            val (input2: String, f: (A) -> B) = ff.fix().runParser(input).bind()
            val (input3: String, g: A) = fix().runParser(input2).bind()
            Tuple2(input3, f(g))
        }
    }

    companion object {
        operator fun <T> invoke(parser: (String) -> Option<Tuple2<String, T>>): Parser<T> = object : Parser<T> {
            override val runParser: (String) -> Option<Tuple2<String, T>> = parser
        }

        fun <A> just(a: A): Parser<A> = Parser { input ->
            Some(Tuple2(input, a))
        }
    }
}

private fun charParser(char: Char): Parser<Char> =
    Parser { x: String ->
        NonEmptyList.fromList(x.toList())
            .filter { it.head == char }
            .map { Tuple2(it.tail.s(), it.head) }
    }

private fun stringParser(string: String): Parser<String> {
    val traverse: Kind<ForParser, ListK<Char>> =
        string.toList().map(::charParser).k().traverse(ParserApplicativeInstance) { it }
    return traverse.fix().map {
        it.s()
    }
}

private fun spanParser(p: (Char) -> Boolean): Parser<String> = Parser { input ->
    val (xs, input2) = input.toList().k().partition(p)
    if (xs.isEmpty()) {
        None
    } else {
        Some(Tuple2(input2.s(), xs.s()))
    }
}

fun jsonNull(): Parser<JsonValue> =
    stringParser("null").map { JsonNull }

fun jsonBool(): Parser<JsonValue> = ParserAlternativeInstance.run {
    stringParser("true") alt stringParser("false")
}.fix().map { f: String ->
    when (f) {
        "true" -> JsonBool(true)
        "false" -> JsonBool(false)
        else -> TODO("this should never happen ;)")
    }
}

fun jsonNumber(): Parser<JsonValue> = spanParser(Char::isDigit).map { JsonNumber(it.toInt()) }

// TODO: escaping
fun stringLiteral(): Parser<String> = spanParser { it != '"' }

fun jsonString(): Parser<JsonValue> = ParserAlternativeInstance.run {
    val tmp: Parser<String> = charParser('"').rightWins(stringLiteral()).leftWins(charParser('"'))
    tmp.map { JsonString(it) }
}

fun whiteSpace(): Parser<String> = spanParser { it.isWhitespace() }

fun <A, B> sepBy(sep: Parser<A>, element: Parser<B>): Parser<List<B>> = ParserAlternativeInstance.run {
    val elementAsSeq: Parser<SequenceK<B>> = element.map { sequenceOf(it).k() }
    val rw: Parser<SequenceK<B>> = sep.rightWins(element).many().fix()

    val cons: Parser<(SequenceK<B>) -> SequenceK<B>> = elementAsSeq.map { a: SequenceK<B> -> { b: SequenceK<B> -> (a + b).k() } }
    val result: Parser<SequenceK<B>> = rw.ap(cons).fix()
    val just: Parser<SequenceK<B>> = just(emptySequence<B>().k()).fix()
    (result alt just).map { it.toList() }.fix()
}

fun jsonArray(): Parser<JsonValue> = ParserAlternativeInstance.run {
    val sep = whiteSpace().rightWins(charParser(',')).leftWins(whiteSpace())
    val elements: Parser<List<JsonValue>> = sepBy(sep, jsonValue())
    val rightWins: Parser<List<JsonValue>> = charParser('[').rightWins(whiteSpace()).rightWins(elements)
    val result: Parser<List<JsonValue>> = rightWins.leftWins(whiteSpace()).leftWins(charParser(']'))
    result.map { JsonArray(it) }
}

fun jsonValue(): Parser<JsonValue> = ParserAlternativeInstance.run {
    jsonNull() alt jsonBool() alt jsonNumber() alt jsonString() alt jsonArray()
}.fix()

fun main() {
//    println(stringParser("hello").runParser("hellothere"))
//    println(jsonBool().runParser("true"))
//    println(jsonBool().runParser("false"))
//    println(jsonNumber().runParser(""))
//    println(jsonString().runParser("\"testing\""))
//    println(jsonNull().runParser("nullnull"))

    println(sepBy(charParser(','), charParser('a')).runParser("a,a,a,a"))
    println(sepBy(charParser(','), charParser('a')).runParser(""))

    // TODO stackoverflows :(
//    println(jsonArray().runParser("[]"))
//    println(jsonArray().runParser("[1, \"asdf\", true, null]"))
}