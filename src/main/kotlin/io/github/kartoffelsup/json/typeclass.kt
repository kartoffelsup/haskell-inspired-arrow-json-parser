package io.github.kartoffelsup.json

import arrow.Kind
import arrow.core.None
import arrow.core.Option
import arrow.core.SequenceK
import arrow.core.Some
import arrow.core.Tuple2
import arrow.core.curry
import arrow.core.k
import arrow.core.orElse
import arrow.core.toT
import arrow.typeclasses.Alternative
import arrow.typeclasses.Applicative
import arrow.typeclasses.Functor

interface ParserFunctor : Functor<ForParser> {
    override fun <A, B> ParserOf<A>.map(f: (A) -> B) = fix().map(f)

}

interface ParserApplicative : Applicative<ForParser> {
    override fun <A> just(a: A): Parser<A> = Parser { input ->
        Some(Tuple2(input, a))
    }

    override fun <A, B> ParserOf<A>.ap(ff: ParserOf<(A) -> B>) = fix().ap(ff)
}

interface ParserAlternative : Alternative<ForParser> {
    override fun <A> empty(): Parser<A> = Parser { None }

    override fun <A, B> ParserOf<A>.ap(ff: ParserOf<(A) -> B>): Parser<B> = fix().ap(ff)

    override fun <A> just(a: A): Kind<ForParser, A> = Parser.just(a)

    override fun <A> Kind<ForParser, A>.many(): Parser<SequenceK<A>> = Parser { input: String ->
        val parserA: Parser<A> = this.fix()
        val runParser: Option<Tuple2<String, A>> = parserA.runParser(input)
        runParser.fold(
            ifEmpty = {
                val tuple2: Tuple2<String, SequenceK<A>> = input toT emptySequence<A>().k()
                val some: Option<Tuple2<String, SequenceK<A>>> = Some(tuple2)
                some
            },
            ifSome = { (r: String, a: A) ->
                many().runParser(r).fold({ Some(r toT sequenceOf(a).k()) }, { (r2: String, xs: SequenceK<A>) ->
                    val tuple: Tuple2<String, SequenceK<A>> = r2 toT (sequenceOf(a) + xs).k()
                    Some(tuple)
                })
            })
    }

    override fun <A> Kind<ForParser, A>.orElse(b: Kind<ForParser, A>): Kind<ForParser, A> = Parser { input ->
        this.fix().runParser(input).orElse {
            b.fix().runParser(input)
        }
    }

    fun <A, B, C> Kind<ForParser, A>.liftA2(a: Kind<ForParser, A>, b: Kind<ForParser, B>, f: (A, B) -> C): Parser<C> {
        return b.ap(a.fix().map(f.curry())).fix()
    }

    fun <A, B> Kind<ForParser, A>.leftWins(right: Kind<ForParser, B>): Parser<A> = liftA2(this, right) { a, _ -> a }

    fun <A, B> Kind<ForParser, A>.rightWins(right: Kind<ForParser, B>): Parser<B> = liftA2(this, right) { _, b -> b }
}

object ParserFunctorInstance : ParserFunctor
object ParserApplicativeInstance : ParserApplicative
object ParserAlternativeInstance : ParserAlternative
