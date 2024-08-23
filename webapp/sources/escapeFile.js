function escapeFile(fileText){
    fileText = fileText.replace(/&/g, "&amp");
    fileText = fileText.replace(/</g, "&lt");
    fileText = fileText.replace(/>/g, "&gt");
    return fileText;
}

export {escapeFile};