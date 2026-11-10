package cn.yidongchi.app

import cn.yidongchi.core.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

class LegacyPoolImport(private val directory:File,private val store:PoolHistoryStore) {
    suspend fun restore(onCached:suspend (PoolBatch?)->Unit) {
        onCached(store.complete()?:store.pending()?:File(directory,"batch.json").takeIf{it.isFile}?.let{PoolCodec.decode(it.readText())})
        run()
    }
    suspend fun run() {
        if(store.metadata("legacy-import")=="done")return
        val batchFile=File(directory,"batch.json")
        val batch=batchFile.takeIf{it.isFile}?.let{PoolCodec.decode(it.readText())}
        val stocks=batch?.universe?.associateBy{it.symbol}.orEmpty()+batch?.rows?.associate{it.stock.symbol to it.stock}.orEmpty()
        val pattern=Regex("^(\\d{4}-\\d{2}-\\d{2})-(tencent|eastmoney)-((?:sh|sz)\\d{6})\\.json$")
        val groups=directory.listFiles().orEmpty().mapNotNull{f->pattern.matchEntire(f.name)?.let{it.groupValues to f}}.groupBy{it.first[2] to it.first[3]}
        for(chunk in groups.entries.chunked(100)) {
            currentCoroutineContext().ensureActive()
            val writes=chunk.mapNotNull{(key,files)->
                val (provider,symbol)=key
                val parsed=files.sortedByDescending{it.first[1]}.firstNotNullOfOrNull{(_,file)->
                    runCatching {val text=file.readText();val bars=if(provider=="tencent")MarketParser.daily(symbol,text)else MarketParser.eastDaily(symbol,text);bars.takeIf{it.isNotEmpty()}}.getOrNull()
                }?:return@mapNotNull null
                val old=store.history(symbol,provider)
                if(old!=null&&old.bars.last().date>=parsed.last().date)return@mapNotNull null
                val stock=stocks[symbol]?:Stock(symbol,symbol,Watchlist.boardFor(symbol))
                HistoryWrite(HistorySeries(stock,provider,parsed),true)
            }
            store.write(writes)
        }
        if(store.complete()==null&&store.pending()==null&&batch!=null) {
            store.begin(batch)
            if(batch.complete&&batch.basis.time.hour>=15)store.finish(batch)
        }
        store.metadata("legacy-import","done")
    }
}
