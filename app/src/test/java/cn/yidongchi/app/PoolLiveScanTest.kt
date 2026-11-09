package cn.yidongchi.app

import cn.yidongchi.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** 手动启用的真实全市场验收，常规单测不发数千条网络请求。 */
class PoolLiveScanTest {
    @Test fun scanAllShanghaiAndShenzhenStocks()=runBlocking {
        assumeTrue(System.getenv("YIDONGCHI_FULL_SCAN")=="1")
        val root=File(System.getenv("YIDONGCHI_SCAN_DIR")?:".superpowers/sdd/2026-11-09-tomorrow-pool/live")
        root.mkdirs()
        val repo=PoolRepository(root)
        val started=System.currentTimeMillis()
        val basis=repo.basis()
        val old=repo.cached()
        val initial=if(old?.basis?.time?.toLocalDate()==basis.time.toLocalDate())old.retryFailures()else PoolBatch(basis,repo.universe())
        println("UNIVERSE ${initial.universe.size} DATE ${initial.basis.time}")
        var latest=initial
        val result=try {
            withTimeout(45*60*1000L){PoolScanner(repo).scan(initial,{ResetDates()}) {batch->
                latest=batch
                if(batch.processed%50==0||batch.processed==batch.universe.size){repo.save(batch);println("PROGRESS ${batch.processed}/${batch.universe.size} hits=${batch.rows.size} failed=${batch.failures.size} excluded=${batch.exclusions.size} elapsed=${(System.currentTimeMillis()-started)/1000}s")}
            }}
        }finally{repo.save(latest)}
        repo.save(result)
        val summary="total=${result.universe.size}, processed=${result.processed}, failures=${result.failures.size}, excluded=${result.exclusions.size}, hitStocks=${result.rows.size}, elapsedSeconds=${(System.currentTimeMillis()-started)/1000}"
        File(root,"summary.txt").writeText(summary)
        println("RESULT $summary")
        assertTrue("必须确实覆盖全市场",result.universe.size>5000)
        assertEquals(result.universe.size,result.processed)
        assertEquals("存在数据失败，需要明确报告并重试",0,result.failures.size)
    }
}
