package com.example.cameraplayback.ui.view

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.cameraplayback.databinding.FragmentPlaybackCameraBinding
import com.example.cameraplayback.ui.view.camera.viewmodel.MainViewModel
import com.example.cameraplayback.utils.Constant
import com.example.cameraplayback.utils.Constant.ConfigCamera.VNPT_CAMERA_RECORD_RESOLUTION_FHD
import com.example.cameraplayback.utils.Constant.ConfigCamera.VNPT_CAMERA_RECORD_RESOLUTION_SD
import com.vnpttech.opengl.MGLSurfaceView

// timeStamp 1720887363000; getStartOfDay(timeStamp) 1720803600000
class PlaybackCameraFragment : Fragment(),
    MGLSurfaceView.ISwipeTouchListener {
    private var _binding: FragmentPlaybackCameraBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by viewModels()
    private var timeStamp = 1720887363000

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaybackCameraBinding.inflate(inflater, container, false)
        addObserver()
        onCommonViewLoaded()

        return binding.root
    }

    private fun addObserver() {
        // Observe state camera
        mainViewModel.apply {
            cameraState.observe(viewLifecycleOwner) { state ->
                setViewStateCamera(state)
            }
            compositeDisposable.add(
                dataVideoStream
                    .subscribeOn(schedulerProvider.newThread())
                    .observeOn(schedulerProvider.newThread())
                    .subscribe { data ->
                        binding.glSurfaceview.setYUVData(
                            data.widthData,
                            data.heightData,
                            data.byteArray_Data_fy,
                            data.byteArray_Data_fu,
                            data.byteArray_Data_fv
                        )
                    }
            )

            // Set video record quality
            videoRecordQuality.observe(viewLifecycleOwner) { quality ->
                setQualityValue(quality)
            }

            // Observe state play video playback
            playbackState.observe(viewLifecycleOwner) { state ->
                setViewStatePlayFilePlayback(state)
            }
        }


    }

    /**
     * Cập nhật view theo trạng thái play file playback: play, pause, seek,...
     */
    private fun setViewStatePlayFilePlayback(state: Constant.PlaybackSdCardStateUI) {
        when (state) {
            Constant.PlaybackSdCardStateUI.PLAYING -> {
                setViewPlayingPlayback()
            }

            Constant.PlaybackSdCardStateUI.CONTINUE_PLAY -> {
//                setViewNextFilePlayback()
            }

            Constant.PlaybackSdCardStateUI.PAUSING -> {
//                setViewPausingEvent()
            }

            Constant.PlaybackSdCardStateUI.PAUSED -> {
//                setViewPausedEvent()
            }

            Constant.PlaybackSdCardStateUI.SEEK -> {
//                setViewSeekVideoPlayback()
            }

            Constant.PlaybackSdCardStateUI.NEXT_FILE -> {
//                setViewNextFilePlayback()
            }

            Constant.PlaybackSdCardStateUI.SELECT_DAY -> {
                // UI giống với state NEXT_FILE
//                setViewNextFilePlayback()
            }

            Constant.PlaybackSdCardStateUI.EMPTY_FILE,
            Constant.PlaybackSdCardStateUI.EMPTY_FILE_ALL_DAY -> {
//                setViewEmptyFilePlayback()
            }

            Constant.PlaybackSdCardStateUI.SCAN -> {
//                updateViewWhenScanEvent()
            }

            Constant.PlaybackSdCardStateUI.NEXT_DAY -> {
//                setViewNextFilePlayback()
//                updateViewWhenScanEvent()
            }

            Constant.PlaybackSdCardStateUI.COMPLETE -> {
//                setViewPlayComplete()
            }

            Constant.PlaybackSdCardStateUI.EMPTY_FILE_NOTIFICATION -> {
//                setViewEmptyFilePlaybackNotification()
            }
        }
    }

    /**
     * Set view khi playback ở trạng thái playing
     */
    private fun setViewPlayingPlayback() {
        binding.apply {
            glSurfaceview.show()
        }
    }

    fun View.show() {
        this.visibility = View.VISIBLE
    }

    /**
     * Set quality value
     */
    private fun setQualityValue(quality: Int) {
        when (quality) {
            // Độ phân giải SD
            VNPT_CAMERA_RECORD_RESOLUTION_SD -> {
                binding.apply {
//                    tvChangeQualityVideo.text = getString(R.string.sd_video_quality)
//                    tvLandQualityVideo.text = getString(R.string.sd_video_quality)
                }
            }

            // Độ phân giải FHD
            VNPT_CAMERA_RECORD_RESOLUTION_FHD -> {
                binding.apply {
//                    tvChangeQualityVideo.text = getString(R.string.fhd_video_quality)
//                    tvLandQualityVideo.text = getString(R.string.fhd_video_quality)
                }
            }
        }
    }

    /**
     * Set view tương ứng với từng state của camera
     */
    private fun setViewStateCamera(stateCamera: Constant.CameraState) {
        when (stateCamera) {
            Constant.CameraState.Init -> {
//                setViewInitializedCamera()
            }

            Constant.CameraState.CameraOnline -> {
//                binding.apply {
//                    if (ivReceiveAudio.isSelected || ivLandReceiveAudio.isSelected) {
//                        onOffAudioPlayback(true)
//                    }
//                }
            }

            Constant.CameraState.CameraConnectionTimeOut,
            Constant.CameraState.CameraOffline -> {
//                setViewCameraOffline()
            }

            Constant.CameraState.CameraLossConnection -> {
//                setViewInitializedCamera()
            }

            else -> {}
        }
    }

    private fun onCommonViewLoaded() {
        initGlSurfaceView()

        mainViewModel.apply {
            if (timeStamp != 0L) {
                //Trỏ đến nơi phát hiện chuyển động
                setTimeSeekValue(timeStamp)
            }

            prepareAndInitializeCamera(getDataCamera())
        }


    }

    /**
     * Initialize GLSurfaceview
     */
    private fun initGlSurfaceView() {
        binding.apply {
            glSurfaceview.apply {
                setMinScale(1f)
                setMaxScale(10f)
                setScrollView(scroolview)
                setSwipeListener(this@PlaybackCameraFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onSwipeRight() {
    }

    override fun onSwipeLeft() {
    }

    override fun onSwipeTop() {
    }

    override fun onSwipeBottom() {
    }

    override fun onSingleTapUp(p0: MotionEvent?) {
    }
}

