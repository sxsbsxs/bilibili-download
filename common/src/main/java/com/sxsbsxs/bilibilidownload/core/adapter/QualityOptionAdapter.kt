package com.sxsbsxs.bilibilidownload.core.adapter
import com.sxsbsxs.bilibilidownload.core.model.StreamOption
object QualityOptionAdapter {
 fun sorted(options:List<StreamOption>):List<StreamOption> = options.distinctBy{it.quality}.sortedByDescending{it.quality}
}