import { expHighlight } from "./prism.js";

function escapeFile(fileText){
    fileText = fileText.replace(/&/g, "&amp");
    fileText = fileText.replace(/</g, "&lt");
    fileText = fileText.replace(/>/g, "&gt");
    return fileText;
}
function lang(fname){
    const parts = fname.split(".");
    let returning = parts[parts.length-1];
    if(DEBUGGING) console.log("File lang is " + returning);
    return parts[parts.length-1];
}
function showFile(){
    const fdataPre = document.getElementById("fdata-pre");

    let file = escapeFile(FILE);

    fdataPre.innerHTML = "<code  id='fdata-code'>" + file + "</code>";
    fdataPre.className = 'line-numbers linkable-line-numbers language-' + lang(FNAME);
    expHighlight();
}

export {showFile};