<?php
$req_fname = $_GET["fname"];

$fname = "/var/www/" . $req_fname;

$fp = fopen($fname, "r");

if ($fp == false)
{
	echo "File does not exist!";
}
else
{
	$fdata = fread($fp, filesize($fname));
	fclose($fp);
	echo $fdata;
}
?>

