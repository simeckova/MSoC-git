import { showLogs } from './showLogs.js';
import { expHighlight } from './prism.js';

function removeHash () { 
    var scrollV, scrollH, loc = window.location;
    if ("pushState" in history)
        history.pushState("", document.title, loc.pathname + loc.search);
    else {
        // Prevent scrolling by storing the page's current scroll offset
        scrollV = document.body.scrollTop;
        scrollH = document.body.scrollLeft;

        loc.hash = "";

        // Restore the scroll offset, should be flicker free
        document.body.scrollTop = scrollV;
        document.body.scrollLeft = scrollH;
    }
}

function clean(){
    LOADEDFILE = false;
    HASLOGS = false;
    FNAME = "";
    FILE = ""
    LOGS.clear();
    removeHash();
    showLogs(0);

    const fdataPre = document.getElementById("fdata-pre");
    fdataPre.innerHTML = '<code id="fdata-code">Enter file name, file example is JPFUtils.java or keywords-java.txt or example.js</code>';
    fdataPre.className = 'language-none';
    expHighlight();
}

export {clean};