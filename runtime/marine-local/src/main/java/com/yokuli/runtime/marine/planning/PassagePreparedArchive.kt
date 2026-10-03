package com.yokuli.runtime.marine.planning

import com.google.gson.Gson
import com.yokuli.runtime.contract.hardware.VirtualHostServices
import com.yokuli.runtime.contract.planning.PASSAGE_RULES_VERSION
import java.io.*
import java.util.UUID
import java.util.zip.CRC32

/** .yklpkg v3 的基础导航产物附件；不包含船型、避让、用户航线或后台作业。 */
internal object PassagePreparedArchive {
    private const val MAGIC=0x594e4133
    private const val MAX_FILE=64L*1024*1024
    private const val MAX_TOTAL=8L*1024*1024*1024
    private val names=Regex("[a-f0-9]{32}\\.nav")
    private val gson=Gson()

    /** 返回 false 表示该资料尚无已完成的基础拓扑；原始资料仍可正常导出。 */
    fun write(sourceDirectory:File,target:File,check:()->Unit,sourceIdentity:String?=null):Boolean {
        val directory=File(sourceDirectory,"runtime/navigation")
        val files=directory.listFiles().orEmpty().filter {file->
            check()
            if(!file.isFile||!names.matches(file.name)||header(file)?.let{it.policy=="base-water-semantics-v2"&&it.rules==PASSAGE_RULES_VERSION&&(sourceIdentity==null||it.source==sourceIdentity)}!=true)false else
                try{validate(file,check);true}catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}catch(_:Exception){false}
        }.sortedBy{it.name}
        if(files.isEmpty())return false
        require(files.size<=32_768&&files.sumOf{it.length()}<=MAX_TOTAL){"NAVIGATION_ARCHIVE_LIMIT"}
        val stage=File(target.parentFile,".${target.name}.${UUID.randomUUID()}.pending")
        try {
            target.parentFile?.mkdirs()
            FileOutputStream(stage).use {file->
                val out=DataOutputStream(file.buffered())
                out.writeInt(MAGIC);out.writeInt(1);out.writeInt(files.size)
                for(source in files) {
                    check();VirtualHostServices.beforeRead();validate(source,check)
                    out.writeUTF(source.name);out.writeLong(source.length())
                    val crc=CRC32();var count=0L
                    source.inputStream().buffered().use {input->
                        val buffer=ByteArray(64*1024)
                        while(true) {
                            check();val size=input.read(buffer);if(size<0)break
                            count+=size;require(count<=MAX_FILE);crc.update(buffer,0,size);out.write(buffer,0,size)
                        }
                    }
                    require(count==source.length()){ "NAVIGATION_PRODUCT_CHANGED" }
                    out.writeLong(crc.value)
                }
                out.flush();file.fd.sync()
            }
            check();VirtualHostServices.beforeWrite();require(stage.renameTo(target)){"NAVIGATION_ARCHIVE_PUBLISH_FAILED"}
            return true
        }finally {stage.delete()}
    }

    /** 安装到尚未发布的事实库版本目录；路径由本代码决定，附件不能写任意路径。 */
    fun install(archive:File,targetDirectory:File,check:()->Unit) {
        val directory=File(targetDirectory,"runtime/navigation").apply{mkdirs()}
        val staged=ArrayList<Pair<File,File>>()
        try {
            DataInputStream(archive.inputStream().buffered()).use {input->
                check();VirtualHostServices.beforeRead()
                require(input.readInt()==MAGIC&&input.readInt()==1){"NAVIGATION_ARCHIVE_VERSION"}
                val count=input.readInt();require(count in 1..32_768)
                val seen=HashSet<String>();var total=0L
                repeat(count) {
                    check()
                    val name=input.readUTF();require(names.matches(name)&&seen.add(name)){"NAVIGATION_ARCHIVE_PATH"}
                    val size=input.readLong();require(size in 20..MAX_FILE);total+=size;require(total<=MAX_TOTAL)
                    require(directory.usableSpace>size+8L*1024*1024){"CHART_STORAGE_FULL"}
                    val stage=File(directory,".${UUID.randomUUID()}.pending");staged+=stage to File(directory,name)
                    val crc=CRC32()
                    FileOutputStream(stage).use {file->
                        val out=file.buffered();val buffer=ByteArray(64*1024);var remaining=size
                        while(remaining>0) {
                            check();VirtualHostServices.beforeWrite()
                            val bytes=input.read(buffer,0,minOf(buffer.size.toLong(),remaining).toInt())
                            require(bytes>0){"NAVIGATION_ARCHIVE_TRUNCATED"}
                            crc.update(buffer,0,bytes);out.write(buffer,0,bytes);remaining-=bytes
                        }
                        out.flush();file.fd.sync()
                    }
                    require(input.readLong()==crc.value){"NAVIGATION_ARCHIVE_CHECKSUM"}
                    val metadata=validate(stage,check)
                    require(metadata.policy in setOf("base-water-topology-v1","base-water-semantics-v2")){"NAVIGATION_ARCHIVE_PRIVATE_PRODUCT"}
                    require(name==passageHash(listOf(if(metadata.schema==2)"navigation-region-2"else"navigation-region-3",metadata.source,metadata.policy,metadata.region,metadata.rules))+".nav"){
                        "NAVIGATION_ARCHIVE_IDENTITY"
                    }
                    // 旧基础 WKB 附件仍可随包导入；它不具备本版船型语义，不发布成可用新产物。
                    if(metadata.schema!=3||metadata.rules!=PASSAGE_RULES_VERSION){stage.delete();staged.removeAt(staged.lastIndex)}
                }
                require(input.read()==-1){"NAVIGATION_ARCHIVE_TRAILING_DATA"}
            }
            // 所有文件先验证，再发布；外层版本事务负责失败时撤销本次新版本。
            for((stage,target) in staged){check();VirtualHostServices.beforeWrite();require(stage.renameTo(target)){"NAVIGATION_PRODUCT_PUBLISH_FAILED"}}
        }finally {staged.forEach{it.first.delete()}}
    }
    private fun header(file:File):PassageRegionHeader?=try {
        if(file.length() !in 20..MAX_FILE)null else DataInputStream(file.inputStream().buffered()).use {input->
            require(input.readInt()==0x594b4e31&&input.readInt() in 2..3)
            val length=input.readInt();require(length in 1..8*1024*1024)
            val bytes=ByteArray(length);input.readFully(bytes)
            require(input.readLong()==CRC32().apply{update(bytes)}.value)
            gson.fromJson(String(bytes,Charsets.UTF_8),PassageRegionHeader::class.java)
        }
    }catch(_:Exception){null}
    private fun validate(file:File,check:()->Unit):PassageRegionHeader {
        val metadata=requireNotNull(header(file)){"NAVIGATION_PRODUCT_HEADER"}
        require(metadata.schema in 2..3&&metadata.rules.length in 1..256&&metadata.source.length in 1..256)
        require(metadata.region.x in 0 until PassageRegionId.COLUMNS&&metadata.region.y in 0 until PassageRegionId.ROWS)
        require(metadata.componentCount in 0..4096&&metadata.evidence.size<=8192&&metadata.portals.size<=16384)
        val size=file.length();require(size in 20..MAX_FILE)
        val crc=CRC32()
        DataInputStream(file.inputStream().buffered()).use {input->
            val buffer=ByteArray(64*1024);var remaining=size-8
            while(remaining>0) {
                check();val count=input.read(buffer,0,minOf(buffer.size.toLong(),remaining).toInt())
                require(count>0){"NAVIGATION_PRODUCT_TRUNCATED"};crc.update(buffer,0,count);remaining-=count
            }
            require(input.readLong()==crc.value&&input.read()==-1){"NAVIGATION_PRODUCT_CHECKSUM"}
        }
        return metadata
    }
}
