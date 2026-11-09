package cn.yidongchi.app

import java.time.LocalDate

interface PoolHistoryStore {
    fun history(symbol:String,provider:String?=null):HistorySeries?
    fun write(writes:List<HistoryWrite>)
    fun complete():PoolBatch?
    fun pending():PoolBatch?
    fun begin(batch:PoolBatch,clearQuotes:Boolean=false)
    fun checkpoint(batch:PoolBatch,writes:List<HistoryWrite> = emptyList())
    fun finish(batch:PoolBatch)
    fun quotes(date:LocalDate):Map<String,String>
    fun saveQuotes(date:LocalDate,quotes:Map<String,String>)
    fun metadata(key:String):String?
    fun metadata(key:String,value:String)
}
