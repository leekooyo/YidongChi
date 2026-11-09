package cn.yidongchi.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import java.time.LocalDate
import java.io.Closeable

class SQLitePoolHistory(context:Context,name:String="pool-history.db"):SQLiteOpenHelper(context,name,null,1),PoolHistoryStore,Closeable {
    override fun onCreate(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("CREATE TABLE series (symbol TEXT, provider TEXT, stock TEXT NOT NULL, last_day TEXT NOT NULL, PRIMARY KEY(symbol,provider))")
        db.execSQL("CREATE TABLE bars (symbol TEXT, provider TEXT, day TEXT, value TEXT NOT NULL, PRIMARY KEY(symbol,provider,day))")
        db.execSQL("CREATE TABLE quotes (symbol TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("CREATE TABLE outcomes (symbol TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int)=Unit
    private inline fun transaction(block:()->Unit) {
        val db=writableDatabase;db.beginTransaction()
        try{block();db.setTransactionSuccessful()}finally{db.endTransaction()}
    }
    override fun metadata(key:String):String?=readableDatabase.rawQuery("SELECT value FROM metadata WHERE key=?",arrayOf(key)).use{if(it.moveToFirst())it.getString(0)else null}
    override fun metadata(key:String,value:String){writableDatabase.execSQL("INSERT OR REPLACE INTO metadata VALUES(?,?)",arrayOf(key,value))}
    override fun history(symbol:String,provider:String?):HistorySeries? {
        val args=if(provider==null)arrayOf(symbol)else arrayOf(symbol,provider)
        return readableDatabase.rawQuery("SELECT provider,stock FROM series WHERE symbol=?${if(provider==null)""else " AND provider=?"} ORDER BY last_day DESC,provider DESC LIMIT 1",args).use {rows->
            if(!rows.moveToFirst())return null
            val source=rows.getString(0);val stock=PoolCodec.stock(JSONObject(rows.getString(1)))
            val bars=readableDatabase.rawQuery("SELECT value FROM bars WHERE symbol=? AND provider=? ORDER BY day",arrayOf(symbol,source)).use {b->buildList{while(b.moveToNext())add(PoolCodec.bar(org.json.JSONArray(b.getString(0))))}}
            HistorySeries(stock,source,bars)
        }
    }
    override fun write(writes:List<HistoryWrite>)=transaction{writeRows(writes)}
    private fun writeRows(writes:List<HistoryWrite>) {
        val db=writableDatabase
        db.compileStatement("INSERT OR REPLACE INTO bars VALUES(?,?,?,?)").use {insert->
            writes.forEach {w->
                val s=w.series;val args=arrayOf(s.stock.symbol,s.provider)
                if(w.replace)db.delete("bars","symbol=? AND provider=?",args)
                s.bars.forEach {b->insert.clearBindings();insert.bindString(1,s.stock.symbol);insert.bindString(2,s.provider);insert.bindString(3,b.date.toString());insert.bindString(4,PoolCodec.bar(b).toString());insert.executeInsert()}
                val keep=if(s.stock.symbol in AfterCloseIndices.symbols)320 else 120
                db.execSQL("DELETE FROM bars WHERE symbol=? AND provider=? AND day NOT IN (SELECT day FROM bars WHERE symbol=? AND provider=? ORDER BY day DESC LIMIT $keep)",arrayOf(s.stock.symbol,s.provider,s.stock.symbol,s.provider))
                if(s.bars.isNotEmpty())db.execSQL("INSERT OR REPLACE INTO series VALUES(?,?,?,(SELECT MAX(day) FROM bars WHERE symbol=? AND provider=?))",arrayOf(s.stock.symbol,s.provider,PoolCodec.stock(s.stock).toString(),s.stock.symbol,s.provider))
            }
        }
    }
    override fun complete()=metadata("complete")?.let(PoolCodec::decode)
    override fun pending():PoolBatch? {
        val batch=metadata("pending")?.let(PoolCodec::decode)?:return null
        val outcomes=readableDatabase.rawQuery("SELECT symbol,value FROM outcomes",null).use{r->buildMap{while(r.moveToNext())put(r.getString(0),PoolCodec.outcome(JSONObject(r.getString(1))))}}
        return batch.copy(outcomes=outcomes)
    }
    override fun begin(batch:PoolBatch,clearQuotes:Boolean)=transaction {
        writableDatabase.delete("outcomes",null,null)
        metadata("pending",PoolCodec.encode(batch.copy(outcomes=emptyMap())))
        if(clearQuotes||metadata("quote-day")!=batch.basis.time.toLocalDate().toString()) {
            writableDatabase.delete("quotes",null,null);metadata("quote-day",batch.basis.time.toLocalDate().toString())
        }
        writeOutcomes(batch)
    }
    private fun writeOutcomes(batch:PoolBatch) {
        writableDatabase.compileStatement("INSERT OR REPLACE INTO outcomes VALUES(?,?)").use {stmt->batch.outcomes.forEach{(symbol,o)->stmt.bindString(1,symbol);stmt.bindString(2,PoolCodec.outcome(o).toString());stmt.executeInsert()}}
    }
    override fun checkpoint(batch:PoolBatch,writes:List<HistoryWrite>)=transaction{writeRows(writes);writeOutcomes(batch)}
    override fun finish(batch:PoolBatch)=transaction {
        require(batch.complete){"结果尚未完整"}
        metadata("complete",PoolCodec.encode(batch));writableDatabase.delete("metadata","key=?",arrayOf("pending"));writableDatabase.delete("outcomes",null,null)
    }
    override fun quotes(date:LocalDate):Map<String,String> {
        if(metadata("quote-day")!=date.toString())return emptyMap()
        return readableDatabase.rawQuery("SELECT symbol,value FROM quotes",null).use{r->buildMap{while(r.moveToNext())put(r.getString(0),r.getString(1))}}
    }
    override fun saveQuotes(date:LocalDate,quotes:Map<String,String>)=transaction {
        if(metadata("quote-day")!=date.toString()){writableDatabase.delete("quotes",null,null);metadata("quote-day",date.toString())}
        writableDatabase.compileStatement("INSERT OR REPLACE INTO quotes VALUES(?,?)").use {stmt->quotes.forEach{(s,q)->stmt.bindString(1,s);stmt.bindString(2,q);stmt.executeInsert()}}
    }
}
