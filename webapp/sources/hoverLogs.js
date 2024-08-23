import { escapeFile } from "./escapeFile.js";

function hoverLogs(lineNum, lineSpan) {
    let text = ""
    if(lineNum != 0 && LOGS.has(lineNum)){
        text = LOGS.get(lineNum).trim();
        if(text != ''){
            lineSpan.classList.add("tooltip");
            lineSpan.innerHTML = '<span class="tooltiptext"><code>' + escapeFile(text) + '</code></span>';
        }
    }
}

export {hoverLogs};