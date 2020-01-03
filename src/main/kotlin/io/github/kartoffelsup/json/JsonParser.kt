package io.github.kartoffelsup.json

import arrow.Kind
import arrow.core.Eval
import arrow.core.ListK
import arrow.core.SequenceK
import arrow.core.Tuple2
import arrow.core.k
import arrow.core.toMap
import arrow.core.toT
import kotlin.time.ExperimentalTime
import kotlin.time.TimedValue
import kotlin.time.measureTimedValue

interface Parser<A> : ParserOf<A> {
    // TODO: no proper error reporting
    val runParser: (StringView) -> Eval<Tuple2<StringView, A>?>

    fun <B> map(f: (A) -> B): Parser<B> = Parser { input ->
        runParser(input).map { tuple2 -> tuple2?.map(f) }
    }

    fun <B> ap(ff: ParserOf<(A) -> B>): Parser<B> = lazyAp { ff }

    fun <B> lazyAp(ff: () -> ParserOf<(A) -> B>): Parser<B> = Parser { input ->
        fix().runParser(input).flatMap { op: Tuple2<StringView, A>? ->
            if (op == null) {
                Eval.just(null)
            } else {
                ff().fix().runParser(op.a).map { t ->
                    t?.map { it(op.b) }
                }
            }
        }
    }

    companion object {
        operator fun <A> invoke(parser: (StringView) -> Eval<Tuple2<StringView, A>?>): Parser<A> =
            ParserInstance(parser)

        fun <A> just(a: A): Parser<A> = Parser { input: StringView -> Eval.just(Tuple2(input, a)) }

        private data class ParserInstance<A>(
            override val runParser: (StringView) -> Eval<Tuple2<StringView, A>?>
        ) : Parser<A>
    }
}

internal fun maybe(char: Char): Parser<Char?> =
    ParserAlternativeInstance.run {
        val maybeParser: Kind<ForParser, String> = stringParser(char.toString()) alt just("")
        maybeParser.map { it.firstOrNull() }
    }.fix()

internal fun charParser(char: Char): Parser<Char> =
    Parser { input: StringView ->
        Eval.later {
            input
                .takeIf { it.isNotEmpty() && it[0] == char }
                ?.let { it.drop(1) toT it[0] }
        }
    }

internal fun stringParser(toParse: String): Parser<String> {
    val parser: Parser<ListK<Char>> = toParse
        .map(::charParser).k()
        .traverse(ParserApplicativeInstance) { it.fix() }.fix()

    return parser.map { it.s() }
}

internal fun spanParser(p: (Char) -> Boolean): Parser<StringView> =
    Parser { input ->
        Eval.later {
            val (xs, input2) = input.span(p)
            input2 toT xs
        }
    }

val jsonNull: Parser<JsonValue> =
    ParserFunctorInstance.run {
        stringParser("null").mapConst(JsonNull).fix()
    }

val jsonBool: Parser<JsonValue> =
    ParserAlternativeInstance.run {
        val jsonTrue = stringParser("true").mapConst(JsonBool(true))
        val jsonFalse = stringParser("false").mapConst(JsonBool(false))
        jsonTrue.alt(jsonFalse).fix()
    }

fun notEmpty(p: Parser<StringView>): Parser<StringView> =
    Parser { input ->
        p.runParser(input).map { option: Tuple2<StringView, StringView>? ->
            option?.takeIf { it.b.isNotEmpty() }
        }
    }

val jsonNumber: Parser<JsonValue> = maybe('-').ap(notEmpty(spanParser(Char::isDigit))
    .map { digits: StringView ->
        { maybeMinus: Char? ->
            maybeMinus?.let { minus -> minus + digits.value } ?: digits.value
        }
    }).map { JsonNumber(it.toInt()) }

// TODO: no escape support
val stringLiteral: Parser<StringView> =
    ParserAlternativeInstance.run {
        charParser('"').followedBy(spanParser { it != '"' }.apTap(charParser('"'))).fix()
    }

