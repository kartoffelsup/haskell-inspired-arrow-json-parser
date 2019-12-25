package io.github.kartoffelsup.json

import arrow.core.None
import arrow.core.Option
import arrow.core.Tuple2
import arrow.core.extensions.option.eqK.eqK
import arrow.test.UnitSpec
import arrow.test.generators.GenK
import arrow.test.laws.AlternativeLaws
import arrow.test.laws.ApplicativeLaws
import arrow.test.laws.FunctorLaws
import arrow.typeclasses.Eq
import arrow.typeclasses.EqK
import io.kotlintest.properties.Gen

object ParserEqK : EqK<ForParser> {
    override fun <A> ParserOf<A>.eqK(other: ParserOf<A>, EQ: Eq<A>): Boolean {
        val first: Tuple2<StringView, A>? = fix().runParser(StringView.from(""))
        val second: Tuple2<StringView, A>? = other.fix().runParser(StringView.from(""))
        return Option.eqK().run {
            first == second
        }
    }
}

class ParserLawsTest : UnitSpec() {
    init {
        val genK = object : GenK<ForParser> {
            override fun <A> genK(gen: Gen<A>): Gen<ParserOf<A>> {
                return gen.orNull().map { a -> a?.let { Parser.just(it) } ?: Parser { null } }
            }
        }
        testLaws(
            FunctorLaws.laws(ParserFunctorInstance, genK, ParserEqK),
            ApplicativeLaws.laws(ParserApplicativeInstance, genK, ParserEqK),
            AlternativeLaws.laws(ParserAlternativeInstance, genK, ParserEqK)
        )
    }
}