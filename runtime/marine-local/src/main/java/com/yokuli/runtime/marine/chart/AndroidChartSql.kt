package com.yokuli.runtime.marine.chart

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import java.io.File

/** 薄平台适配；查询/写入/格式解释仍由同一份共享编译代码完成。 */
internal class AndroidChartSql(private val db:SQLiteDatabase,private val ownsConnection:Boolean=false):ChartSql {
    override fun rawQuery(sql:String,args:Array<out String>?):ChartSqlRows=Rows(db.rawQuery(sql,args))
    override fun execSQL(sql:String,args:Array<out Any?>) {
        try { if(args.isEmpty())db.execSQL(sql) else db.execSQL(sql,args) }
        catch(error:SQLiteException){throw ChartSqlException(error.message,error)}
    }
    override fun beginTransaction()=db.beginTransaction()
    override fun setTransactionSuccessful()=db.setTransactionSuccessful()
    override fun endTransaction()=db.endTransaction()
    override fun close(){if(ownsConnection)db.close()}
    private class Rows(private val rows:Cursor):ChartSqlRows {
        override fun moveToNext()=rows.moveToNext()
        override fun moveToFirst()=rows.moveToFirst()
        override fun getString(index:Int):String=rows.getString(index)
        override fun getInt(index:Int)=rows.getInt(index)
        override fun getLong(index:Int)=rows.getLong(index)
        override fun getDouble(index:Int)=rows.getDouble(index)
        override fun getBlob(index:Int)=rows.getBlob(index)
        override fun isNull(index:Int)=rows.isNull(index)
        override fun getType(index:Int)=rows.getType(index)
        override fun getColumnIndexOrThrow(name:String)=rows.getColumnIndexOrThrow(name)
        override fun close()=rows.close()
    }
    companion object:ChartSqlFactory {
        override fun openReadOnly(file:File)=AndroidChartSql(SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS),true)
        override fun create(file:File)=AndroidChartSql(SQLiteDatabase.openOrCreateDatabase(file,null),true)
    }
}
