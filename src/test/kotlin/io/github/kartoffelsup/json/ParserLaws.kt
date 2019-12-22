package io.github.kartoffelsup.json

import arrow.Kind
import arrow.test.UnitSpec
import arrow.test.laws.AlternativeLaws
import arrow.test.laws.ApplicativeLaws
import arrow.test.laws.FunctorLaws
import arrow.typeclasses.Eq

interface ParserIntEq : Eq<Kind<ForParser, Int>> {
    override fun Kind<ForParser, Int>.eqv(b: Kind<ForParser, Int>): Boolean {
        return fix().runParser(StringView.from("asdf")) == b.fix().runParser(StringView.from("asdf")) &&
                fix().runParser(StringView.from("")) == b.fix().runParser(StringView.from(""))
    }
}

object ParserIntEqInstance : ParserIntEq

class ParserLawsTest : UnitSpec() {
    init {
        testLaws(
            FunctorLaws.laws(ParserFunctorInstance, { Parser.just(it) }, ParserIntEqInstance),
            ApplicativeLaws.laws(ParserApplicativeInstance, ParserIntEqInstance),
            AlternativeLaws.laws(
                ParserAlternativeInstance,
                { Parser.just(it) },
                { i -> Parser.just { j: Int -> i + j } },
                ParserIntEqInstance
            )
        )
    }
}