val jsonString: Parser<JsonValue> =
    ParserAlternativeInstance.run {
        stringLiteral.map { JsonString(it.value) }
    }

val whiteSpace: Parser<StringView> = spanParser { it.isWhitespace() }

fun <A, B> sepBy(sep: Parser<A>, element: Parser<B>): Parser<List<B>> =
    ParserAlternativeInstance.run {
        val elementAsSeq: Parser<SequenceK<B>> = element.map { sequenceOf(it).k() }
        val manySeparatedElements: Parser<SequenceK<B>> = sep.followedBy(element).many().fix()

        val combineWithManyElements: Parser<(SequenceK<B>) -> SequenceK<B>> =
            manySeparatedElements.map { a: SequenceK<B> -> { b: SequenceK<B> -> (a + b).k() } }

        val result: Parser<SequenceK<B>> = elementAsSeq.ap(combineWithManyElements).fix()
        val emptySequence: Parser<SequenceK<B>> = just(emptySequence<B>().k()).fix()
        (result alt emptySequence).map { it.toList() }.fix()
    }

val jsonArray: Parser<JsonValue> =
    ParserAlternativeInstance.run {
        val separator = whiteSpace.followedBy(charParser(',')).apTap(whiteSpace).fix()
        val elements: Parser<List<JsonValue>> = sepBy(separator, jsonValue())
        val prefix = charParser('[').followedBy(whiteSpace)
        val suffix = whiteSpace.apTap(charParser(']'))
        val result: Parser<List<JsonValue>> = prefix.followedBy(elements).apTap(suffix).fix()
        result.map { JsonArray(it) }
    }

val jsonObject: Parser<JsonValue> =
    ParserAlternativeInstance.run {
        val prefix = charParser('{').apTap(whiteSpace).fix()
        val suffix = whiteSpace.followedBy(charParser('}')).fix()
        val objectSeparator = whiteSpace.followedBy(charParser(',')).apTap(whiteSpace).fix()

        val keyValueSeparator: Parser<Char> = whiteSpace.followedBy(charParser(':')).apTap(whiteSpace).fix()

        val combineIntoJsonValue: Parser<(Char) -> (StringView) -> Tuple2<String, JsonValue>> =
            jsonValue().map { value: JsonValue -> { separator: Char -> { key: StringView -> key.value toT value } } }

        val pair: Parser<Tuple2<String, JsonValue>> = stringLiteral.ap(keyValueSeparator.ap(combineIntoJsonValue))

        prefix.followedBy(sepBy(objectSeparator, pair)).apTap(suffix).fix()
            .map { JsonObject(it.toMap()) }
    }

fun jsonValue(): Parser<JsonValue> =
    Parser { input ->
        ParserAlternativeInstance.run {
            jsonNull alt jsonBool alt jsonNumber alt jsonString alt jsonArray alt jsonObject
        }.fix().runParser(input)
    }

@ExperimentalTime
fun main() {
//    val sampleJson = loadResource("/sample.json")
//    val tsodingScheduleJson = loadResource("/tsoding_schedule.json")
    val githubGists = loadResource("/github_gists.json")
//    val _180mbJson = loadResource("/citylots_no_floats.json")
//    measureParse("""{"test": [[-1,[2],[[4,3,[9,8,7], 2], 1,2,3]], 1,2,3], "foo": 1, "bar": false, "baz": "value"}""")
//    measureParse(sampleJson)
//    measureParse(tsodingScheduleJson)
    measureParse(githubGists)
//    measureParse(_180mbJson)
}

fun loadResource(name: String) = Parser::class.java.getResourceAsStream(name).readBytes().toString(Charsets.UTF_8)

@ExperimentalTime
fun measureParse(s: String) {
    val f: TimedValue<Tuple2<StringView, JsonValue>?> = measureTimedValue {
        jsonValue().runParser(StringView.from(s)).value()
    }

    println("Took ${f.duration} to parse ${s.length} characters roughly ${(s.length + 2) / 1024 / 1024}mb")
}
