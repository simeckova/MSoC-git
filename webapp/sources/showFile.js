import { highlight } from "./prism.js";
import { escapeFile } from "./escapeFile.js";

function lang(fname){
    const parts = fname.split(".");
    let returning = parts[parts.length-1];
    return parts[parts.length-1];
}
function showFile(){
    const fdataPre = document.getElementById("fdata-pre");

    let file = escapeFile(FILE);

    fdataPre.innerHTML = "<code  id='fdata-code'>" + file + "</code>";
    fdataPre.className = 'line-numbers linkable-line-numbers language-' + lang(FNAME);
    highlight(fdataPre);
}

export {showFile};