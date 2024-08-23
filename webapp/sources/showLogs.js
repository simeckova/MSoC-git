import { highlight } from "./prism.js";
import { escapeFile } from "./escapeFile.js";

function showLogs(lineNum) {
    const lineHeader = document.getElementById("line-header");
    const lineText = document.getElementById("line-text");

    if (!LOADEDFILE) {
        lineHeader.style.display = 'none';
        lineText.style.display = 'none';
    } else if (!HASLOGS) {
        lineHeader.style.display = 'block';
        lineText.style.display = 'none';
        lineHeader.innerHTML = "File has no logs.";
    } else if (lineNum == 0){
        lineHeader.style.display = 'block';
        lineText.style.display = 'none';
        lineHeader.innerHTML = "No line picked.";
    } else if (LOGS.has(lineNum)){
        lineHeader.style.display = 'block';
        lineText.style.display = 'block';
        lineHeader.innerHTML = "Text for line " + lineNum + " of file " + FNAME + ":";
        lineText.innerHTML = "<code>" + escapeFile(LOGS.get(lineNum)) + "</code>";
        lineText.className = 'line-numbers language-plain';
    } else {
        lineHeader.style.display = 'block';
        lineText.style.display = 'none';
        lineHeader.innerHTML = "No text for line " + lineNum + " of file " + FNAME + ".";
    }
    highlight(lineText);
}

export {showLogs};
