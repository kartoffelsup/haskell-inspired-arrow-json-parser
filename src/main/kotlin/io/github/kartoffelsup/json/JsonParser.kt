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
import arrow.core.toMap
import arrow.core.toT

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
    charParser('"').followedBy(spanParser { it != '"' }.apTap(charParser('"'))).fix()
}

fun jsonString(): Parser<JsonValue> = ParserAlternativeInstance.run {
    stringLiteral().map { JsonString(it) }
}

fun whiteSpace(): Parser<String> = spanParser { it.isWhitespace() }

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
    val followedBy: Parser<List<JsonValue>> = charParser('[').followedBy(whiteSpace()).followedBy(elements).fix()
    val result: Parser<List<JsonValue>> = followedBy.apTap(whiteSpace()).apTap(charParser(']')).fix()
    result.map { JsonArray(it) }
}

fun jsonObject(): Parser<JsonValue> = ParserAlternativeInstance.run {
    val prefix = charParser('{').apTap(whiteSpace()).fix()
    val suffix = whiteSpace().followedBy(charParser('}')).fix()
    val objectSeparator = whiteSpace().followedBy(charParser(',')).apTap(whiteSpace()).fix()
    val keyValueSeparator: Parser<Char> = whiteSpace().followedBy(charParser(':')).apTap(whiteSpace()).fix()
    val combineIntoObject: (String, Char, JsonValue) -> Tuple2<String, JsonValue> = { key, _, value -> key toT value }

    val map: Parser<(Char) -> (JsonValue) -> Tuple2<String, JsonValue>> =
        stringLiteral().map { s: String -> { c: Char -> { v: JsonValue -> combineIntoObject(s, c, v) } } }

    val pair: Parser<Tuple2<String, JsonValue>> = jsonValue().ap(keyValueSeparator.ap(map))

    prefix.followedBy(sepBy(objectSeparator, pair)).apTap(suffix).fix()
        .map { JsonObject(it.toMap()) }
}

fun jsonValue(): Parser<JsonValue> = Parser { input ->
    ParserAlternativeInstance.run {
        jsonNull() alt jsonBool() alt jsonNumber() alt jsonString() alt jsonArray() alt jsonObject()
    }.fix().runParser(input)
}

fun main() {
    println(
        jsonValue().runParser(
            """
            {"web-app": {
              "servlet": [   
                {
                  "servlet-name": "cofaxCDS",
                  "servlet-class": "org.cofax.cds.CDSServlet",
                  "init-param": {
                    "configGlossary:installationAt": "Philadelphia, PA",
                    "configGlossary:adminEmail": "ksm@pobox.com",
                    "configGlossary:poweredBy": "Cofax",
                    "configGlossary:poweredByIcon": "/images/cofax.gif",
                    "configGlossary:staticPath": "/content/static",
                    "templateProcessorClass": "org.cofax.WysiwygTemplate",
                    "templateLoaderClass": "org.cofax.FilesTemplateLoader",
                    "templatePath": "templates",
                    "templateOverridePath": "",
                    "defaultListTemplate": "listTemplate.htm",
                    "defaultFileTemplate": "articleTemplate.htm",
                    "useJSP": false,
                    "jspListTemplate": "listTemplate.jsp",
                    "jspFileTemplate": "articleTemplate.jsp",
                    "cachePackageTagsTrack": 200,
                    "cachePackageTagsStore": 200,
                    "cachePackageTagsRefresh": 60,
                    "cacheTemplatesTrack": 100,
                    "cacheTemplatesStore": 50,
                    "cacheTemplatesRefresh": 15,
                    "cachePagesTrack": 200,
                    "cachePagesStore": 100,
                    "cachePagesRefresh": 10,
                    "cachePagesDirtyRead": 10,
                    "searchEngineListTemplate": "forSearchEnginesList.htm",
                    "searchEngineFileTemplate": "forSearchEngines.htm",
                    "searchEngineRobotsDb": "WEB-INF/robots.db",
                    "useDataStore": true,
                    "dataStoreClass": "org.cofax.SqlDataStore",
                    "redirectionClass": "org.cofax.SqlRedirection",
                    "dataStoreName": "cofax",
                    "dataStoreDriver": "com.microsoft.jdbc.sqlserver.SQLServerDriver",
                    "dataStoreUrl": "jdbc:microsoft:sqlserver://LOCALHOST:1433;DatabaseName=goon",
                    "dataStoreUser": "sa",
                    "dataStorePassword": "dataStoreTestQuery",
                    "dataStoreTestQuery": "SET NOCOUNT ON;select test='test';",
                    "dataStoreLogFile": "/usr/local/tomcat/logs/datastore.log",
                    "dataStoreInitConns": 10,
                    "dataStoreMaxConns": 100,
                    "dataStoreConnUsageLimit": 100,
                    "dataStoreLogLevel": "debug",
                    "maxUrlLength": 500}},
                {
                  "servlet-name": "cofaxEmail",
                  "servlet-class": "org.cofax.cds.EmailServlet",
                  "init-param": {
                  "mailHost": "mail1",
                  "mailHostOverride": "mail2"}},
                {
                  "servlet-name": "cofaxAdmin",
                  "servlet-class": "org.cofax.cds.AdminServlet"},
             
                {
                  "servlet-name": "fileServlet",
                  "servlet-class": "org.cofax.cds.FileServlet"},
                {
                  "servlet-name": "cofaxTools",
                  "servlet-class": "org.cofax.cms.CofaxToolsServlet",
                  "init-param": {
                    "templatePath": "toolstemplates/",
                    "log": 1,
                    "logLocation": "/usr/local/tomcat/logs/CofaxTools.log",
                    "logMaxSize": "",
                    "dataLog": 1,
                    "dataLogLocation": "/usr/local/tomcat/logs/dataLog.log",
                    "dataLogMaxSize": "",
                    "removePageCache": "/content/admin/remove?cache=pages&id=",
                    "removeTemplateCache": "/content/admin/remove?cache=templates&id=",
                    "fileTransferFolder": "/usr/local/tomcat/webapps/content/fileTransferFolder",
                    "lookInContext": 1,
                    "adminGroupID": 4,
                    "betaServer": true}}],
              "servlet-mapping": {
                "cofaxCDS": "/",
                "cofaxEmail": "/cofaxutil/aemail/*",
                "cofaxAdmin": "/admin/*",
                "fileServlet": "/static/*",
                "cofaxTools": "/tools/*"},
             
              "taglib": {
                "taglib-uri": "cofax.tld",
                "taglib-location": "/WEB-INF/tlds/cofax.tld"}}}
        """.trimIndent()
        )
    )
}