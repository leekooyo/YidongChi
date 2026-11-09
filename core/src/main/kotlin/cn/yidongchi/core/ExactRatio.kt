package cn.yidongchi.core

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/** 价格和点位都是有限小数；保持分数直到最终报价取整，避免循环除法多进一档。 */
internal class ExactRatio private constructor(val numerator:BigInteger,val denominator:BigInteger):Comparable<ExactRatio> {
    operator fun times(other:ExactRatio)=of(numerator*other.numerator,denominator*other.denominator)
    operator fun div(other:ExactRatio)=of(numerator*other.denominator,denominator*other.numerator)
    operator fun plus(other:ExactRatio)=of(numerator*other.denominator+other.numerator*denominator,denominator*other.denominator)
    operator fun minus(other:ExactRatio)=of(numerator*other.denominator-other.numerator*denominator,denominator*other.denominator)
    override fun compareTo(other:ExactRatio)=(numerator*other.denominator).compareTo(other.numerator*denominator)
    fun decimal()=BigDecimal(numerator).divide(BigDecimal(denominator),MC)
    fun quote(unit:BigDecimal,direction:Direction):BigDecimal {
        val steps=this/from(unit)
        return BigDecimal(steps.numerator).divide(BigDecimal(steps.denominator),0,if(direction==Direction.UP)RoundingMode.CEILING else RoundingMode.FLOOR).multiply(unit)
    }
    companion object {
        private fun of(n:BigInteger,d:BigInteger):ExactRatio {val gcd=n.gcd(d);val sign=if(d.signum()<0)BigInteger.valueOf(-1)else BigInteger.ONE;return ExactRatio(n/gcd*sign,d/gcd*sign)}
        fun from(value:BigDecimal):ExactRatio = if(value.scale()>=0)of(value.unscaledValue(),BigInteger.TEN.pow(value.scale())) else of(value.unscaledValue()*BigInteger.TEN.pow(-value.scale()),BigInteger.ONE)
        fun growth(bars:List<Bar>)=bars.fold(from(BigDecimal.ONE)){acc,b->acc*from(b.close)/from(b.previousClose)}
    }
}
