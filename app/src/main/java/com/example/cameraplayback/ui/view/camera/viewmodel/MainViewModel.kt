package com.example.cameraplayback.ui.view.camera.viewmodel

import com.example.cameraplayback.manager.VNTTCamManagerImpl
import android.util.Log
import android.util.LongSparseArray
import androidx.core.util.forEach
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cameraplayback.di.scheduler.SchedulerProviderImpl
import com.example.cameraplayback.manager.SessionCameraManager
import com.example.cameraplayback.manager.VNTTCamManager
import com.example.cameraplayback.model.Device
import com.example.cameraplayback.model.PlaybackFileModel
import com.example.cameraplayback.model.VideoStreamData
import com.example.cameraplayback.utils.Constant
import com.example.cameraplayback.utils.CryptoAES.Companion.decrypt
import com.vnpttech.ipcamera.Constants
import com.vnpttech.model.DeviceInfo
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.lang.StringUtils
import vn.vnpt.ONEHome.di.component.scheduler.SchedulerProvider

class MainViewModel : ViewModel() {
    private val _device = MutableLiveData<Device>()
    val device: LiveData<Device> get() = _device

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> get() = _error
    private var devicesCamera: Device? = null
    private var seekTimeValue: Long = 0
    private var idCam = 0

    private var uidCam = "VNTTC-000237-DXKPK"

    private var passCam = Constant.EMPTY_STRING

    var vnttCamManager: VNTTCamManager = VNTTCamManagerImpl()

    private lateinit var _compositeDisposable: CompositeDisposable

    val compositeDisposable: CompositeDisposable
        get() {
            // Khi Xoay ngang màn hình thì compositeDisposable bị disposed đi => Các request sẽ ko Submit được
            // Trong trường hợp này khởi tạo lại
            if (_compositeDisposable.isDisposed) {
                _compositeDisposable = CompositeDisposable()
            }
            return _compositeDisposable
        }

    init {
        _compositeDisposable = CompositeDisposable()
    }

    val schedulerProvider: SchedulerProvider = SchedulerProviderImpl()

    private val _cameraState: MutableLiveData<Constant.CameraState> by lazy { MutableLiveData() }
    val cameraState: LiveData<Constant.CameraState> get() = _cameraState

    private var isCameraOnline = false

    private val _cameraInfo: MutableLiveData<DeviceInfo> by lazy { MutableLiveData() }
    val cameraInfo: LiveData<DeviceInfo> get() = _cameraInfo

    var startDay = 1720803600000

    private var disposableVideoStream: Disposable? = null

    private var disposableAudioStream: Disposable? = null

    private var disposableTimeStamp: Disposable? = null

    private var disposableVideoRecordStream: Disposable? = null

    private var isCancelledPlayback = false

    private var hasDataVideo: Boolean = false

    private var startSeekEvent: Boolean = false

    private val _dataVideoStream: PublishSubject<VideoStreamData> by lazy { PublishSubject.create() }
    val dataVideoStream: PublishSubject<VideoStreamData> get() = _dataVideoStream

    var currentFilePlay: PlaybackFileModel? = null

    private var currentFileName: String = Constant.EMPTY_STRING      // File playback đang được play

    private val _videoRecordQuality: MutableLiveData<Int> by lazy { MutableLiveData() }
    val videoRecordQuality: LiveData<Int> get() = _videoRecordQuality

    private val _playbackState: MutableLiveData<Constant.PlaybackSdCardStateUI> by lazy { MutableLiveData() }
    val playbackState: LiveData<Constant.PlaybackSdCardStateUI> get() = _playbackState

    fun setDataCamera(device: Device) {
        devicesCamera = device
        idCam = device.id ?: 0
        uidCam = device.uid ?: device.name.toString()
        passCam = device.password ?: Constant.EMPTY_STRING

    }

    /**
     * Set time seek value
     */
    fun setTimeSeekValue(value: Long) {
        seekTimeValue = value
    }

    /**
     * 1. Set playback mode for camera manager
     * 2. Get id, uid, password camera
     * 3. Initialize camera manager
     * 4. Set camera live data value
     */

    fun prepareAndInitializeCamera(camera: Device) {
        getIdCamera(camera)
        initializeCameraManager(idCam, camera.name.toString(), passCam)
        setPlaybackMode()
//        setCameraDevice(camera)
    }

