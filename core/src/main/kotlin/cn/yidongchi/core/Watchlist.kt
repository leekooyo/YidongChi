package cn.yidongchi.core

object Watchlist {
    fun symbolFrom(text:String):String? {
        val code=Regex("(?<!\\d)\\d{6}(?!\\d)").find(text)?.value?:return null
        val exchange=when {code.startsWith("6")||code.startsWith("900")->"sh";code.startsWith("0")||code.startsWith("3")||code.startsWith("200")->"sz";else->return null}
        return exchange+code
    }
    fun boardFor(symbol:String):Board=when {
        symbol.startsWith("sh688")||symbol.startsWith("sh689")->Board.STAR
        symbol.startsWith("sz30")->Board.CHINEXT
        symbol.startsWith("sh900")->Board.SH_B
        symbol.startsWith("sz200")->Board.SZ_B
        symbol.startsWith("sh")->Board.SH_MAIN
        else->Board.SZ_MAIN
    }
    fun merge(existing:List<WatchItem>,incoming:List<WatchItem>)=(existing+incoming).distinctBy {it.symbol}
    fun mergeAlerts(existing:List<AlertItem>,incoming:List<AlertItem>)=(existing+incoming).distinctBy {it.key}.takeLast(200)
}
