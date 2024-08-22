import { clean } from './clean.js';
import { showFile } from './showFile.js';
import { showLogs } from './showLogs.js';

function loadFile() {
    clean();

    FNAME = document.getElementById('fname').value.trim();

    if(FNAME==''){
        return;
    }
    LOADEDFILE = true;

    const reqFile = new XMLHttpRequest();
    reqFile.onload = function() {
        FILE = this.responseText;
        showFile();
    }
    reqFile.open("GET", ADDRESS + "showfile.php?fname=" + FNAME, true);
    reqFile.send();

    const reqLogs = new XMLHttpRequest();
    reqLogs.onload = function() {
        let logText = this.responseText.trim();
        if(logText == ''){
            HASLOGS = false;
        } else {
            HASLOGS = true;
            for(let line of logText.split("\n")){
                line = line.trim();
                let key = line.split(":")[0];
                let value = line.slice(key.length+1);
                LOGS.set(parseInt(key), value);
            }
        }
        showLogs(0);
    }
    reqLogs.open("GET", ADDRESS + "showlogs.php?fname=" + FNAME, true);
    reqLogs.send();
}

export {loadFile};