    /**
     * Trường hợp app ở trạng thái onResume (vào app lần đầu sẽ không chạy hàm này)
     * 1. Nếu trong Session manager đã tồn tại cam, tiếp tục play file playback
     * 2. Nếu Session đã bị disconnect, thì connect lại camera
     */
    fun reconnectCameraAgain() {
        if (!SessionCameraManager.isSessionExist(uidCam)) {
//            isReconnect = true
            hasDataVideo = false
            startSeekEvent = false
            connectCameraWithNewSession(idCam, uidCam, passCam)
        }
    }

    /**
     * Set playback mode
     */
    private fun setPlaybackMode() {
        vnttCamManager.setPlaybackMode(true)
    }

    /**
     * B1: Khởi tạo camera manager
     * - Nếu trong com.example.cameraplayback.manager.SessionCameraManager đã tồn tại uid, thì lấy camera manager để sử dụng luôn
     * - Nếu chưa tồn tại uid, khởi tạo camera manager mới, và add vào com.example.cameraplayback.manager.SessionCameraManager
     *
     * B2: Observe camera state
     */
    private fun initializeCameraManager(id: Int, uid: String, pass: String) {
        if (SessionCameraManager.isSessionExist(uid)) {
            SessionCameraManager.getCameraManagerByUID("VNTTC-000237-DXKPK")?.let { camManager ->
                vnttCamManager = camManager
            }
        } else {
            connectCameraWithNewSession(id, "VNTTC-000237-DXKPK", pass)
        }

        observeCameraState()
    }

    /**
     * Observe camera state from camera manager
     */
    private fun observeCameraState() {
        compositeDisposable.add(
            vnttCamManager.observeCameraState()
                .subscribeOn(schedulerProvider.io())
                .observeOn(schedulerProvider.ui())
                .subscribe({ state ->
                    Log.e("observeCameraState","Camera name: ${getDataCamera().name}, uid: ${getDataCamera().uid} --> ${state.value}")

                    if (state == Constant.CameraState.CameraSleep || state == Constant.CameraState.CameraOnline) {
                        // Khi connect camera trả về status = 0 -> Camera online -> Thêm cam vào Session Manager
                        SessionCameraManager.addCamera(uidCam, vnttCamManager)
                    }

                    // Đối với state online: Cần đợi đến thời điểm video data stream trả về mới set state online
                    // Các trường hợp còn lại thì set luôn
                    if (state != Constant.CameraState.CameraOnline) {
                        setCameraState(state)
                    }

                    processCameraState(state)
                }, {
                    Log.e("observeCameraState","Observe camera state failed: ${it.message}")
                })
        )


    }

    /**
     * Xử lý các logic tương ứng với từng state camera
     */
    private fun processCameraState(state: Constant.CameraState) {
        when (state) {
            Constant.CameraState.CameraOnline,
            Constant.CameraState.CameraSleep -> {
                if (!isCameraOnline) {
                    isCameraOnline = true
                    observeCameraInfo()
                    observeListVideoPlayback()
                    observeCommandSet()
                    observeDataStream()
                    vnttCamManager.getDeviceInfo()
                }





                            getListVideoPlaybackFromCamera(startDay)

            }

            Constant.CameraState.CameraLossConnection -> {
//                isReconnect = true
//                hasDataVideo = false
            }

            Constant.CameraState.CameraOffline -> {
//                disconnectAndRemoveCameraFromListSession()
            }

            else -> {}
        }
    }

    /**
     * Lấy danh sách video playback sd card của 1 ngày từ camera
     * @param startTime: thời gian đầu ngày (00:00:00) của ngày được chọn
     */
    private fun getListVideoPlaybackFromCamera(startTime: Long) {
        vnttCamManager.getListVideoPlayBackSdCard(startTime)
    }

