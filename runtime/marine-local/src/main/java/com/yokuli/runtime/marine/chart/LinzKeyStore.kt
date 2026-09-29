package com.yokuli.runtime.marine.chart

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** KeyStore 加密密钥仅 Core 私有落盘；从不放进公开资料目录/状态/来源 URL。 */
internal class LinzKeyStore(context:Context) {
    private val file=AtomicFile(File(context.noBackupFilesDir,"linz-api-key"))
    private val alias="yokuli.linz.api.v1"
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey(alias,null) as? SecretKey)?.let{return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun read():String {
        if(!file.baseFile.exists()&&!File(file.baseFile.path+".bak").exists())return ""
        val parts=file.openRead().bufferedReader().use{it.readText()}.split(':')
        require(parts.size==2&&parts.sumOf{it.length}<2048){"LINZ_KEY_UNREADABLE"}
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)))
            String(doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),Charsets.UTF_8)
        }
    }
    @Synchronized fun save(value:String) {
        require(value.isBlank()||value.matches(Regex("[A-Za-z0-9_-]{16,256}"))){"LINZ_KEY_INVALID"}
        if(value.isBlank()){file.delete();return}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,key())}
        val encrypted=cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val encoded=Base64.encodeToString(cipher.iv,Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP)
        val stream=file.startWrite()
        try{stream.write(encoded.toByteArray());stream.fd.sync();file.finishWrite(stream)}catch(error:Exception){file.failWrite(stream);throw error}
        check(read()==value){"LINZ_KEY_NOT_SAVED"}
    }
}
