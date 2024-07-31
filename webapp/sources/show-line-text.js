function show(lineNum, text){
    text = text.trim();
    if(debugging) console.log("Showing line text: -" + text + "-");

    const lineHeader = document.getElementById("line-header");
    const lineText = document.getElementById("line-text");
    if(text == ""){
        lineHeader.innerHTML = "No text for line " + lineNum + " of file " + fname + ".";
        lineText.innerHTML = "";
        lineText.className = '';
        lineText.style.display = 'none';
    } else {
        lineHeader.innerHTML = "Text for line " + lineNum + " of file " + fname + ":";
        lineText.innerHTML = "<code>" + text + "</code>";
        lineText.className = 'line-numbers language-none';
        lineText.style.display = 'block';
    }
    Prism.highlightAll();
}

export function showLineText(lineNum) {
    if(debugging) console.log("Clicked line " + lineNum + " in file " + fname);

    let text = "";

    const reqLineText = new XMLHttpRequest();
    reqLineText.onload = function() {
        text = this.responseText;
        if(debugging) console.log("Loaded line text");
        show(lineNum, text);
    }
    reqLineText.open("GET", address + "linetext.php?fname=" + fname + "&linenum=" + lineNum, true);
    reqLineText.send();
}