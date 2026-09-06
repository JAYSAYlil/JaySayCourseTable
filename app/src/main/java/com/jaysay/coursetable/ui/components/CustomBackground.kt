package com.jaysay.coursetable.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jaysay.coursetable.data.preferences.CustomBackgroundStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberCustomBackground(revision: Long): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val image by produceState<ImageBitmap?>(initialValue = null, context, revision) {
        value = if (revision <= 0L) null else withContext(Dispatchers.IO) {
            runCatching { CustomBackgroundStore.decodeStored(context)?.asImageBitmap() }.getOrNull()
        }
    }
    return image
}

@Composable
fun CustomBackgroundImage(image: ImageBitmap, modifier: Modifier = Modifier) {
    Image(
        bitmap = image,
        contentDescription = null,
        // 毛玻璃底：壁纸整体轻虚化，半透明课程卡片叠上后呈现真实的磨砂观感；
        // blur 仅在 Android 12+ 生效，旧系统自动退化为原图（卡片自身透明度不变）。
        modifier = modifier.fillMaxSize().blur(14.dp),
        contentScale = ContentScale.Crop
    )
}
