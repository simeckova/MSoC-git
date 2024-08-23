console.log("main.js is running");

import {loadFile} from "./loadFile.js";
import {clean} from "./clean.js";

window.DEBUGGING = true;
window.ADDRESS = "http://localhost:8080/";
window.LOADEDFILE = false;
window.HASLOGS = false;
window.FNAME = "";
window.FILE = ""
window.LOGS = new Map();

window.loadFile = loadFile;
clean();

document.getElementById('input').addEventListener('keydown', function(event) {
    if (event.key === 'Enter') {
        loadFile();
    }
});
