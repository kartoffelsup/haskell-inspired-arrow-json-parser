package io.github.kartoffelsup.json

import arrow.core.Eval
import arrow.core.Option
import arrow.core.Tuple2
import arrow.core.extensions.option.eqK.eqK
import arrow.core.test.UnitSpec
import arrow.core.test.generators.GenK
import arrow.core.test.laws.AlternativeLaws
import arrow.core.test.laws.ApplicativeLaws
import arrow.core.test.laws.FunctorLaws
import arrow.typeclasses.Eq
import arrow.typeclasses.EqK
import io.kotlintest.properties.Gen

object ParserEqK : EqK<ForParser> {
    override fun <A> ParserOf<A>.eqK(other: ParserOf<A>, EQ: Eq<A>): Boolean {
        val first: Tuple2<StringView, A>? = fix().runParser(StringView.from("")).value()
        val second: Tuple2<StringView, A>? = other.fix().runParser(StringView.from("")).value()
        return Option.eqK().run {
            first == second
        }
    }
}

class ParserLawsTest : UnitSpec() {
    init {
        val genK = object : GenK<ForParser> {
            override fun <A> genK(gen: Gen<A>): Gen<ParserOf<A>> {
                return gen.orNull().map { a: A? -> a?.let { it: A -> Parser.just(it) } ?: Parser { Eval.just(null) } }
            }
        }
        testLaws(
            FunctorLaws.laws(ParserFunctorInstance, genK, ParserEqK),
            ApplicativeLaws.laws(ParserApplicativeInstance, genK, ParserEqK),
            AlternativeLaws.laws(ParserAlternativeInstance, genK, ParserEqK)
        )
    }
}