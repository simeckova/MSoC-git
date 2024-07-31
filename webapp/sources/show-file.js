import { showLineText } from './show-line-text.js';


let file = "";
let loadedFile = false;

function lang(fname){
    const parts = fname.split(".");
    let returning = parts[parts.length-1];
    if(returning == "html") returning = "markup";
    if(debugging) console.log("File lang is " + returning);
    return parts[parts.length-1];
}
function ready(){
    return loadedFile;
}
function show(){
    if(!ready()) return;
    if(debugging) console.log("Showing");
    const fdataPre = document.getElementById("fdata-pre");

    if(lang(fname)=="markup") file = "<!--" + file + "-->";

    fdataPre.innerHTML = "<code  id='fdata-code'>" + file + "</code>";
    fdataPre.className = 'line-numbers linkable-line-numbers language-' + lang(fname);
    Prism.highlightAll();
}

function retrieveFileData() {
    showLineText(0);

    fname = document.getElementById('fname').value;
    if(debugging) console.log("Recieved file name " + fname);

    file = "";
    loadedFile = false;

    const reqFile = new XMLHttpRequest();
    reqFile.onload = function() {
        file = this.responseText;
        loadedFile = true;
        if(debugging) console.log("Loaded file");
        show();
    }
    reqFile.open("GET", address + "showfile.php?fname=" + fname, true);
    reqFile.send();
}