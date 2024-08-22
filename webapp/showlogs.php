<?php
$req_fname = $_GET["fname"];

$fname = "/var/www/logs-" . $req_fname;

$fp = fopen($fname, "r");

if ($fp == false)
{
	echo " ";
}
else
{
	$fdata = fread($fp, filesize($fname));
	fclose($fp);
	echo $fdata;
}
?>
