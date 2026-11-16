package com.jestk.browser.engine.media

/**
 * One-shot scripts for background playback. They run only on demand, define no `window.*` names
 * and leave nothing a page can enumerate behind.
 */
internal object MediaPageScripts {
    private const val SCAN =
        "var m=document.querySelectorAll('video,audio'),p=false,h=false;" +
            "for(var i=0;i<m.length;i++){var e=m[i];if(e.ended)continue;" +
            "if(!e.paused){p=true}else if(e.currentTime>0){h=true}}"

    /** Reports `playing`, `paused` or `none` without touching the page. */
    const val PROBE = "(function(){$SCAN return p?'playing':h?'paused':'none'})()"

    const val PAUSE =
        "(function(){var m=document.querySelectorAll('video,audio');" +
            "for(var i=0;i<m.length;i++){if(!m[i].paused)m[i].pause()}})()"

    const val PLAY =
        "(function(){var m=document.querySelectorAll('video,audio');" +
            "for(var i=0;i<m.length;i++){var e=m[i];if(e.paused&&!e.ended&&e.currentTime>0){e.play();return}}})()"
}
