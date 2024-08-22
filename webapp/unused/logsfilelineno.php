<?php
$req_fname = $_GET["fname"];
$req_lineno = $_GET["lineno"];

$logs_fname = "/var/www/logs-" . $req_fname;

$fp = fopen($logs_fname, "r");

if ($fp == false)
{
	echo "Log file does not exist!";
}
else
{
	$logmsg = "";

	while (($line = fgets($fp)) !== false)
	{
		if (str_starts_with($line, $req_lineno))
		{
			// <line no>:<log msg>
			$logmsg = substr($line, strlen($req_lineno) + 1);
			break;
		}
    }

	fclose($fp);
	echo $logmsg;
}
?>

