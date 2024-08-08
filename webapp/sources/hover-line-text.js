function hoverLineText(lineNum, lineSpan) {
    let text = ""
    if(lineNum != 0){
        const reqLineText = new XMLHttpRequest();
        reqLineText.onload = function() {
            text = this.responseText.trim();
            if(text != ''){
                lineSpan.classList.add("tooltip");
                lineSpan.innerHTML = '<span class="tooltiptext"><code>' + text + '</code></span>';
            }
        }
        reqLineText.open("GET", address + "logsfilelineno.php?fname=" + fname + "&lineno=" + lineNum, true);
        reqLineText.send();
    }
}

export {hoverLineText};
