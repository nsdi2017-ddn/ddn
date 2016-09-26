<?php
// Match the request and write its info and group id into file

$path = '/var/www/info';
set_include_path(get_include_path() . PATH_SEPARATOR . $path);
$features = explode("\t", $_POST["payload"]);
//print_r($features);

include 'match.php';

// if no match
if (empty($group_id)) {
    $group_id = "null";
    // get default decision
    $out = file_get_contents('/var/www/info/d_'.$group_id);
} else {
    $decisions = file('/var/www/info/d_'.$group_id);
    if (count($decisions) == 2) {
        $out = $decisions[1];
    } else if (count($decisions) > 2) {
        $epsilon = floatval($decisions[0]);
        // get random decision
        if (rand(0, 100) < $epsilon * 100) {
            $out = $decisions[rand(0,count($decisions)-3)+2];
        }
        // get best decision
        else {
            $out = $decisions[1];
        }
    }
}

// response
if (empty($out))
    echo "Oops";
else
    echo $out;

// Encode the info with json and write it into file
$info = array(
    "update" => $_POST["payload"],
    "group_id" => $group_id
);

$in = json_encode($info, JSON_UNESCAPED_SLASHES).PHP_EOL;
//echo $in;
file_put_contents('/var/www/info/info_queue',$in,FILE_APPEND|LOCK_EX);

?>
