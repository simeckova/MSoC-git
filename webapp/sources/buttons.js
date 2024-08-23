import { regButton } from "./prism.js";
import { showFile } from "./showFile.js";
import { showLogs } from "./showLogs.js";
import { removeHash } from "./clean.js";

function textToClipboard (text) {
    var dummy = document.createElement("textarea");
    document.body.appendChild(dummy);
    dummy.value = text;
    dummy.select();
    document.execCommand("copy");
    document.body.removeChild(dummy);
}

function makeButtons(){
    //File:
    regButton('f-copy', {
        text: 'Copy',
        onClick: function (env) {
            textToClipboard(FILE);
        }
    });
    regButton('f-hover', {
        text: 'Toggle hover',
        onClick: function (env) {
            HOVERON = !HOVERON;
            showFile();
        }
    });
    regButton('f-clear', {
        text: 'Unhighlight line',
        onClick: function (env) {
            removeHash();
            showLogs(0);
            showFile();
        }
    });

    //Log
    regButton('l-copy', {
        text: 'Copy',
        onClick: function (env) {
            textToClipboard(LOG);
        }
    });
}

export {makeButtons};