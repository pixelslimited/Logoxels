<?php
// Copies the newest Logoxels build from GitHub onto this site. Run from cron, e.g. every 10 minutes.
// Private repo: put a read-only GitHub token (Contents: read) in /home/pixelsli/.logoxels_token.
$repo = 'pixelslimited/Logoxels';
$web = '/home/pixelsli/logoxels.pixelslimited.ng';
$log = '/home/pixelsli/logoxels-deploy.log';
$tokenFile = '/home/pixelsli/.logoxels_token';
$token = is_file($tokenFile) ? trim(file_get_contents($tokenFile)) : '';

function say($m) { global $log; file_put_contents($log, date('c') . ' ' . $m . "\n", FILE_APPEND); echo $m . "\n"; }

function fetch($url, $token, $accept, $file = null) {
    $h = ['Accept: ' . $accept, 'User-Agent: logoxels-deploy'];
    if ($token !== '') $h[] = 'Authorization: Bearer ' . $token;
    $ch = curl_init($url);
    curl_setopt_array($ch, [CURLOPT_FOLLOWLOCATION => true, CURLOPT_HTTPHEADER => $h, CURLOPT_TIMEOUT => 600, CURLOPT_FAILONERROR => false]);
    $fp = null;
    if ($file) { $fp = fopen($file, 'wb'); curl_setopt($ch, CURLOPT_FILE, $fp); } else { curl_setopt($ch, CURLOPT_RETURNTRANSFER, true); }
    $body = curl_exec($ch);
    $code = curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
    if ($fp) fclose($fp);
    curl_close($ch);
    return [$code, $body];
}

[$code, $body] = fetch("https://api.github.com/repos/$repo/releases/latest", $token, 'application/vnd.github+json');
if ($code !== 200) { say("release lookup failed: HTTP $code" . ($token === '' ? ' (no token file)' : '')); exit(1); }
$rel = json_decode($body, true);
if (!preg_match('/^build-(\d+)$/', $rel['tag_name'] ?? '', $m)) { say('unrecognised tag'); exit(1); }
$build = (int)$m[1];
$asset = null;
foreach ($rel['assets'] ?? [] as $a) if (($a['name'] ?? '') === 'Logoxels.apk') $asset = $a;
if (!$asset) { say("build $build has no APK"); exit(1); }

$cur = is_file("$web/update.json") ? (int)(json_decode(file_get_contents("$web/update.json"), true)['build'] ?? 0) : 0;
if ($cur >= $build) { say("up to date at build $cur"); exit(0); }

$tmp = "$web/Logoxels.apk.part";
[$code] = fetch($asset['url'], $token, 'application/octet-stream', $tmp);
if ($code !== 200 || !is_file($tmp) || filesize($tmp) < 1000000 || file_get_contents($tmp, false, null, 0, 2) !== 'PK') {
    @unlink($tmp); say("download failed: HTTP $code"); exit(1);
}
$sha = hash_file('sha256', $tmp);
$size = filesize($tmp);
rename($tmp, "$web/Logoxels.apk");
$json = json_encode(['build' => $build, 'url' => 'https://logoxels.pixelslimited.ng/Logoxels.apk', 'size' => $size, 'sha256' => $sha], JSON_UNESCAPED_SLASHES);
file_put_contents("$web/update.json.tmp", $json);
rename("$web/update.json.tmp", "$web/update.json");
say("published build $build ($size bytes)");
