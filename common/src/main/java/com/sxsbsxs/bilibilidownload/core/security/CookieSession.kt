package com.sxsbsxs.bilibilidownload.core.security
/** Cookie remains memory-only; never write raw SESSDATA into preferences or logs. */
class CookieSession {
 @Volatile private var value:String?=null
 fun set(cookie:String?){value=cookie?.trim()?.takeIf{it.isNotEmpty()}}
 fun get():String?=value
 fun clear(){value=null}
 val isLoggedIn:Boolean get()=!value.isNullOrBlank()
}