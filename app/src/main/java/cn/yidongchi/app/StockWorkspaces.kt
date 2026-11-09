package cn.yidongchi.app

import androidx.compose.runtime.*
import cn.yidongchi.core.*
import kotlin.reflect.KProperty

class StoredInput<T>(initial:T,private val save:(T)->Unit) {
    private val state=mutableStateOf(initial)
    var value:T
        get()=state.value
        set(value){state.value=value;save(value)}
    operator fun getValue(owner:Any?,property:KProperty<*>)=value
    operator fun setValue(owner:Any?,property:KProperty<*>,value:T){this.value=value}
}

class StockWorkspace(
    val key:String,
    legacySymbol:String,
    private val read:(String,String)->String,
    private val write:(String,String)->Unit
) {
    var symbol by mutableStateOf(read("selected-$key",legacySymbol));private set
    var history by mutableStateOf(read("recent-$key",read("recent","")).split('|').filter{it.isNotBlank()});private set
    var searchText by mutableStateOf("");private set
    var suggestions by mutableStateOf(emptyList<Stock>());private set
    private val texts=mutableMapOf<String,StoredInput<String>>()
    private val flags=mutableMapOf<String,StoredInput<Boolean>>()
    init {
        // 固定首次迁移结果，避免未操作的页面下次仍跟随旧selected变化。
        write("selected-$key",symbol);write("recent-$key",history.joinToString("|"))
    }
    fun select(symbol:String) {
        this.symbol=symbol;write("selected-$key",symbol)
        history=(listOf(symbol)+history).distinct().take(8);write("recent-$key",history.joinToString("|"))
        if(key=="calculation")write("selected",symbol)
    }
    fun query(text:String){searchText=text;suggestions=emptyList()}
    fun acceptResults(query:String,stocks:List<Stock>){if(searchText==query)suggestions=stocks}
    fun clearHistory(){history=emptyList();write("recent-$key","")}
    fun text(name:String,default:String="",legacyKey:String?=null):StoredInput<String> {
        val storageKey="input-$key-$symbol-$name"
        return texts.getOrPut(storageKey){StoredInput(read(storageKey,legacyKey?.let{read(it,default)}?:default)){write(storageKey,it)}}
    }
    fun flag(name:String,default:Boolean=false):StoredInput<Boolean> {
        val storageKey="input-$key-$symbol-$name"
        return flags.getOrPut(storageKey){StoredInput(read(storageKey,default.toString())=="true"){write(storageKey,it.toString())}}
    }
}

class StockWorkspaces(read:(String,String)->String,private val write:(String,String)->Unit) {
    private val legacy=read("selected","sh600000")
    val calculation=StockWorkspace("calculation",legacy,read,write)
    val forecast=StockWorkspace("forecast",legacy,read,write)
    val warnings=StockWorkspace("warnings",legacy,read,write)
    val all=listOf(calculation,forecast,warnings)
    fun warningSymbols(watches:List<WatchItem>,showAll:Boolean)=if(showAll)watches.map{it.symbol}.distinct()else listOf(warnings.symbol)
    fun clearHistory(){all.forEach{it.clearHistory()};write("recent","")}
}
