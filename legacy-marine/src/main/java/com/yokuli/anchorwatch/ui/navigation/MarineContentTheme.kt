package com.yokuli.anchorwatch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.yokuli.anchorwatch.domain.model.AppLanguage
import com.yokuli.anchorwatch.ui.theme.YokuliTheme
@Composable fun MarineContentTheme(chinese:Boolean,accent:Color,light:Boolean,content:@Composable ()->Unit){
    YokuliTheme(accent=accent,light=light){CompositionLocalProvider(LocalAppLanguage provides if(chinese)AppLanguage.SIMPLIFIED_CHINESE else AppLanguage.ENGLISH){content()}}
}
