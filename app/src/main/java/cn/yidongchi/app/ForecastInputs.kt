package cn.yidongchi.app

import cn.yidongchi.core.Scenario
import cn.yidongchi.core.bd
import java.math.BigDecimal

object ForecastInputs {
    fun scenario(mode:String,stock:String,index:String,stockPath:String,indexPath:String,reset:Boolean):Scenario {
        fun number(value:String)=value.trim().ifEmpty {"0"}.bd()
        fun path(value:String)=if(value.isBlank())emptyList() else value.replace('，',',').split(',').map {number(it)}
        val limit=when(mode){"连续涨停"->1;"连续跌停"->-1;else->0}
        return Scenario("情景A",if(limit==0)number(stock)else BigDecimal.ZERO,number(index),
            if(mode=="逐日路径")path(stockPath)else emptyList(),if(mode=="逐日路径")path(indexPath)else emptyList(),limit,reset)
    }
}
