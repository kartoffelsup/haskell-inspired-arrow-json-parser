package io.github.kartoffelsup.json

import arrow.core.Tuple2
import arrow.core.toT
import java.lang.StringBuilder

fun List<Char>.s(): String = joinTo(StringBuilder(size), "").toString()

fun String.span(p: (Char) -> Boolean): Tuple2<String, String> = this.takeWhile(p) toT this.dropWhile(p)
