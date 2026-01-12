package com.hanto.ridesync.ui.dashboard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.hanto.ridesync.databinding.ActivityDashboardBinding
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding

    private val viewModel: DashboardViewModel by viewModels()

    // 1. 뮤직 플레이어
    private var player: ExoPlayer? = null

    // 2. 인터컴 시뮬레이터 (TTS)
    private lateinit var tts: TextToSpeech

    // 3. 오디오 매니저 (시스템 포커스 제어용)
    private lateinit var audioManager: AudioManager

    private var focusRequest: AudioFocusRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 시스템 서비스 초기화
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        initializeTts()
        initializePlayer()
        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        val name = intent.getStringExtra("DEVICE_NAME") ?: "Unknown Device"
        val address = intent.getStringExtra("DEVICE_ADDRESS") ?: "00:00:00:00:00:00"

        binding.tvDeviceName.text = "Connected: $name"

        // 연결 상태 및 주소
        binding.tvConnectionStatus.text = "Status: Active ($address)"
    }

    // --- [Step 1] ExoPlayer (Music) 설정 ---
    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build()

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // handleAudioFocus = true: 포커스 뺏기면(Ducking 신호 오면) ExoPlayer가 알아서 볼륨 줄임
        player?.setAudioAttributes(audioAttributes, true)

        // 저작권 없는 무료 음악 샘플 URL
        val mediaItem =
            MediaItem.fromUri("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3")
        player?.setMediaItem(mediaItem)
        player?.prepare()
    }

    // --- [Step 2] TTS (Intercom) 설정 ---
    private fun initializeTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US

                // [고급] TTS가 끝났는지 감지하는 리스너
                // 말이 끝나면 오디오 포커스를 반납(Abandon)해야 음악이 다시 커짐
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        // TTS 끝남 -> 포커스 반납 -> 음악 볼륨 자동 복구
                        abandonIntercomFocus()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                    }
                })
            }
        }
    }

    private fun setupListeners() {
        // [음악 재생 버튼]
        binding.btnPlayMusic.setOnClickListener {
            if (player?.isPlaying == true) {
                player?.pause()
                binding.btnPlayMusic.text = "Play Music"
            } else {
                player?.play()
                binding.btnPlayMusic.text = "Pause Music"
            }
        }

        // [인터컴 시뮬레이션 버튼]
        binding.btnIntercom.setOnClickListener {
            simulateIntercomCall()
        }

        binding.btnDisconnect.setOnClickListener {
            viewModel.disconnectDevice()
            Toast.makeText(this, "Disconnected from device", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // 오디오 포커스 변경 리스너 (API 26 미만 호환용)
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // 영구적으로 포커스를 잃음 (예: 전화 옴) -> 필요시 로직 추가
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // 잠시 잃음
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                // 다시 얻음
            }
        }
    }

    private fun simulateIntercomCall() {
        val result: Int

        // 1. 오디오 포커스 요청 (버전 분기 처리)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // [API 26 이상] AudioFocusRequest 빌더 사용
            val mFocusRequest =
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION) // 용도: 음성 통신
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(focusChangeListener) // 리스너 등록
                    .build()

            this.focusRequest = mFocusRequest // 반납을 위해 멤버 변수에 저장
            result = audioManager.requestAudioFocus(mFocusRequest)

        } else {
            // [API 26 미만] 구형 방식 사용 (StreamType 지정)
            @Suppress("DEPRECATION")
            result = audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }

        // 2. 결과 처리
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Toast.makeText(this, "Intercom Active (Ducking Music)", Toast.LENGTH_SHORT).show()

            // 포커스 획득 성공 시 말하기 시작
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "IntercomMessage")

            tts.speak(
                "Intercom connected. Rider A is speaking.",
                TextToSpeech.QUEUE_FLUSH,
                params,
                "IntercomMessage"
            )
        }
    }

    // 포커스 반납 (음악 복구를 위해 필수)
    private fun abandonIntercomFocus() {
        val result: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // [API 26 이상] 저장해둔 AudioFocusRequest 객체로 반납
            result = focusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            } ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
        } else {
            // [API 26 미만] 리스너 객체로 반납
            @Suppress("DEPRECATION")
            result = audioManager.abandonAudioFocus(focusChangeListener)
        }

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            runOnUiThread {
                Toast.makeText(this, "Intercom Ended (Music Restore)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        tts.shutdown()
    }

}