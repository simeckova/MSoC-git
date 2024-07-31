<?php
$req_fname = $_GET["fname"];

$idfs_fname = "/var/www/identifiers-" . $req_fname;

$fp = fopen($idfs_fname, "r");

if ($fp == false)
{
	echo "File does not exist!";
}
else
{
	$idfs_fdata = fread($fp, filesize($idfs_fname));
	fclose($fp);
	echo $idfs_fdata;
}
?>