    /**
     * Observe data stream of playback sdcard
     * Audio stream, video stream, timestamp stream
     */
    private fun observeDataStream() {
        if (disposableVideoStream == null) {
            disposableVideoStream = vnttCamManager.observePlaybackVideoStream()
                .subscribeOn(schedulerProvider.computation())
                .observeOn(schedulerProvider.computation())
                .subscribe { videoData ->
                    if (hasDataVideo) {
                        _dataVideoStream.onNext(videoData)
                    }

                    if (!hasDataVideo && !startSeekEvent) {
                        // Nếu bắt đầu có data của video trả về và không phải là sự kiện seek video
                        // ==> Set playback state là playing
                        hasDataVideo = true
//                        isPaused = false
//                        isReconnect = false

                        getQualityVideo(videoData.widthData, videoData.heightData)
                        setPlaybackStateUI(Constant.PlaybackSdCardStateUI.PLAYING)
                    }
                }
        }

        if (disposableAudioStream == null) {
            disposableAudioStream = vnttCamManager.observePlaybackAudioStream()
                .subscribeOn(schedulerProvider.newThread())
                .observeOn(schedulerProvider.newThread())
                .doOnNext { dataAudio ->
                    // Nếu đang trong chế độ record, thì tiến hành ghi data vào file
//                    writeData(dataAudio, true)
                }
                .subscribe { audioData ->
                    if (!startSeekEvent) {
//                        audioTrack.write(
//                            audioData,
//                            0,
//                            audioData.size
//                        )
                    }
                }
        }

        if (disposableTimeStamp == null) {
//            disposableTimeStamp = vnttCamManager.observeTimestampPlayback()
//                .subscribeOn(schedulerProvider.newThread())
//                .observeOn(schedulerProvider.newThread())
//                .subscribe { offset ->
//                    if (!startSeekEvent) {
//                        currentFilePlay?.let { file ->
//                            val timestamp = convertSecondToMillis(file.timestamp)
//
//                            if (ceil((offset / 1000.0)) >= file.duration) {
//                                // Đã play hết file hiện tại ==> Next sang file tiếp theo
//                                processNextFile()
//                            }
//
//                            _cursorTimebar.postValue(Pair(true, timestamp + offset))
//                        }
//                    }
//                }
        }

        disposableVideoStream?.let {
            compositeDisposable.delete(it)
            compositeDisposable.add(it)
        }

        disposableAudioStream?.let {
            compositeDisposable.delete(it)
            compositeDisposable.add(it)
        }

        disposableTimeStamp?.let {
            compositeDisposable.delete(it)
            compositeDisposable.add(it)
        }
    }

    /**
     * Set state play video playback
     */
    private fun setPlaybackStateUI(state: Constant.PlaybackSdCardStateUI) {
        _playbackState.postValue(state)
    }

    /**
     * Lấy chất lượng của video playback đang phát
     * SD: 640 x 360
     * FHD: 1920 x 1080
     */
    private fun getQualityVideo(widthData: Int, heightData: Int) {
        currentFilePlay?.let { file ->
            if (!StringUtils.equals(file.name, currentFileName)) {
                currentFileName = file.name.toString()

                if (widthData == 1920 && heightData == 1080) {
                    // FHD
                    _videoRecordQuality.postValue(Constant.ConfigCamera.VNPT_CAMERA_RECORD_RESOLUTION_FHD)
                } else if (widthData == 640 && heightData == 360) {
                    // SD
                    _videoRecordQuality.postValue(Constant.ConfigCamera.VNPT_CAMERA_RECORD_RESOLUTION_SD)
                } else {
                    // Nếu không lấy được độ phân giải từ video của cam thì gọi lệnh xuống cam để lấy config
                    observeVideoRecordQuality()
                    getVideoRecordQualityValue()
                }
            }
        }
    }

    /**
     * Observe video record quality
     */
    private fun observeVideoRecordQuality() {
        compositeDisposable.add(
            vnttCamManager.observeVideoQualityRecord()
                .subscribeOn(schedulerProvider.io())
                .observeOn(schedulerProvider.ui())
                .subscribe({ quality ->
                    _videoRecordQuality.value = quality
                }, {
                    Log.e("observeVideoRecordQuality","Observe video record quality failed")
                })
        )
    }

    /**
     * Get video record quality value
     */
    private fun getVideoRecordQualityValue() {
        vnttCamManager.getVideoRecordQuality()
    }

    /**
     * Observe commandset from camera
     */
    private fun observeCommandSet() {
        compositeDisposable.add(
            vnttCamManager.observeCommandSet()
                .subscribeOn(schedulerProvider.io())
                .observeOn(schedulerProvider.ui())
                .subscribe { commandSet ->
                    commandSet?.first?.let { command ->
                        val status = commandSet.second
//                        setStateByCommandSet(command, status)
                    }
                }
        )
    }



