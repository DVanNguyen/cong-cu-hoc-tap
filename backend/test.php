<?php
require 'vendor/autoload.php';
$app = require_once 'bootstrap/app.php';
$kernel = $app->make(Illuminate\Contracts\Console\Kernel::class);
$kernel->bootstrap();

try {
    $req = request();
    $req->setUserResolver(fn() => App\Models\User::first());
    $res = app('App\Http\Controllers\Api\BlockController')->index($req, '055aba1c-e74c-4e51-8c2e-629482121391');
    echo $res->getContent();
} catch(\Throwable $e) {
    echo get_class($e) . ': ' . $e->getMessage();
}
