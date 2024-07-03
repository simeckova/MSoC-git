<?php
$req_lang = $_GET["lang"];

$kwords_fname = "/var/www/keywords-" . $req_lang . ".txt";

$fp = fopen($kwords_fname, "r");

if ($fp == false)
{
	echo "File does not exist!";
}
else
{
	$kwords_fdata = fread($fp, filesize($kwords_fname));
	fclose($fp);
	echo $kwords_fdata;
}
?>

