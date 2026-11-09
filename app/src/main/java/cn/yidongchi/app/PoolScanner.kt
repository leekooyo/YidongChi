package cn.yidongchi.app

import cn.yidongchi.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDateTime

data class UniversePage(val total:Int,val stocks:List<Stock>)
data class PoolBasis(val time:LocalDateTime,val indices:Map<String,List<Bar>>,val provider:String="腾讯公开行情")
data class PoolOutcome(val result:PoolStock?=null,val issue:String?=null,val excluded:Boolean=false,val provider:String?=null)
data class PoolBatch(val basis:PoolBasis,val universe:List<Stock>,val outcomes:Map<String,PoolOutcome> = emptyMap()) {
    val processed get()=outcomes.size
    val failures get()=outcomes.filterValues{it.issue!=null&&!it.excluded}
    val exclusions get()=outcomes.filterValues{it.excluded}
    val rows get()=outcomes.values.mapNotNull{it.result}.filter{it.hits.isNotEmpty()}
    val complete get()=universe.isNotEmpty()&&processed==universe.size&&failures.isEmpty()
    fun retryFailures()=copy(outcomes=outcomes.filterValues{it.issue==null||it.excluded})
}

interface PoolSource {
    suspend fun universe():List<Stock>
    suspend fun basis():PoolBasis
    suspend fun load(stock:Stock,basis:PoolBasis):MarketSnapshot
}
class PoolExcluded(message:String):IllegalArgumentException(message)

object PoolUniverse {
    suspend fun collect(page:suspend (Int)->UniversePage):List<Stock> {
        val all=linkedMapOf<String,Stock>();var number=1;var total:Int?=null
        do {
            val next=page(number++)
            if(total==null)total=next.total
            require(next.total==total&&total>0){"全市场清单已变化或为空，请重新扫描"}
            val previous=all.size
            next.stocks.forEach{all[it.symbol]=it}
            require(all.size>previous){"无法取得完整沪深股票清单，请重试"}
            require(all.size<=total){"证券清单数量不一致，请重试"}
        }while(all.size<total!!)
        return all.values.toList()
    }
}

class PoolScanner(private val source:PoolSource) {
    suspend fun scan(initial:PoolBatch,resets:(String)->ResetDates,onProgress:suspend (PoolBatch)->Unit):PoolBatch=coroutineScope {
        var current=initial
        val gate=Semaphore(3);val lock=Mutex()
        initial.universe.filter{it.symbol !in initial.outcomes}.map {stock->launch {
            gate.withPermit {
                ensureActive()
                val outcome=try {
                    val snapshot=source.load(stock,initial.basis)
                    require(snapshot.quote.time.toLocalDate()==initial.basis.time.toLocalDate()){"行情日期与扫描基准不一致"}
                    val result=TomorrowPool.calculate(snapshot.quote.stock,snapshot.bars,snapshot.indexBars,resets(stock.symbol))
                    if(result.excludedReason!=null)PoolOutcome(issue=result.excludedReason,excluded=true)
                    else PoolOutcome(result=result.takeIf{it.hits.isNotEmpty()},provider=snapshot.provider)
                } catch(e:CancellationException){throw e}
                catch(e:PoolExcluded){PoolOutcome(issue=e.message,excluded=true)}
                catch(e:Exception){PoolOutcome(issue=e.message?:"数据读取失败")}
                ensureActive()
                lock.withLock {
                    current=current.copy(outcomes=current.outcomes+(stock.symbol to outcome))
                    onProgress(current)
                }
            }
        }}.joinAll()
        current
    }
}
