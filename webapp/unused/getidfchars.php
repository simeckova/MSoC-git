<?php
$req_lang = $_GET["lang"];

if ($req_lang == "java")
{
	echo "[a-zA-Z_][a-zA-Z0-9_$]*";
}
else
{
	echo "Unsupported language! (you entered ".$req_lang;
}
?>