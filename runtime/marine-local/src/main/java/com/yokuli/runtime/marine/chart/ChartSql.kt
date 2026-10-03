package com.yokuli.runtime.marine.chart

import java.io.Closeable
import java.io.File

/** 编译边界只需要有界游标、绑定参数与事务；业务语义不依赖 Android/JDBC。 */
internal interface ChartSql:Closeable {
    fun rawQuery(sql:String,args:Array<out String>?=null):ChartSqlRows
    fun execSQL(sql:String,args:Array<out Any?> = emptyArray())
    fun beginTransaction()
    fun setTransactionSuccessful()
    fun endTransaction()
    fun insert(table:String,values:Map<String,Any?>) {
        require(table.matches(Regex("[a-z_]+"))&&values.keys.all{it.matches(Regex("[a-z_]+"))})
        execSQL("INSERT INTO $table (${values.keys.joinToString(",")}) VALUES (${values.keys.joinToString(","){"?"}})",values.values.toTypedArray())
    }
}
internal interface ChartSqlRows:Closeable {
    fun moveToNext():Boolean
    fun moveToFirst():Boolean
    fun getString(index:Int):String
    fun nullableString(index:Int):String?=if(isNull(index))null else getString(index)
    fun getInt(index:Int):Int
    fun getLong(index:Int):Long
    fun getDouble(index:Int):Double
    fun getBlob(index:Int):ByteArray
    fun isNull(index:Int):Boolean
    fun getType(index:Int):Int
    fun getColumnIndexOrThrow(name:String):Int
    companion object { const val FIELD_TYPE_INTEGER=1 }
}
internal interface ChartSqlFactory {
    fun openReadOnly(file:File):ChartSql
    fun create(file:File):ChartSql
}
internal class ChartSqlException(message:String?,cause:Throwable):RuntimeException(message,cause)
