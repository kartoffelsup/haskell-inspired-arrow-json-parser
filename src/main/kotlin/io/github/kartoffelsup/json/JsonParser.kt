package io.github.kartoffelsup.json

import arrow.Kind
import arrow.core.ForListK
import arrow.core.None
import arrow.core.Option
import arrow.core.SequenceK
import arrow.core.Some
import arrow.core.Tuple2
import arrow.core.extensions.fx
import arrow.core.extensions.list.traverse.sequence
import arrow.core.fix
import arrow.core.k
import arrow.core.toMap
import arrow.core.toOption
import arrow.core.toT
import kotlin.time.ExperimentalTime
import kotlin.time.TimedValue
import kotlin.time.measureTimedValue

interface Parser<out A> : ParserOf<A> {
    // TODO: no proper error reporting
    val runParser: (StringView) -> Option<Tuple2<StringView, A>>

    fun <B> map(f: (A) -> B): Parser<B> =
        Parser { input: StringView ->
            runParser(input).map { t ->
                t.map(f)
            }
        }

    fun <B> ap(ff: ParserOf<(A) -> B>): Parser<B> = Parser { input ->
        Option.fx {
            val (input2: StringView, f: (A) -> B) = ff.fix().runParser(input).bind()
            val (input3: StringView, g: A) = fix().runParser(input2).bind()
            Tuple2(input3, f(g))
        }
    }

    companion object {
        operator fun <T> invoke(parser: (StringView) -> Option<Tuple2<StringView, T>>): Parser<T> =
            ParserInstance(parser)

        fun <A> just(a: A): Parser<A> = Parser { input ->
            Some(Tuple2(input, a))
        }

        private data class ParserInstance<A>(
            override val runParser: (StringView) -> Option<Tuple2<StringView, A>>
        ) : Parser<A>
    }
}

@Suppress("SameParameterValue")
private fun maybe(char: Char): Parser<Option<Char>> =
    ParserAlternativeInstance.run {
        val maybeParser: Kind<ForParser, String> = stringParser(char.toString()) alt just("")
        maybeParser.map { it.firstOrNull().toOption() }
    }.fix()

private fun charParser(char: Char): Parser<Char> =
    Parser { input: StringView ->
        input
            .takeIf { it.isNotEmpty() && it[0] == char }
            .toOption()
            .map { it.drop(1) toT it[0] }
    }

private fun stringParser(string: String): Parser<String> {
    val parser: Parser<Kind<ForListK, Char>> = string
        .map(::charParser).k()
        .sequence(ParserApplicativeInstance).fix()

    return parser.map { it.fix().s() }
}

private fun spanParser(p: (Char) -> Boolean): Parser<StringView> = Parser { input ->
    val (xs, input2) = input.span(p)
    Some(input2 toT xs)
}

fun jsonNull(): Parser<JsonValue> =
    stringParser("null").map { JsonNull }

fun jsonBool(): Parser<JsonValue> = ParserAlternativeInstance.run {
    stringParser("true").map { JsonBool(true) } alt stringParser("false").map { JsonBool(false) }
}.fix()

fun notEmpty(p: Parser<StringView>): Parser<StringView> = Parser { input ->
    val runParser = p.runParser(input)
    runParser.flatMap { tuple ->
        if (tuple.b.isEmpty()) {
            None
        } else {
            Some(tuple)
        }
    }
}

fun jsonNumber(): Parser<JsonValue> {
    val maybeMinusCombineWithDigits: Parser<(StringView) -> String> = maybe('-')
        .map { maybeMinus: Option<Char> ->
            { digits: StringView ->
                maybeMinus.fold(
                    ifEmpty = { digits.value },
                    ifSome = { minus -> minus + digits.value }
                )
            }
        }

    return notEmpty(spanParser(Char::isDigit)).ap(maybeMinusCombineWithDigits).map { JsonNumber(it.toInt()) }
}

// TODO: no escape support
fun stringLiteral(): Parser<StringView> = ParserAlternativeInstance.run {
    charParser('"').followedBy(spanParser { it != '"' }.apTap(charParser('"'))).fix()
}

