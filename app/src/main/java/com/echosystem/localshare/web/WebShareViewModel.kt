package com.echosystem.localshare.web

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.google.zxing.BarcodeFormat
import com.echosystem.localshare.security.PairingManager
import com.echosystem.localshare.security.TrustManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WebShareViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val trustManager: TrustManager,
    val pairingManager: PairingManager
) : ViewModel() {

    private val server = WebShareServer(context)
    
    private val _webShareUrl = MutableStateFlow("")
    val webShareUrl: StateFlow<String> = _webShareUrl.asStateFlow()

    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmapState: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()

    private val _activeSessions = MutableStateFlow<List<String>>(emptyList())
    val activeSessions: StateFlow<List<String>> = _activeSessions.asStateFlow()

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    // Backwards compatibility property mapping
    var qrBitmap: Bitmap? by mutableStateOf(null)
        private set

    init {
        viewModelScope.launch {
            while (true) {
                if (_isServerRunning.value) {
                    _activeSessions.value = server.getActiveSessionIps()
                }
                kotlinx.coroutines.delay(3000)
            }
        }
    }

    fun startWebShare() {
        viewModelScope.launch {
            try {
                server.start()
                _isServerRunning.value = true
                generateQr()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopWebShare() {
        try {
            server.stop()
            _isServerRunning.value = false
            _webShareUrl.value = ""
            _qrBitmap.value = null
            qrBitmap = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateQr() {
        val ip = NetworkUtils.getLocalIpAddress()
        val url = "http://$ip:8989"
        _webShareUrl.value = url
        try {
            val encoder = BarcodeEncoder()
            val bitmap = encoder.encodeBitmap(url, BarcodeFormat.QR_CODE, 600, 600)
            _qrBitmap.value = bitmap
            qrBitmap = bitmap
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopWebShare()
    }
}
