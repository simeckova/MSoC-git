function show(lineNum, text){
    text = text.trim();
    if(debugging) console.log("Showing line text: -" + text + "-");

    const lineHeader = document.getElementById("line-header");
    const lineText = document.getElementById("line-text");
    if(lineNum == 0){
        if(debugging) console.log("Picked 0 line");
        lineHeader.innerHTML = "No line picked.";
        lineText.innerHTML = "";
        lineText.className = '';
        lineText.style.display = 'none';
    } else if(text == ""){
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
}

function showLineText(lineNum) {
    if(debugging) console.log("Clicked line " + lineNum + " in file " + fname);

    if(lineNum == 0){
        show(0, '')
    } else {
        let text = "";

        const reqLineText = new XMLHttpRequest();
        reqLineText.onload = function() {
            text = this.responseText;
            if(debugging) console.log("Loaded line text");
            show(lineNum, text);
        }
        reqLineText.open("GET", address + "logsfilelineno.php?fname=" + fname + "&lineno=" + lineNum, true);
        reqLineText.send();
    }
}

export {showLineText};
