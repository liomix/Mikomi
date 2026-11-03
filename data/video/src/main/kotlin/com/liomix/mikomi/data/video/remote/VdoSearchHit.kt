package com.liomix.mikomi.data.video.remote

/**
 * 搜索命中的一部片。
 *
 * @property name 站点给的片名，原样保留（后面要不要模糊匹配是上层的事）。
 * @property url 详情页地址，已拼成绝对地址。
 */
data class VdoSearchHit(val name: String, val url: String)
