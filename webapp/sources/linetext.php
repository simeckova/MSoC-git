<?php
$req_fname = $_GET["fname"];
$linenum = $_GET["linenum"];

$fname = "/var/www/" . $req_fname;

$fp = fopen($fname, "r");

if ($fp == false)
{
	echo "File " . $req_fname . " does not exist!";
}
else
{
	if ($linenum == 2){
		echo "This is text for line 2";
	} else if ($linenum == 3){
		echo "And this is text for line 3";
	} else {
		echo "";
	}
}
?>

