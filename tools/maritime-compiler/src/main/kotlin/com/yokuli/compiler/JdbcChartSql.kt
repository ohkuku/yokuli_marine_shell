package com.yokuli.compiler

import com.yokuli.runtime.marine.chart.*
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

/** 桌面 SQLite 适配器。源库以 URI mode=ro 打开；目标事务提交后才可原子发布。 */
internal class JdbcChartSql(private val connection:Connection):ChartSql {
    private var successful=false
    private val writes=object:LinkedHashMap<String,PreparedStatement>(32,.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,PreparedStatement>?):Boolean {
            if(size<=48)return false
            eldest?.value?.close();return true
        }
    }
    override fun rawQuery(sql:String,args:Array<out String>?):ChartSqlRows {
        val statement=connection.prepareStatement(sql)
        try {
            args?.forEachIndexed {index,value->statement.setString(index+1,value)}
            return Rows(statement,statement.executeQuery())
        }catch(error:Throwable){statement.close();throw error}
    }
    override fun execSQL(sql:String,args:Array<out Any?>) {
        try {
            val statement=writes.getOrPut(sql){connection.prepareStatement(sql)}
            statement.clearParameters()
            args.forEachIndexed {index,value->when(value){
                is ByteArray->statement.setBytes(index+1,value)
                else->statement.setObject(index+1,value)
            }}
            statement.execute().let {if(it)statement.resultSet?.close()}
        }catch(error:SQLException){throw ChartSqlException(error.message,error)}
    }
    override fun beginTransaction(){check(connection.autoCommit);connection.autoCommit=false;successful=false}
    override fun setTransactionSuccessful(){check(!connection.autoCommit);successful=true}
    override fun endTransaction(){try{if(successful)connection.commit()else connection.rollback()}finally{connection.autoCommit=true;successful=false}}
    override fun close(){try{writes.values.forEach{it.close()}}finally{connection.close()}}
    private class Rows(private val statement:PreparedStatement,private val rows:ResultSet):ChartSqlRows {
        private var started=false
        private var available=false
        override fun moveToNext():Boolean {started=true;available=rows.next();return available}
        override fun moveToFirst():Boolean {check(!started){"Forward-only chart cursor cannot be rewound"};return moveToNext()}
        override fun getString(index:Int):String=rows.getString(index+1)
        override fun getInt(index:Int)=rows.getInt(index+1)
        override fun getLong(index:Int)=rows.getLong(index+1)
        override fun getDouble(index:Int)=rows.getDouble(index+1)
        override fun getBlob(index:Int):ByteArray=rows.getBytes(index+1)
        override fun isNull(index:Int)=rows.getObject(index+1)==null
        override fun getType(index:Int):Int=when(rows.getObject(index+1)){null->0;is ByteArray->4;is Double,is Float->2;is Number->1;else->3}
        override fun getColumnIndexOrThrow(name:String)=rows.findColumn(name)-1
        override fun close(){try{rows.close()}finally{statement.close()}}
    }
    companion object:ChartSqlFactory {
        init { Class.forName("org.sqlite.JDBC") }
        override fun openReadOnly(file:File):JdbcChartSql {
            require(file.isFile){"Source does not exist: $file"}
            return JdbcChartSql(DriverManager.getConnection("jdbc:sqlite:${file.canonicalFile.toURI()}?mode=ro")).also{it.execSQL("PRAGMA query_only=ON")}
        }
        override fun create(file:File)=JdbcChartSql(DriverManager.getConnection("jdbc:sqlite:${file.canonicalPath}"))
    }
}
