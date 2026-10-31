package com.sxsbsxs.bilibilidownload.core.audio
/** Typed representation for the DASH muxing milestone. Parsing/muxing is intentionally not claimed yet. */
data class DashTrack(val id:Int,val codec:String,val bandwidth:Long,val mimeType:String,val baseUrl:String)
data class DashPackage(val videoTracks:List<DashTrack>,val audioTracks:List<DashTrack>) {
 val isMuxable:Boolean get()=videoTracks.isNotEmpty()&&audioTracks.isNotEmpty()
}