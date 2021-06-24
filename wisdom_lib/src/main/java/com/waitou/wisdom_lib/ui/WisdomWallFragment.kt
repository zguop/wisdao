package com.waitou.wisdom_lib.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.waitou.wisdom_lib.Wisdom
import com.waitou.wisdom_lib.bean.Album
import com.waitou.wisdom_lib.bean.Media
import com.waitou.wisdom_lib.interfaces.LoaderAlbum
import com.waitou.wisdom_lib.interfaces.LoaderMedia
import com.waitou.wisdom_lib.interfaces.IFullImage
import com.waitou.wisdom_lib.config.WisdomConfig
import com.waitou.wisdom_lib.loader.AlbumCollection
import com.waitou.wisdom_lib.loader.MediaCollection
import com.waitou.wisdom_lib.loader.MediaLoader
import com.waitou.wisdom_lib.utils.*
import java.util.*

/**
 * auth aboom
 * date 2019-05-24
 */
abstract class WisdomWallFragment : Fragment(),
    LoaderAlbum,
    LoaderMedia {

    companion object {
        @JvmField
        val TAG: String = WisdomWallFragment::class.java.name + hashCode()
    }

    private val backDispatcher by lazy { requireActivity().onBackPressedDispatcher }
    private val albumCollection by lazy { AlbumCollection(requireActivity(), this) }
    private val mediaCollection by lazy { MediaCollection(requireActivity(), this) }
    private val cameraStrategy by lazy { CameraStrategy() }
    private val cropStrategy by lazy { CropStrategy() }

    private var currentAlbumId: String = Album.ALBUM_ID_ALL
    private var cameraPermissionGranted: (() -> Unit)? = null
    private var iFullImage: IFullImage? = null

    private val storagePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) {
            startLoading()
        } else {
            onStoragePermissionDenied(!shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) {
            cameraPermissionGranted?.invoke()
        } else {
            onCameraPermissionDenied(!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        iFullImage = context as? IFullImage
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //权限检查
        requestStartPermissionLaunch()
        //回调默认勾选的media
        beforeSelectorMedias(WisdomConfig.getInstance().imgMedias)
    }


    private fun checkPermissionOnCamera(cameraPermission: (() -> Unit)?) {
        cameraPermissionGranted = cameraPermission
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (Activity.RESULT_OK != resultCode) {
            return
        }
        // the camera callback
        if (CameraStrategy.CAMERA_REQUEST == requestCode) {
            if (isAndroidQ()) {
                handleCamera(cameraStrategy.fileUri)
            } else {
                SingleMediaScanner(requireContext(), cameraStrategy.filePath) { uri, _ ->
                    handleCamera(uri)
                }
            }
        }
        // the crop callback
        WisdomConfig.getInstance().cropEngine?.let {
            if (cropStrategy.startCropRequest == requestCode) {
                onCropResult(cropStrategy.cropResult(data))
            }
        }
        //预览页面回来
        if (WisPreViewActivity.WIS_PREVIEW_REQUEST_CODE == requestCode) {
            val exit = data!!.getBooleanExtra(WisPreViewActivity.EXTRA_PREVIEW_RESULT_EXIT, false)
            val fullImage = data.getBooleanExtra(WisPreViewActivity.EXTRA_FULL_IMAGE, false)
            val medias = data.getParcelableArrayListExtra<Media>(WisPreViewActivity.EXTRA_PREVIEW_SELECT_MEDIA).orEmpty()
            handlePreview(exit, fullImage, medias)
        }
    }

    private fun handleCamera(uri: Uri) {
        val media = requireContext().applicationContext.contentResolver.query(uri, MediaLoader.PROJECTION, null, null, null)?.use {
            it.moveToFirst()
            Media.valueOf(it)
        }
        if (media != null) {
            onCameraResult(media)
        }
    }

    private fun handlePreview(exit: Boolean, fullImage: Boolean, medias: List<Media>) {
        iFullImage?.setFullImage(fullImage)
        if (exit) {
            resultFinish(medias)
        } else {
            onPreviewResult(medias)
        }
    }


    /**
     * **************************下面是必须对外的方法**************************
     */

    /**
     * 申请存储权限，默认会被调用
     */
    fun requestStartPermissionLaunch() {
        storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    /**
     * 加载目录
     */
    fun loadAlbum() {
        albumCollection.loadAlbum()
    }

    /**
     * 加载相册
     * @param albumId 目录id 默认加载全部
     */
    @JvmOverloads
    fun loadMedia(albumId: String = Album.ALBUM_ID_ALL) {
        currentAlbumId = albumId
        mediaCollection.loadMedia(albumId, WisdomConfig.getInstance().isCamera)
    }

    /**
     * 打开相机拍照 复写onCameraResult方法接收相机回调
     */
    fun startCameraImage() {
        checkPermissionOnCamera {
            cameraStrategy.startCamera(
                this,
                WisdomConfig.getInstance().authorities,
                WisdomConfig.getInstance().directory
            )
        }
    }

    /**
     * 打开相机录像，复写onCameraResult方法接收相机回调
     */
    fun startCameraVideo() {
        checkPermissionOnCamera {
            cameraStrategy.startCameraVideo(
                this,
                WisdomConfig.getInstance().authorities,
                WisdomConfig.getInstance().directory
            )
        }
    }

    /**
     * 跳转到预览页面 复写onPreviewResult 处理预览回调数据
     */
    fun startPreview(
        clazz: Class<out WisPreViewActivity>,
        selectMedia: List<Media>,
        currentPosition: Int = 0,
        bundle: Bundle? = null
    ) {
        //当前点击的position 所有选择的数据 mediaId
        val i = WisPreViewActivity.getIntent(
            requireContext(),
            clazz,
            selectMedia,
            currentPosition,
            currentAlbumId,
            (requireActivity() as IFullImage).isFullImage(),
            WisPreViewActivity.WIS_PREVIEW_MODULE_TYPE_EDIT
        )
        startActivityForResult(i, WisPreViewActivity.WIS_PREVIEW_REQUEST_CODE, bundle)
    }

    /**
     * 配置了cropEngine进行裁剪
     */
    fun startCrop(media: Media): Boolean {
        WisdomConfig.getInstance().cropEngine ?: return false
        if (media.isGif() || media.isVideo()) return false
        cropStrategy.startCrop(this, media)
        return true
    }

    /**
     * 配置compressEngine进行图片压缩
     */
    fun compress(resultMedias: List<Media>, function: () -> Unit) {
        if (iFullImage?.isFullImage() == true || WisdomConfig.getInstance().compressEngine == null) {
            function.invoke()
            return
        }
        WisdomConfig.getInstance().compressEngine!!.compress(requireContext(), resultMedias, function)
    }

    /**
     * 关闭 回调数据 link {WisdomWallActivity onResultFinish}
     */
    fun resultFinish(resultMedias: List<Media>) {
        compress(resultMedias) {
            val i = Intent()
            i.putParcelableArrayListExtra(Wisdom.EXTRA_RESULT_SELECTION, ArrayList(resultMedias))
            requireActivity().setResult(Activity.RESULT_OK, i)
            backDispatcher.onBackPressed()
        }
    }

    /**
     * **************************下面是必须实现和可复写的方法**************************
     */

    abstract fun startLoading()

    /**
     * 默认勾选medias，onActivityCreated调用
     */
    open fun beforeSelectorMedias(imgMedias: List<Media>?) {}

    /**
     * 相机拍照或者录像后回调，包装成一个media
     */
    open fun onCameraResult(media: Media) {}

    /**
     * 裁剪完成后，调用
     */
    open fun onCropResult(media: Media) {}

    /**
     * 预览页面回调，在预览页面做了的事情，将数据源回调更新
     */
    open fun onPreviewResult(medias: List<Media>) {}

    /**
     * 拒绝了存储权限回调
     * @param isDeniedForever 用户永久拒绝了权限
     */
    open fun onStoragePermissionDenied(isDeniedForever: Boolean) {}

    /**
     * 拒绝了相机权限回调
     * @param isDeniedForever 用户永久拒绝了权限
     */
    open fun onCameraPermissionDenied(isDeniedForever: Boolean) {}
}