fun jsonString(): Parser<JsonValue> = ParserAlternativeInstance.run {
    stringLiteral().map { JsonString(it.value) }
}

fun whiteSpace(): Parser<StringView> = spanParser { it.isWhitespace() }

fun <A, B> sepBy(sep: Parser<A>, element: Parser<B>): Parser<List<B>> = ParserAlternativeInstance.run {
    val elementAsSeq: Parser<SequenceK<B>> = element.map { sequenceOf(it).k() }
    val rw: Parser<SequenceK<B>> = sep.followedBy(element).many().fix()

    val cons: Parser<(SequenceK<B>) -> SequenceK<B>> =
        elementAsSeq.map { a: SequenceK<B> -> { b: SequenceK<B> -> (a + b).k() } }

    val result: Parser<SequenceK<B>> = rw.ap(cons).fix()
    val just: Parser<SequenceK<B>> = just(emptySequence<B>().k()).fix()
    (result alt just).map { it.toList() }.fix()
}

fun jsonArray(): Parser<JsonValue> = ParserAlternativeInstance.run {
    val separator = whiteSpace().followedBy(charParser(',')).apTap(whiteSpace()).fix()
    val elements: Parser<List<JsonValue>> = sepBy(separator, jsonValue())
    val prefix = charParser('[').followedBy(whiteSpace())
    val suffix = whiteSpace().apTap(charParser(']'))
    val result: Parser<List<JsonValue>> = prefix.followedBy(elements).apTap(suffix).fix()
    result.map { JsonArray(it) }
}

fun jsonObject(): Parser<JsonValue> = ParserAlternativeInstance.run {
    val prefix = charParser('{').apTap(whiteSpace()).fix()
    val suffix = whiteSpace().followedBy(charParser('}')).fix()
    val objectSeparator = whiteSpace().followedBy(charParser(',')).apTap(whiteSpace()).fix()
    val keyValueSeparator: Parser<Char> = whiteSpace().followedBy(charParser(':')).apTap(whiteSpace()).fix()
    val combineIntoObject: (StringView, Char, JsonValue) -> Tuple2<String, JsonValue> =
        { key, _, value -> key.value toT value }

    val map: Parser<(Char) -> (JsonValue) -> Tuple2<String, JsonValue>> =
        stringLiteral().map { s: StringView -> { c: Char -> { v: JsonValue -> combineIntoObject(s, c, v) } } }

    val pair: Parser<Tuple2<String, JsonValue>> = jsonValue().ap(keyValueSeparator.ap(map))

    prefix.followedBy(sepBy(objectSeparator, pair)).apTap(suffix).fix()
        .map { JsonObject(it.toMap()) }
}

fun jsonValue(): Parser<JsonValue> = Parser { input: StringView ->
    ParserAlternativeInstance.run {
        jsonNull() alt jsonBool() alt jsonNumber() alt jsonString() alt jsonArray() alt jsonObject()
    }.fix().runParser(input)
}

@ExperimentalTime
fun main() {
    val sampleJson = Parser::class.java.getResourceAsStream("/sample.json").readBytes().toString(Charsets.UTF_8)
    val _180mbJson = Parser::class.java.getResourceAsStream("/citylots_no_floats_no_negatives.json").readBytes()
        .toString(Charsets.UTF_8)
    measureParse(""""test"""")
    measureParse("""1""")
    measureParse("""-1""")
    measureParse("""false""")
    measureParse("""null""")
    measureParse("""[1]""")
    measureParse("""[1,2,3]""")
    measureParse("""{"test": [[1,2,[1,2,3]], 1,2,3], "foo": 1, "bar": false, "baz": "value"}""")
    measureParse(sampleJson)
    measureParse(_180mbJson)
}

@ExperimentalTime
fun measureParse(s: String) {
    val f: TimedValue<Option<Tuple2<StringView, JsonValue>>> = measureTimedValue {
        jsonValue().runParser(StringView.from(s))
    }

    println("Took ${f.duration} to parse ${f.value}")
}
