package io.github.kartoffelsup.json

import arrow.Kind
import arrow.core.AndThen
import arrow.core.Tuple2
import arrow.typeclasses.Alternative
import arrow.typeclasses.Applicative
import arrow.typeclasses.Functor

interface ParserFunctor : Functor<ForParser> {
    override fun <A, B> ParserOf<A>.map(f: (A) -> B) = fix().map(f)
}

interface ParserApplicative : Applicative<ForParser> {
    override fun <A> just(a: A): Parser<A> = Parser(AndThen { input ->
        Tuple2(input, a)
    })

    override fun <A, B> ParserOf<A>.ap(ff: ParserOf<(A) -> B>): Parser<B> = fix().ap(ff)

    override fun <A, B> ParserOf<A>.lazyAp(ff: () -> ParserOf<(A) -> B>) = fix().lazyAp(ff)
}

interface ParserAlternative : Alternative<ForParser> {
    override fun <A> empty(): Parser<A> = Parser { null }

    override fun <A> just(a: A): ParserOf<A> = Parser.just(a)

    override fun <A, B> ParserOf<A>.ap(ff: ParserOf<(A) -> B>): Parser<B> = fix().ap(ff)

    override fun <A, B> ParserOf<A>.lazyAp(ff: () -> ParserOf<(A) -> B>) = fix().lazyAp(ff)

    override fun <A> Kind<ForParser, A>.orElse(b: Kind<ForParser, A>): Kind<ForParser, A> = Parser(
        this.fix().runParser.flatMap { g -> b.fix().runParser.map { bo -> g ?: bo } }
    )
}

object ParserFunctorInstance : ParserFunctor
object ParserApplicativeInstance : ParserApplicative
object ParserAlternativeInstance : ParserAlternative
