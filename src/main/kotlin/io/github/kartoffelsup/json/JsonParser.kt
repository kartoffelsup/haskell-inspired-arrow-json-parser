package io.github.kartoffelsup.json

import arrow.Kind
import arrow.core.ForListK
import arrow.core.NonEmptyList
import arrow.core.None
import arrow.core.Option
import arrow.core.SequenceK
import arrow.core.Some
import arrow.core.Tuple2
import arrow.core.extensions.fx
import arrow.core.extensions.list.traverse.sequence
import arrow.core.fix
import arrow.core.k
import arrow.core.toT
import io.github.kartoffelsup.json.ParserAlternativeInstance.leftWins
import io.github.kartoffelsup.json.ParserAlternativeInstance.rightWins

// typealias Parser<T> = (String) -> Option<Tuple2<String, T>>

interface Parser<out A> : ParserOf<A> {
    // TODO: no proper error reporting
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
            .map { it.tail.s() toT it.head }
    }

private fun stringParser(string: String): Parser<String> {
    val parser: Parser<Kind<ForListK, Char>> = string.toList()
        .map(::charParser).k()
        .sequence(ParserApplicativeInstance).fix()

    return parser.map { it.fix().s() }
}

private fun spanParser(p: (Char) -> Boolean): Parser<String> = Parser { input ->
    val (xs, input2) = input.toList().span(p)
    Some(input2.s() toT xs.s())
}

fun jsonNull(): Parser<JsonValue> =
    stringParser("null").map { JsonNull }

fun jsonBool(): Parser<JsonValue> = ParserAlternativeInstance.run {
    stringParser("true").map { JsonBool(true) } alt stringParser("false").map { JsonBool(false) }
}.fix()

fun notEmpty(p: Parser<String>): Parser<String> = Parser { input ->
    val runParser = p.runParser(input)
    runParser.flatMap { tuple ->
        if (tuple.b.isEmpty()) {
            None
        } else {
            Some(tuple)
        }
    }
}

fun jsonNumber(): Parser<JsonValue> = notEmpty(spanParser(Char::isDigit)).map { JsonNumber(it.toInt()) }

// TODO: no escape support
fun stringLiteral(): Parser<String> = ParserAlternativeInstance.run {
    charParser('"').rightWins(spanParser { it != '"' }.leftWins(charParser('"')))
}

fun jsonString(): Parser<JsonValue> = ParserAlternativeInstance.run {
    stringLiteral().map { JsonString(it) }
}

fun whiteSpace(): Parser<String> = spanParser { it.isWhitespace() }

// TODO Fix this
fun <A, B> sepBy(sep: Parser<A>, element: Parser<B>): Parser<List<B>> = ParserAlternativeInstance.run {
    val elementAsSeq: Parser<SequenceK<B>> = element.map { sequenceOf(it).k() }
    val rw: Parser<SequenceK<B>> = sep.rightWins(element).many().fix()

    val cons: Parser<(SequenceK<B>) -> SequenceK<B>> =
        elementAsSeq.map { a: SequenceK<B> -> { b: SequenceK<B> -> (a + b).k() } }

    val result: Parser<SequenceK<B>> = rw.ap(cons).fix()
    val just: Parser<SequenceK<B>> = just(emptySequence<B>().k()).fix()
    (result alt just).map { it.toList() }.fix()
}

fun jsonArray(): Parser<JsonValue> = ParserAlternativeInstance.run {
    val separator = whiteSpace().rightWins(charParser(',')).leftWins(whiteSpace())
    val elements: Parser<List<JsonValue>> = sepBy(separator, jsonValue())
    val rightWins: Parser<List<JsonValue>> = charParser('[').rightWins(whiteSpace()).rightWins(elements)
    val result: Parser<List<JsonValue>> = rightWins.leftWins(whiteSpace()).leftWins(charParser(']'))
    result.map { JsonArray(it) }
}

fun jsonValue(): Parser<JsonValue> = Parser { input ->
    ParserAlternativeInstance.run {
        jsonNull() alt jsonBool() alt jsonNumber() alt jsonString() alt jsonArray()
    }.fix().runParser(input)
}

fun main() {
    println(jsonNumber().runParser("1"))
    println(jsonString().runParser("\"asdf\""))
    println(jsonArray().runParser("[]"))
    println(jsonArray().runParser("[1]"))
    println(jsonArray().runParser("[true]"))
    println(jsonArray().runParser("[null]"))
    println(jsonArray().runParser("[1, 2, 3, 4, true, \"asdf\", null, false, [1,2,3]]"))
    println(jsonArray().runParser("""["asdf"]"""))
}
