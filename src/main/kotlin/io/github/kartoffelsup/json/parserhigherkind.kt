package io.github.kartoffelsup.json

class ForParser private constructor() { companion object }
typealias ParserOf<A> = arrow.Kind<ForParser, A>

@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
inline fun <A> ParserOf<A>.fix(): Parser<A> =
    this as Parser<A>
