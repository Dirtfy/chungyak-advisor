package com.chungyak.advisor.map

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.chungyak.advisor.BuildConfig
import com.chungyak.advisor.R
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import org.osmdroid.util.GeoPoint as OsmPoint

/**
 * 상세 화면의 "위치" 카드: 공급위치 주소 + 지도 미리보기(osmdroid MapView, 지오코딩 좌표에 마커) + [지도 앱에서 열기].
 * 좌표를 못 찾거나 오프라인이면 지도 없이 주소·안내·버튼만 보인다(빈 지도 X, 크래시 X).
 * 지도는 스크롤 화면 안의 미리보기다(끌기·확대 없음). 누르면 지도 앱이 열린다.
 */
@Composable
fun LocationCard(name: String, address: String) {
    if (address.isBlank()) return
    val ctx = LocalContext.current
    val repo = remember { GeocodeRepository(ctx) }
    var attempt by remember { mutableIntStateOf(0) }
    val state by produceState<GeoResult?>(repo.cached(address), address, attempt) {
        if (value == null || attempt > 0) value = repo.resolve(address)
    }
    val online = remember(attempt) { isOnline(ctx) }
    LocationCardContent(
        address = address,
        state = state,
        online = online,
        onOpen = { openInMapApp(ctx, address) },
        onRetry = { attempt++ },
        map = { MapPreview(name, it) },
    )
}

/**
 * 배치 규칙(v0.6.1): 지도 영역 안에는 지도·마커와 작은 OSM 저작권 표기만 둔다. 설명 문구와 버튼은 지도 아래
 * 별도 줄이라 지도와 겹치지 않는다. 지도는 [clipToBounds]로 잘라야 한다 — osmdroid는 가장자리 타일을 통째로
 * 그리는데 Compose의 AndroidView는 자르지 않아서, 타일이 위아래 글자·버튼을 덮었다(v0.6.0 버그).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LocationCardContent(
    address: String,
    state: GeoResult?,
    online: Boolean,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    map: @Composable (GeoPoint) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().testTag(TAG_CARD),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(CARD_PADDING)) {
            Text("위치", style = MaterialTheme.typography.titleMedium)
            Text(
                address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(TAG_ADDRESS),
            )
            Spacer(Modifier.height(10.dp))
            val note = when {
                !online -> "오프라인이라 지도를 불러올 수 없습니다. 연결되면 [다시 시도]를 누르세요."
                state == null -> "위치를 찾는 중…"
                state is GeoResult.Found ->
                    if (state.point.exact) null
                    else "대략적 위치('${state.point.query}' 중심)입니다. 정확한 위치는 지도 앱에서 확인하세요."
                state is GeoResult.Offline -> "네트워크 문제로 위치를 찾지 못했습니다. [다시 시도]하거나 지도 앱에서 확인하세요."
                else -> "이 주소는 지도에서 위치를 찾지 못했습니다. 지도 앱에서 확인하세요."
            }
            if (online && state is GeoResult.Found) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(MAP_HEIGHT)
                        .clip(RoundedCornerShape(14.dp))
                        .testTag(TAG_MAP),
                ) {
                    map(state.point)
                    // 지도 위 터치는 이 레이어가 받아 지도 앱으로 넘긴다(미리보기 안에서 끌기·확대 없음).
                    Box(Modifier.matchParentSize().clickable(onClick = onOpen))
                    // OSM 라이선스상 필요한 표기만 지도 위 오른쪽 아래에 작게(마커는 가운데라 가리지 않음).
                    Text(
                        "© OpenStreetMap",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                        color = Color.Black.copy(alpha = 0.75f),
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(Color.White.copy(alpha = 0.6f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                            .testTag(TAG_ATTRIBUTION),
                    )
                }
                if (note != null) Spacer(Modifier.height(8.dp))
            }
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(TAG_NOTE),
                )
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(
                Modifier.fillMaxWidth().testTag(TAG_BUTTONS),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onOpen, modifier = Modifier.testTag(TAG_OPEN)) { Text("지도 앱에서 열기") }
                if (!online || state is GeoResult.Offline) {
                    TextButton(onClick = onRetry, modifier = Modifier.testTag(TAG_RETRY)) { Text("다시 시도") }
                }
            }
        }
    }
}

internal val MAP_HEIGHT = 200.dp
internal val CARD_PADDING = 18.dp
internal const val TAG_CARD = "loc_card"
internal const val TAG_ADDRESS = "loc_address"
internal const val TAG_MAP = "loc_map"
internal const val TAG_ATTRIBUTION = "loc_attribution"
internal const val TAG_NOTE = "loc_note"
internal const val TAG_BUTTONS = "loc_buttons"
internal const val TAG_OPEN = "loc_open"
internal const val TAG_RETRY = "loc_retry"

@Composable
internal fun MapPreview(name: String, p: GeoPoint) {
    AndroidView(
        modifier = Modifier.fillMaxSize().clipToBounds(),
        factory = { ctx ->
            configureOsmdroid(ctx)
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(false)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                isClickable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
        },
        update = { map ->
            val pt = OsmPoint(p.lat, p.lon)
            map.controller.setZoom(if (p.exact) 16.0 else 14.0)
            map.controller.setCenter(pt)
            map.overlays.clear()
            map.overlays.add(Marker(map).apply {
                position = pt
                title = name
                icon = ContextCompat.getDrawable(map.context, R.drawable.ic_map_pin)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { _, _ -> false }
            })
            map.invalidate()
        },
        onRelease = { it.onDetach() },
    )
}

/** OSM 타일 이용 정책: 앱 식별 User-Agent 필수. 타일 캐시는 앱 캐시 폴더(권한 불필요, 백업 제외). */
private fun configureOsmdroid(ctx: Context) {
    Configuration.getInstance().apply {
        userAgentValue = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}"
        val base = File(ctx.cacheDir, "osmdroid")
        osmdroidBasePath = base
        osmdroidTileCache = File(base, "tiles")
    }
}

private fun isOnline(ctx: Context): Boolean {
    val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/** geo: 인텐트 — 설치된 지도 앱(카카오맵·네이버지도·구글지도 등)이 주소 검색으로 열린다. 없으면 웹 지도. */
fun mapAppIntent(address: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(AddressQuery.forMapApp(address))))

fun openInMapApp(ctx: Context, address: String) {
    val q = AddressQuery.forMapApp(address)
    try {
        ctx.startActivity(mapAppIntent(address).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://map.kakao.com/link/search/" + Uri.encode(q)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