    /**
     * Observe Camera Information
     */
    private fun observeCameraInfo() {
        compositeDisposable.add(
            vnttCamManager.observeDeviceInfo()
                .subscribeOn(schedulerProvider.io())
                .observeOn(schedulerProvider.ui())
                .subscribe { deviceInfo ->
                    _cameraInfo.value = deviceInfo
                }
        )
    }

    /**
     * Set camera state
     */
    private fun setCameraState(state: Constant.CameraState) {
        _cameraState.postValue(state)
    }

    /**
     * Connect to camera with new session
     */
    private fun connectCameraWithNewSession(id: Int, uid: String, pass: String) {
        vnttCamManager.apply {
            createCameraManager(uid, pass)
            initCallback()
            receiveVideo(false)
            receiveAudio(false)
            connectToCamera(id)
        }
    }

    /**
     * Lấy thông tin id, uid, password camera
     */
    private fun getIdCamera(camera: Device) {
        camera.id?.let { idCam = it }
        camera.uid?.let { uidCam = "VNTTC-000237-DXKPK" }
        decryptPassword(camera)?.let { passCam = "Phh7a0j3" }
    }

    private fun decryptPassword(camera: Device): String? {
        return decrypt(camera.password)
    }





    fun getDataCamera() : Device {
        return devicesCamera ?: Device(name = "Camera")
    }

    fun prepareAndSetUpCamera() {
//        getDetailDevices(getDataCamera())
    }

    fun fetchDevice(deviceId: Int) {
        viewModelScope.launch {
            try {
                val device = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.getDevice(deviceId)
                }
                _device.postValue(device)
            } catch (e: Exception) {
                _error.postValue(e.message)
            }
        }
    }

    /**
     * Observe list video playback sd card
     * Trừ đi 1s trong duration của mỗi video, bởi vì trong quá trình next file, sẽ có lúc offset cuối cùng trả về sẽ < duration.
     * Vì vậy cần trừ đi 1s để đảm bảo offset cuối cùng luôn >= duration. Lúc đó mới next file được
     */
    private fun observeListVideoPlayback() {
        compositeDisposable.add(
            vnttCamManager.observeListVideoPlayback()
                .subscribeOn(schedulerProvider.io())
                .observeOn(schedulerProvider.ui())
                .map { data ->
                    data.forEach { _, listVideoInDay ->
                        // Phải là isNullOrEmpty() -> không được dùng isNotEmpty()
                        if (!listVideoInDay.isNullOrEmpty()) {
                            listVideoInDay.forEach { file ->
                                val decreaseDuration = file.duration - 1
                                file.duration = decreaseDuration
                            }
                        }
                    }

                    return@map data
                }
                .subscribe({ playbackData ->
                    processPlaybackData(playbackData)
                }, {
                    Log.e("observeListVideoPlayback","Observe list video playback failed: $it")
                })
        )
    }

    /**
     * Xử lý danh sách video playback lấy được từ camera sau khi query:
     *    - Nếu danh sách video của ngày hiện tại rỗng:
     *          + Trường hợp 1: Khi người dùng chọn ngày, mà ngày đó rỗng, thì show luôn thông báo "không có dữ liệu"
     *          + Trường hợp 2: Thời điểm đầu tiên vào xem playback, nếu ngày hiện tại không có video
     *                          --> Tiếp tục query từng ngày trước đó. Nếu ngày trước đó có dữ liệu thì query và play ngày đó, nếu vẫn empty thì tiếp tục query.
     *                              Lặp lại cho đến khi hết 30 ngày
     *          + Trường hợp 3: Tất cả các ngày đều không có video playback -> show thông báo
     *    - Nếu có data của ngày hiện tại:
     *          + Set timestamp vào thanh timeline
     *
     * @param playbackData: danh sách video playback lấy từ cam
     */
    private fun processPlaybackData(
        playbackData: LongSparseArray<ArrayList<PlaybackFileModel>>
    ) {
        val timeKeyIndex = playbackData.indexOfKey(startDay)
        val timeKey = playbackData.keyAt(timeKeyIndex)
        val dataOfDay = playbackData.get(timeKey)
        if (dataOfDay.isNullOrEmpty()) {
//
        } else {
//            listPlayback.put(timeKey, dataOfDay)
//            emptyFileAllDay = false
//            setDataToTimelineAndPlayFile(dataOfDay)
        }
    }
}
