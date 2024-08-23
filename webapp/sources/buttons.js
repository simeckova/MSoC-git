import { regButton } from "./prism.js";

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
        text: 'Copy', // required
        onClick: function (env) { // optional
            textToClipboard(FILE);
        }
    });

    //Log
    regButton('l-copy', {
        text: 'Copy', // required
        onClick: function (env) { // optional
            textToClipboard(LOG);
        }
    });
}

export {makeButtons};