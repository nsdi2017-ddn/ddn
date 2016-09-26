<?php
// Match the request and write its info and group id into file

$path = '/var/www/info';
set_include_path(get_include_path() . PATH_SEPARATOR . $path);
$features = explode("\t", $_POST["payload"]);
//print_r($features);

include 'match.php';

// if no match
if (empty($group_id)) {
    //$group_id = "null";
    $group_id = $features[4];
    // get default decision
    $out = file_get_contents('/var/www/info/d_'.$group_id);
} else {
    $decisions = file('/var/www/info/d_'.$group_id);
    $cnt = count($decisions);
    $value = rand(0,100);
    for ($i = 0; $i < $cnt; $i++) {
        $decision = explode(";", $decisions[$i]);
        $value -= $decision[1] * 100;
        if ($value <= 0) {
            $out = $decision[0];
            break;
        }
    }
    // in case value still bigger than 0. that is, $out is still unassigned
    if ($value > 0)
        $out = $decision[0];
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
