package io.github.kartoffelsup.json

import arrow.core.Tuple2
import arrow.core.toT

fun List<Char>.s() = joinToString("")

fun <T> List<T>.span(p: (T) -> Boolean): Tuple2<List<T>, List<T>> = this.takeWhile(p) toT this.dropWhile(p)