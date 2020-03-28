package io.github.kartoffelsup.json

import arrow.Kind
import arrow.core.Eval
import arrow.core.SequenceK
import arrow.core.Tuple2
import arrow.core.k
import arrow.core.toT
import arrow.typeclasses.Alternative
import arrow.typeclasses.Applicative
import arrow.typeclasses.Functor

object ParserFunctorInstance : Functor<ForParser> {
    override fun <A, B> Kind<ForParser, A>.map(f: (A) -> B): Kind<ForParser, B> = fix().map(f)
}

object ParserApplicativeInstance : Applicative<ForParser> {
    override fun <A> just(a: A): Parser<A> = Parser.just(a)

    override fun <A, B> ParserOf<A>.ap(ff: ParserOf<(A) -> B>): Parser<B> = fix().lazyAp { ff }
}

object ParserAlternativeInstance : Alternative<ForParser> {
    override fun <A> empty(): Parser<A> = Parser { Eval.just(null) }

    override fun <A> just(a: A): ParserOf<A> = Parser.just(a)

    override fun <A> Kind<ForParser, A>.many(): Parser<SequenceK<A>> = Parser { input: StringView ->
        val parserA: Parser<A> = this.fix()
        parserA.runParser(input).flatMap { tuple: Tuple2<StringView, A>? ->
            if (tuple == null) {
                Eval.just(input toT emptySequence<A>().k())
            } else {
                val (r, xs) = tuple
                many().runParser(r).map { t: Tuple2<StringView, SequenceK<A>>? ->
                    if (t == null) {
                        r toT sequenceOf(xs).k()
                    } else {
                        val (r2: StringView, xs2: SequenceK<A>) = t
                        r2 toT (sequenceOf(xs) + xs2).k()
                    }
                }
            }
        }
    }

    override fun <A, B> ParserOf<A>.ap(ff: ParserOf<(A) -> B>): Parser<B> = fix().lazyAp { ff }

    override fun <A> Kind<ForParser, A>.orElse(b: Kind<ForParser, A>): Kind<ForParser, A> = Parser { input ->
        this.fix().runParser(input).flatMap { tuple: Tuple2<StringView, A>? ->
            if (tuple != null) Eval.just(tuple)
            else b.fix().runParser(input)
        }
    }
}
