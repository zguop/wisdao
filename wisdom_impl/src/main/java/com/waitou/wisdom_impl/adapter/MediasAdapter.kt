package com.waitou.wisdom_impl.adapter

import android.graphics.Color
import android.graphics.PorterDuff
import android.support.v7.widget.RecyclerView
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.waitou.wisdom_impl.R
import com.waitou.wisdom_lib.bean.Media
import com.waitou.wisdom_lib.config.WisdomConfig
import com.waitou.wisdom_lib.utils.getScreenImageResize
import com.waitou.wisdom_lib.utils.isSingleImage
import kotlinx.android.synthetic.main.wis_item_camera.view.*
import kotlinx.android.synthetic.main.wis_item_media.view.*

/**
 * auth aboom
 * date 2019-05-28
 */
class MediasAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_CAPTURE: Int = 0x01
        private const val VIEW_TYPE_MEDIA: Int = 0x02
    }

    private val medias = mutableListOf<Media>()
    val selectMedias = mutableListOf<Media>()

    var checkedListener: OnCheckedChangedListener? = null
    var cameraClick: View.OnClickListener? = null
    var mediaClick: ((Media, Int, View) -> Unit)? = null

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(p0: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        VIEW_TYPE_CAPTURE -> {
            val cameraViewHolder =
                    CameraViewHolder(LayoutInflater.from(p0.context).inflate(R.layout.wis_item_camera, p0, false))
            cameraViewHolder.itemView.setOnClickListener { cameraClick?.onClick(it) }
            cameraViewHolder
        }
        else -> {
            val mediaViewHolder =
                    MediaViewHolder(LayoutInflater.from(p0.context).inflate(R.layout.wis_item_media, p0, false))
            mediaViewHolder.itemView.setOnClickListener {
                //预览界面退回，快速点击存在角标问题
                val adapterPosition =
                        if (mediaViewHolder.adapterPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                        else mediaViewHolder.adapterPosition
                mediaClick?.invoke(medias[adapterPosition], adapterPosition, it)
            }
            mediaViewHolder.itemView.checkView.setOnCheckedChangeListener { _, _ ->
                mediaCheckedChange(medias[mediaViewHolder.adapterPosition])
            }
            mediaViewHolder
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (medias[position].isCapture()) VIEW_TYPE_CAPTURE else VIEW_TYPE_MEDIA
    }

    override fun getItemCount(): Int {
        return medias.size
    }

    override fun getItemId(position: Int): Long {
        return medias[position].hashCode().toLong()
    }

    override fun onBindViewHolder(holde: RecyclerView.ViewHolder, position: Int) {
        val media = medias[position]
        if (holde is CameraViewHolder) {
            holde.itemView.cameraText.text = media.path
        } else {
            WisdomConfig.getInstance().iImageEngine?.displayThumbnail(
                    holde.itemView.media, media.uri, getScreenImageResize(), getScreenImageResize(), media.isGif()
            )
            holde.itemView.checkView.setCheckedNum(selectMediaIndexOf(media))
            holde.itemView.media.setColorFilter(
                    if (holde.itemView.checkView.isChecked) Color.argb(80, 0, 0, 0) else Color.TRANSPARENT,
                    PorterDuff.Mode.SRC_ATOP
            )
            holde.itemView.checkView.visibility = if (isSingleImage()) View.GONE else View.VISIBLE
            holde.itemView.size.text = Formatter.formatFileSize(holde.itemView.context, media.size)
            holde.itemView.gif.visibility = if (media.isGif()) View.VISIBLE else View.GONE
            holde.itemView.duration.visibility = if (media.isVideo()) {
                holde.itemView.duration.text = DateUtils.formatElapsedTime(media.duration / 1000)
                View.VISIBLE
            } else View.GONE
        }
    }

    private fun mediaCheckedChange(media: Media) {
        val checkedNumIndex = selectMediaIndexOf(media)
        if (checkedNumIndex > 0) {
            selectMedias.remove(media)
        } else {
            if (selectMedias.size >= WisdomConfig.getInstance().maxSelectLimit) {
                return
            }
            selectMedias.add(media)
        }
        notifyDataSetChanged()
        checkedListener?.onChange()
    }

    interface OnCheckedChangedListener {
        fun onChange()
    }

    private fun selectMediaIndexOf(media: Media): Int {
        val indexOf = selectMedias.indexOf(media)
        return if (indexOf >= 0) indexOf + 1 else indexOf
    }

    fun replaceMedias(medias: List<Media>) {
        this.medias.clear()
        this.medias.addAll(medias)
        notifyDataSetChanged()
    }

    fun replaceSelectMedias(medias: List<Media>) {
        this.selectMedias.clear()
        this.selectMedias.addAll(medias)
        notifyDataSetChanged()
    }

    private class CameraViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView)

    private class MediaViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView)
}
